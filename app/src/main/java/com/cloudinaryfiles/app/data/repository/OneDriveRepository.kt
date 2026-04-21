package com.cloudinaryfiles.app.data.repository

import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.preferences.NamedAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OneDriveRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val AUTH_URL    = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        const val TOKEN_URL   = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        const val SCOPES      = "Files.Read offline_access"
        const val REDIRECT_URI = "com.cloudinaryfiles.app:/oauth2redirect"
    }

    fun fetchAllAssets(account: NamedAccount): Flow<RepositoryResult> = flow {
        emit(RepositoryResult.Progress(0, "Connecting to OneDrive…"))
        try {
            val token = freshToken(account) ?: throw Exception("No access token. Please reconnect.")
            val allAssets = mutableListOf<CloudinaryAsset>()
            // Use delta/search to get all files recursively
            var nextUrl: String? = "https://graph.microsoft.com/v1.0/me/drive/root/search(q='')?" +
                    "\$select=id,name,size,createdDateTime,file,@microsoft.graph.downloadUrl&\$top=1000"

            while (nextUrl != null) {
                val json = getJson(nextUrl, token)
                val items = json.optJSONArray("value") ?: break
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    // Skip folders
                    if (!item.has("file")) continue
                    val id   = item.getString("id")
                    val name = item.getString("name")
                    val ext  = name.substringAfterLast(".", "").lowercase()
                    val size = item.optLong("size", 0L)
                    val created = item.optString("createdDateTime")
                    val mime = item.optJSONObject("file")?.optString("mimeType") ?: ""
                    // @microsoft.graph.downloadUrl is a pre-authenticated link, no auth header needed
                    val downloadUrl = item.optString("@microsoft.graph.downloadUrl").ifEmpty {
                        "https://graph.microsoft.com/v1.0/me/drive/items/$id/content"
                    }
                    allAssets += CloudinaryAsset(
                        assetId   = "onedrive:$id",
                        publicId  = id,
                        format    = ext,
                        resourceType = mimeToResourceType(mime, ext),
                        type      = "upload",
                        createdAt = created,
                        bytes     = size,
                        url       = downloadUrl,
                        secureUrl = downloadUrl,
                        displayName = name.substringBeforeLast(".")
                    )
                }
                nextUrl = json.optString("@odata.nextLink").ifEmpty { null }
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} files…"))
            }
            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            emit(RepositoryResult.Error("OneDrive: ${e.message}"))
        }
    }

    fun freshToken(account: NamedAccount): String? {
        if (account.oauthAccessToken.isBlank()) return null
        val expiry = account.oauthTokenExpiry
        val now = System.currentTimeMillis() / 1000
        if (expiry == 0L || now < expiry - 60) return account.oauthAccessToken
        if (account.oauthRefreshToken.isBlank()) return account.oauthAccessToken
        return try { refreshToken(account) } catch (_: Exception) { account.oauthAccessToken }
    }

    data class TokenResult(val accessToken: String, val refreshToken: String, val expiryEpoch: Long)

    fun exchangeCodeForToken(account: NamedAccount, code: String, port: Int): TokenResult {
        val body = FormBody.Builder()
            .add("code", code)
            .add("client_id", account.oauthClientId)
            .add("redirect_uri", "http://127.0.0.1:$port")
            .add("grant_type", "authorization_code")
            .add("scope", SCOPES)
            .apply { if (account.oauthClientSecret.isNotBlank()) add("client_secret", account.oauthClientSecret) }
            .build()
        val resp = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
        val json = JSONObject(resp.body?.use { it.string() } ?: "")
        if (!resp.isSuccessful) throw Exception(json.optString("error_description", "Token exchange failed"))
        return TokenResult(
            json.getString("access_token"),
            json.optString("refresh_token", ""),
            System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)
        )
    }

    private fun refreshToken(account: NamedAccount): String {
        val body = FormBody.Builder()
            .add("client_id", account.oauthClientId)
            .add("refresh_token", account.oauthRefreshToken)
            .add("grant_type", "refresh_token")
            .add("scope", SCOPES)
            .apply { if (account.oauthClientSecret.isNotBlank()) add("client_secret", account.oauthClientSecret) }
            .build()
        val resp = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
        val json = JSONObject(resp.body?.use { it.string() } ?: "")
        return json.optString("access_token").ifEmpty { account.oauthAccessToken }
    }

    private fun getJson(url: String, token: String): JSONObject {
        val req = Request.Builder().url(url).get().header("Authorization", "Bearer $token").build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.use { it.string() } ?: ""
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${body.take(200)}")
        return JSONObject(body)
    }

    private fun mimeToResourceType(mime: String, ext: String) = when {
        mime.startsWith("audio") || ext in setOf("mp3","wav","ogg","flac","aac","m4a") -> "video"
        mime.startsWith("video") || ext in setOf("mp4","mov","avi","mkv","webm") -> "video"
        mime.startsWith("image") -> "image"
        else -> "raw"
    }
}
