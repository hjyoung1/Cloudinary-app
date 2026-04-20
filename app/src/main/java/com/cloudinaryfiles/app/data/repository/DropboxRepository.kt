package com.cloudinaryfiles.app.data.repository

import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.preferences.NamedAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DropboxRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val AUTH_URL    = "https://www.dropbox.com/oauth2/authorize"
        const val TOKEN_URL   = "https://api.dropboxapi.com/oauth2/token"
        const val REDIRECT_URI = "com.cloudinaryfiles.app://oauth"
        const val SCOPES      = "files.content.read files.metadata.read"
    }

    fun fetchAllAssets(account: NamedAccount): Flow<RepositoryResult> = flow {
        emit(RepositoryResult.Progress(0, "Connecting to Dropbox…"))
        try {
            val token = freshToken(account) ?: throw Exception("No access token. Please reconnect.")
            val allAssets = mutableListOf<CloudinaryAsset>()
            var hasMore = true
            var cursor: String? = null

            while (hasMore) {
                val (url, bodyStr) = if (cursor == null)
                    "https://api.dropboxapi.com/2/files/list_folder" to
                        """{"path":"","recursive":true,"limit":2000}"""
                else
                    "https://api.dropboxapi.com/2/files/list_folder/continue" to
                        """{"cursor":"$cursor"}"""

                val body = bodyStr.toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(url).post(body)
                    .header("Authorization", "Bearer $token").build()
                val resp = client.newCall(req).execute()
                val json = JSONObject(resp.body!!.string())
                if (!resp.isSuccessful) throw Exception(json.optString("error_summary", "API error"))

                val entries = json.getJSONArray("entries")
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    if (entry.optString(".tag") != "file") continue
                    val name = entry.getString("name")
                    val path = entry.getString("path_lower")
                    val ext  = name.substringAfterLast(".", "").lowercase()
                    val size = entry.optLong("size", 0L)
                    val modified = entry.optString("server_modified")

                    // Get a 4-hour temporary direct link (no auth needed for streaming)
                    val tmpLinkBody = """{"path":"$path"}""".toRequestBody("application/json".toMediaType())
                    val tmpReq = Request.Builder()
                        .url("https://api.dropboxapi.com/2/files/get_temporary_link")
                        .post(tmpLinkBody)
                        .header("Authorization", "Bearer $token")
                        .build()
                    val tmpResp = client.newCall(tmpReq).execute()
                    val tmpJson = JSONObject(tmpResp.body!!.string())
                    val streamUrl = tmpJson.optString("link", "")

                    allAssets += CloudinaryAsset(
                        assetId   = "dropbox:$path",
                        publicId  = path,
                        format    = ext,
                        resourceType = extensionToResourceType(ext),
                        type      = "upload",
                        createdAt = modified,
                        bytes     = size,
                        url       = streamUrl,
                        secureUrl = streamUrl,
                        displayName = name.substringBeforeLast(".")
                    )
                }

                hasMore = json.optBoolean("has_more", false)
                cursor  = json.optString("cursor").ifEmpty { null }
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} files…"))
            }
            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            emit(RepositoryResult.Error("Dropbox: ${e.message}"))
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
            json.optString("refresh_token", ""),
            System.currentTimeMillis() / 1000 + json.optLong("expires_in", 14400)
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
        return JSONObject(resp.body!!.string()).getString("access_token")
    }

    private fun extensionToResourceType(ext: String) = when {
        ext in setOf("mp3","wav","ogg","flac","aac","m4a","opus") -> "video"
        ext in setOf("mp4","mov","avi","mkv","webm") -> "video"
        ext in setOf("jpg","jpeg","png","gif","webp","avif") -> "image"
        else -> "raw"
    }
}
