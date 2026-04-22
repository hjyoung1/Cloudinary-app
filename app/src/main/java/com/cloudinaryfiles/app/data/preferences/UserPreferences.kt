package com.cloudinaryfiles.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cloudinaryfiles.app.data.model.CloudinaryCredentials
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cloudvault_prefs")

/** Flat credential bag — each provider uses whichever fields it needs. */
data class NamedAccount(
    val id: String,
    val name: String,
    val providerKey: String = "cloudinary",
    // ── Cloudinary ──────────────────────────────────────────────────────────
    val cloudName: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    // ── S3-compatible ───────────────────────────────────────────────────────
    val s3Endpoint: String = "",      // e.g. s3.amazonaws.com  or  minio.myhost.com:9000
    val s3Region: String = "us-east-1",
    val s3Bucket: String = "",
    val s3AccessKey: String = "",
    val s3SecretKey: String = "",
    val s3ForcePathStyle: Boolean = false,  // needed for MinIO / custom endpoints
    // ── OAuth2 (Google Drive, Dropbox, OneDrive, Box) ───────────────────────
    val oauthClientId: String = "",
    val oauthClientSecret: String = "",  // required by Dropbox / Box
    val oauthAccessToken: String = "",
    val oauthRefreshToken: String = "",
    val oauthTokenExpiry: Long = 0L,     // epoch seconds
    // ── WebDAV / basic auth ─────────────────────────────────────────────────
    val webDavUrl: String = "",          // https://cloud.myhost.com/remote.php/dav/files/user
    val webDavUser: String = "",
    val webDavPass: String = "",
    // Folder paths/prefixes to exclude from listing (empty = include all)
    val excludedFolders: List<String>? = null   // nullable so Gson missing field → null, not crash
) {
    /** Always safe to use — returns empty list when field missing from old stored data */
    val safeExcludedFolders: List<String> get() = excludedFolders ?: emptyList()

    val isCloudinary get() = providerKey == "cloudinary"

    fun toCredentials(): CloudinaryCredentials? =
        if (isCloudinary) CloudinaryCredentials(cloudName, apiKey, apiSecret) else null

    /** Stable display label */
    val displayLabel get() = name.ifBlank { providerKey }
}

class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_CLOUD_NAME = stringPreferencesKey("cloud_name")
        private val KEY_API_KEY    = stringPreferencesKey("api_key")
        private val KEY_API_SECRET = stringPreferencesKey("api_secret")
        private val KEY_ACCOUNTS   = stringPreferencesKey("accounts_json")
        private val KEY_ACTIVE_ID  = stringPreferencesKey("active_account_id")
        private val gson = Gson()
        private val listType = object : TypeToken<List<NamedAccount>>() {}.type
    }

    private fun parse(json: String): List<NamedAccount> =
        try { gson.fromJson(json, listType) ?: emptyList() } catch (_: Exception) { emptyList() }

    private fun legacyAccount(prefs: Preferences): NamedAccount? {
        val cn = prefs[KEY_CLOUD_NAME]; val ak = prefs[KEY_API_KEY]; val s = prefs[KEY_API_SECRET]
        return if (!cn.isNullOrBlank() && !ak.isNullOrBlank() && !s.isNullOrBlank())
            NamedAccount("legacy", cn, "cloudinary", cn, ak, s)
        else null
    }

    val accounts: Flow<List<NamedAccount>> = context.dataStore.data.map { prefs ->
        val j = prefs[KEY_ACCOUNTS]
        if (!j.isNullOrBlank()) parse(j) else legacyAccount(prefs)?.let { listOf(it) } ?: emptyList()
    }

    val activeAccountId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_ID] ?: if (prefs[KEY_CLOUD_NAME] != null) "legacy" else null
    }

    val activeAccount: Flow<NamedAccount?> = context.dataStore.data.map { prefs ->
        val j = prefs[KEY_ACCOUNTS]; val activeId = prefs[KEY_ACTIVE_ID]
        if (!j.isNullOrBlank()) {
            val list = parse(j)
            if (activeId != null) list.firstOrNull { it.id == activeId } else list.firstOrNull()
        } else legacyAccount(prefs)
    }

    val credentials: Flow<CloudinaryCredentials?> = activeAccount.map { it?.toCredentials() }

    suspend fun saveAccount(account: NamedAccount) {
        context.dataStore.edit { prefs ->
            val list: MutableList<NamedAccount> = if (!prefs[KEY_ACCOUNTS].isNullOrBlank())
                parse(prefs[KEY_ACCOUNTS]!!).toMutableList()
            else legacyAccount(prefs)?.let { mutableListOf(it) } ?: mutableListOf()
            val idx = list.indexOfFirst { it.id == account.id }
            if (idx >= 0) list[idx] = account else list.add(account)
            prefs[KEY_ACCOUNTS] = gson.toJson(list)
            if (prefs[KEY_ACTIVE_ID] == null) prefs[KEY_ACTIVE_ID] = account.id
        }
    }

    /** Patch only OAuth tokens in an existing account */
    suspend fun updateOAuthTokens(id: String, accessToken: String, refreshToken: String, expiryEpochSec: Long) {
        context.dataStore.edit { prefs ->
            val j = prefs[KEY_ACCOUNTS] ?: return@edit
            val list = parse(j).toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(
                    oauthAccessToken = accessToken,
                    oauthRefreshToken = refreshToken,
                    oauthTokenExpiry = expiryEpochSec
                )
                prefs[KEY_ACCOUNTS] = gson.toJson(list)
            }
        }
    }

    suspend fun setActiveAccount(id: String) { context.dataStore.edit { it[KEY_ACTIVE_ID] = id } }

    suspend fun deleteAccount(id: String) {
        context.dataStore.edit { prefs ->
            val j = prefs[KEY_ACCOUNTS] ?: return@edit
            val list = parse(j).toMutableList().also { it.removeAll { a -> a.id == id } }
            prefs[KEY_ACCOUNTS] = gson.toJson(list)
            if (prefs[KEY_ACTIVE_ID] == id) prefs[KEY_ACTIVE_ID] = list.firstOrNull()?.id ?: ""
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CLOUD_NAME); prefs.remove(KEY_API_KEY); prefs.remove(KEY_API_SECRET)
            prefs.remove(KEY_ACCOUNTS);   prefs.remove(KEY_ACTIVE_ID)
        }
    }
}
