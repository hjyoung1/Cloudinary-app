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

class GoogleDriveRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val AUTH_URL    = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL   = "https://oauth2.googleapis.com/token"
        const val SCOPES      = "https://www.googleapis.com/auth/drive.readonly"
        const val REDIRECT_URI = "com.cloudinaryfiles.app://oauth"
    }

    fun fetchAllAssets(account: NamedAccount): Flow<RepositoryResult> = flow {
        emit(RepositoryResult.Progress(0, "Connecting to Google Drive…"))
        try {
            val token = freshToken(account) ?: throw Exception("No access token. Please reconnect.")
            val allAssets = mutableListOf<CloudinaryAsset>()
            var pageToken: String? = null
            do {
                val url = buildString {
                    append("https://www.googleapis.com/drive/v3/files")
                    append("?q=trashed%3Dfalse")
                    append("&pageSize=1000")
                    append("&fields=nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime)")
                    if (pageToken != null) append("&pageToken=$pageToken")
                }
                val resp = getJson(url, token)
                val files = resp.optJSONArray("files") ?: break
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val mime = f.optString("mimeType")
                    // Skip Google Docs / folders
                    if (mime.startsWith("application/vnd.google-apps")) continue
                    val id   = f.getString("id")
                    val name = f.getString("name")
                    val ext  = name.substringAfterLast(".", "").lowercase()
                    val size = f.optLong("size", 0L)
                    val created = f.optString("createdTime")
                    // Stream URL with access token embedded (no extra request at play time)
                    val streamUrl = "https://www.googleapis.com/drive/v3/files/$id?alt=media&access_token=$token"
                    allAssets += CloudinaryAsset(
                        assetId   = "gdrive:$id",
                        publicId  = id,
                        format    = ext,
                        resourceType = mimeToResourceType(mime, ext),
                        type      = "upload",
                        createdAt = created,
                        bytes     = size,
                        url       = streamUrl,
                        secureUrl = streamUrl,
                        displayName = name.substringBeforeLast(".")
                    )
                }
                pageToken = resp.optString("nextPageToken").ifEmpty { null }
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} files…"))
            } while (pageToken != null)
            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            emit(RepositoryResult.Error("Google Drive: ${e.message}"))
        }
    }

    /** Returns a valid access token, refreshing if expired */
    fun freshToken(account: NamedAccount): String? {
        if (account.oauthAccessToken.isBlank()) return null
        val expiry = account.oauthTokenExpiry
        val now = System.currentTimeMillis() / 1000
        // Token still valid (with 60s buffer)
        if (expiry == 0L || now < expiry - 60) return account.oauthAccessToken
        // Need refresh
        if (account.oauthRefreshToken.isBlank()) return account.oauthAccessToken
        return try { refreshToken(account) } catch (_: Exception) { account.oauthAccessToken }
    }

    data class TokenResult(val accessToken: String, val refreshToken: String, val expiryEpoch: Long)

    fun exchangeCodeForToken(account: NamedAccount, code: String): TokenResult {
        val body = FormBody.Builder()
            .add("code", code)
            .add("client_id", account.oauthClientId)
            .add("client_secret", account.oauthClientSecret)
            .add("redirect_uri", REDIRECT_URI)
            .add("grant_type", "authorization_code")
            .build()
        val resp = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
        val json = JSONObject(resp.body!!.string())
        if (!resp.isSuccessful) throw Exception(json.optString("error_description", "Token exchange failed"))
        return TokenResult(
            json.getString("access_token"),
            json.optString("refresh_token", account.oauthRefreshToken),
            System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)
        )
    }

    private fun refreshToken(account: NamedAccount): String {
        val body = FormBody.Builder()
            .add("client_id", account.oauthClientId)
            .add("client_secret", account.oauthClientSecret)
            .add("refresh_token", account.oauthRefreshToken)
            .add("grant_type", "refresh_token")
            .build()
        val resp = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
        val json = JSONObject(resp.body!!.string())
        return json.getString("access_token")
    }

    private fun getJson(url: String, token: String): JSONObject {
        val req = Request.Builder().url(url).get().header("Authorization", "Bearer $token").build()
        val resp = client.newCall(req).execute()
        val body = resp.body!!.string()
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
