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
        const val REDIRECT_URI = "com.cloudinaryfiles.app:/oauth2redirect"
        const val SCOPES      = "files.content.read files.metadata.read"
    }

    fun fetchAllAssets(account: NamedAccount): Flow<RepositoryResult> = flow {
        emit(RepositoryResult.Progress(0, "Connecting to Dropbox…"))
        try {
            val token = freshToken(account) ?: throw Exception("No access token. Please reconnect your Dropbox account.")
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

                val json = withRetryBlocking(maxAttempts = 3) {
                    val body = bodyStr.toRequestBody("application/json".toMediaType())
                    val req = Request.Builder().url(url).post(body)
                        .header("Authorization", "Bearer $token").build()
                    val resp = client.newCall(req).execute()
                    val respBody = resp.body?.use { it.string() } ?: ""
                    val j = JSONObject(respBody)
                    if (!resp.isSuccessful) throw Exception(j.optString("error_summary", "API error (HTTP ${resp.code})"))
                    j
                }

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
                    val streamUrl = try {
                        withRetryBlocking(maxAttempts = 2) {
                            val tmpLinkBody = """{"path":"$path"}""".toRequestBody("application/json".toMediaType())
                            val tmpReq = Request.Builder()
                                .url("https://api.dropboxapi.com/2/files/get_temporary_link")
                                .post(tmpLinkBody)
                                .header("Authorization", "Bearer $token")
                                .build()
                            val tmpResp = client.newCall(tmpReq).execute()
                            val tmpJson = JSONObject(tmpResp.body?.use { it.string() } ?: "")
                            tmpJson.optString("link", "")
                        }
                    } catch (_: Exception) { "" }

                    // Extract media info duration if available
                    val mediaMeta = entry.optJSONObject("media_info")
                        ?.optJSONObject("metadata")
                    val durationMs = mediaMeta?.optLong("duration", 0L) ?: 0L
                    val durationSec = if (durationMs > 0) durationMs / 1000.0 else null

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
                        displayName = name.substringBeforeLast("."),
                        duration  = durationSec
                    )
                }

                hasMore = json.optBoolean("has_more", false)
                cursor  = json.optString("cursor").ifEmpty { null }
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} files…"))
            }
            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            val msg = when {
                e.message?.contains("UnknownHost", true) == true ||
                e.message?.contains("Unable to resolve", true) == true ->
                    "Dropbox: Can't reach server. Check your internet connection and try again."
                e.message?.contains("timeout", true) == true ->
                    "Dropbox: Connection timed out. Try again when you have a stronger signal."
                else -> "Dropbox: ${e.message}"
            }
            emit(RepositoryResult.Error(msg))
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

    fun exchangeCodeForToken(account: NamedAccount, code: String, port: Int, pkceVerifier: String? = null): TokenResult {
        return withRetryBlocking(maxAttempts = 3) {
            val bodyBuilder = FormBody.Builder()
                .add("code", code)
                .add("client_id", account.oauthClientId)
                .add("redirect_uri", "http://127.0.0.1:$port")
                .add("grant_type", "authorization_code")

            if (pkceVerifier != null) {
                bodyBuilder.add("code_verifier", pkceVerifier)
            } else if (account.oauthClientSecret.isNotBlank()) {
                bodyBuilder.add("client_secret", account.oauthClientSecret)
            }

            val body = bodyBuilder.build()
            val resp = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
            val respBody = resp.body?.use { it.string() } ?: ""
            val json = JSONObject(respBody)
            if (!resp.isSuccessful) throw Exception(json.optString("error_description",
                "Token exchange failed (HTTP ${resp.code})"))
            TokenResult(
                json.getString("access_token"),
                json.optString("refresh_token", ""),
                System.currentTimeMillis() / 1000 + json.optLong("expires_in", 14400)
            )
        }
    }

    private fun refreshToken(account: NamedAccount): String {
        return withRetryBlocking(maxAttempts = 2) {
            val bodyBuilder = FormBody.Builder()
                .add("client_id", account.oauthClientId)
                .add("refresh_token", account.oauthRefreshToken)
                .add("grant_type", "refresh_token")

            if (account.oauthClientSecret.isNotBlank()) {
                bodyBuilder.add("client_secret", account.oauthClientSecret)
            }

            val body = bodyBuilder.build()
            val resp = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
            val respBody = resp.body?.use { it.string() } ?: ""
            val json = JSONObject(respBody)
            if (!resp.isSuccessful) throw Exception(json.optString("error_description", "Refresh failed"))
            json.getString("access_token")
        }
    }

    private fun extensionToResourceType(ext: String) = when {
        ext in setOf("mp3","wav","ogg","flac","aac","m4a","opus") -> "video"
        ext in setOf("mp4","mov","avi","mkv","webm") -> "video"
        ext in setOf("jpg","jpeg","png","gif","webp","avif") -> "image"
        else -> "raw"
    }

    fun deleteFile(account: NamedAccount, path: String): Boolean {
        return try {
            val token = freshToken(account) ?: return false
            val body = JSONObject().put("path", if (path.startsWith("/")) path else "/$path").toString()
            val req = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/delete_v2")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                .build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
