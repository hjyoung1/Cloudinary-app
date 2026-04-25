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
    val cloudName: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val s3Endpoint: String = "",
    val s3Region: String = "us-east-1",
    val s3Bucket: String = "",
    val s3AccessKey: String = "",
    val s3SecretKey: String = "",
    val s3ForcePathStyle: Boolean = false,
    val oauthClientId: String = "",
    val oauthClientSecret: String = "",
    val oauthAccessToken: String = "",
    val oauthRefreshToken: String = "",
    val oauthTokenExpiry: Long = 0L,
    val webDavUrl: String = "",
    val webDavUser: String = "",
    val webDavPass: String = "",
    val excludedFolders: List<String>? = null
) {
    val safeExcludedFolders: List<String> get() = excludedFolders ?: emptyList()
    val isCloudinary get() = providerKey == "cloudinary"
    fun toCredentials(): CloudinaryCredentials? =
        if (isCloudinary) CloudinaryCredentials(cloudName, apiKey, apiSecret) else null
    val displayLabel get() = name.ifBlank { providerKey }
}

/**
 * App-level OAuth credentials — entered once in App Settings,
 * shared across all accounts of that provider type.
 */
data class AppOAuthSettings(
    val googleClientId: String = "",
    val googleClientSecret: String = "",
    val dropboxAppKey: String = "",
    val dropboxAppSecret: String = "",
    val onedriveClientId: String = "",
    val boxClientId: String = "",
    val boxClientSecret: String = ""
)

class UserPreferences(private val context: Context) {

    companion object {
        // Legacy single-account keys
        private val KEY_CLOUD_NAME = stringPreferencesKey("cloud_name")
        private val KEY_API_KEY    = stringPreferencesKey("api_key")
        private val KEY_API_SECRET = stringPreferencesKey("api_secret")
        // Multi-account store
        private val KEY_ACCOUNTS   = stringPreferencesKey("accounts_json")
        private val KEY_ACTIVE_ID  = stringPreferencesKey("active_account_id")
        // App-level OAuth settings
        private val KEY_APP_GOOGLE_CLIENT_ID     = stringPreferencesKey("app_google_client_id")
        private val KEY_APP_GOOGLE_CLIENT_SECRET = stringPreferencesKey("app_google_client_secret")
        private val KEY_APP_DROPBOX_KEY          = stringPreferencesKey("app_dropbox_key")
        private val KEY_APP_DROPBOX_SECRET       = stringPreferencesKey("app_dropbox_secret")
        private val KEY_APP_ONEDRIVE_CLIENT_ID   = stringPreferencesKey("app_onedrive_client_id")
        private val KEY_APP_BOX_CLIENT_ID        = stringPreferencesKey("app_box_client_id")
        private val KEY_APP_BOX_CLIENT_SECRET    = stringPreferencesKey("app_box_client_secret")
        private val KEY_THEME                    = stringPreferencesKey("app_theme_id")

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

    // ── App OAuth Settings ────────────────────────────────────────────────────

    val selectedThemeId: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "" }

    suspend fun saveTheme(themeId: String) { context.dataStore.edit { it[KEY_THEME] = themeId } }

    val appOAuthSettings: Flow<AppOAuthSettings> = context.dataStore.data.map { prefs ->
        AppOAuthSettings(
            googleClientId     = prefs[KEY_APP_GOOGLE_CLIENT_ID] ?: "",
            googleClientSecret = prefs[KEY_APP_GOOGLE_CLIENT_SECRET] ?: "",
            dropboxAppKey      = prefs[KEY_APP_DROPBOX_KEY] ?: "",
            dropboxAppSecret   = prefs[KEY_APP_DROPBOX_SECRET] ?: "",
            onedriveClientId   = prefs[KEY_APP_ONEDRIVE_CLIENT_ID] ?: "",
            boxClientId        = prefs[KEY_APP_BOX_CLIENT_ID] ?: "",
            boxClientSecret    = prefs[KEY_APP_BOX_CLIENT_SECRET] ?: ""
        )
    }

    suspend fun saveAppOAuthSettings(s: AppOAuthSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_GOOGLE_CLIENT_ID]     = s.googleClientId
            prefs[KEY_APP_GOOGLE_CLIENT_SECRET] = s.googleClientSecret
            prefs[KEY_APP_DROPBOX_KEY]          = s.dropboxAppKey
            prefs[KEY_APP_DROPBOX_SECRET]       = s.dropboxAppSecret
            prefs[KEY_APP_ONEDRIVE_CLIENT_ID]   = s.onedriveClientId
            prefs[KEY_APP_BOX_CLIENT_ID]        = s.boxClientId
            prefs[KEY_APP_BOX_CLIENT_SECRET]    = s.boxClientSecret
        }
    }

    // ── Account CRUD ──────────────────────────────────────────────────────────

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
