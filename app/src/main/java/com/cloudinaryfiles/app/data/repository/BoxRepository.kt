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

class BoxRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val AUTH_URL    = "https://account.box.com/api/oauth2/authorize"
        const val TOKEN_URL   = "https://api.box.com/oauth2/token"
        const val REDIRECT_URI = "com.cloudinaryfiles.app:/oauth2redirect"
    }

    fun fetchAllAssets(account: NamedAccount): Flow<RepositoryResult> = flow {
        emit(RepositoryResult.Progress(0, "Connecting to Box…"))
        try {
            val token = account.oauthAccessToken.ifBlank {
                throw Exception("No access token. Please reconnect.")
            }
            val allAssets = mutableListOf<CloudinaryAsset>()
            // Search all files in the account
            var offset = 0
            val limit = 1000
            var total = Int.MAX_VALUE
            while (offset < total) {
                val url = "https://api.box.com/2.0/folders/0/items" +
                        "?fields=id,name,type,size,created_at,modified_at&limit=$limit&offset=$offset"
                val json = getJson(url, token)
                total = json.optInt("total_count", 0)
                val entries = json.optJSONArray("entries") ?: break
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    if (entry.optString("type") != "file") continue
                    val id   = entry.getString("id")
                    val name = entry.getString("name")
                    val ext  = name.substringAfterLast(".", "").lowercase()
                    val size = entry.optLong("size", 0L)
                    val created = entry.optString("created_at")
                    val downloadUrl = "https://api.box.com/2.0/files/$id/content"
                    allAssets += CloudinaryAsset(
                        assetId   = "box:$id",
                        publicId  = id,
                        format    = ext,
                        resourceType = extensionToResourceType(ext),
                        type      = "upload",
                        createdAt = created,
                        bytes     = size,
                        url       = downloadUrl,
                        secureUrl = downloadUrl,
                        displayName = name.substringBeforeLast(".")
                    )
                }
                offset += entries.length()
                if (entries.length() < limit) break
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} files…"))
            }
            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            emit(RepositoryResult.Error("Box: ${e.message}"))
        }
    }

    data class TokenResult(val accessToken: String, val refreshToken: String, val expiryEpoch: Long)

    fun exchangeCodeForToken(account: NamedAccount, code: String, port: Int): TokenResult {
        val body = FormBody.Builder()
            .add("code", code)
            .add("client_id", account.oauthClientId)
            .add("client_secret", account.oauthClientSecret)
            .add("redirect_uri", "http://127.0.0.1:$port")
            .add("grant_type", "authorization_code")
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

    private fun getJson(url: String, token: String): JSONObject {
        val req = Request.Builder().url(url).get().header("Authorization", "Bearer $token").build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.use { it.string() } ?: ""
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${body.take(200)}")
        return JSONObject(body)
    }

    private fun extensionToResourceType(ext: String) = when {
        ext in setOf("mp3","wav","ogg","flac","aac","m4a","opus") -> "video"
        ext in setOf("mp4","mov","avi","mkv","webm") -> "video"
        ext in setOf("jpg","jpeg","png","gif","webp","avif") -> "image"
        else -> "raw"
    }
}
