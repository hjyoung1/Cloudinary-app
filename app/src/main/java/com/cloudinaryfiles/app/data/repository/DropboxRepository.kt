package com.cloudinaryfiles.app.data.repository

import com.cloudinaryfiles.app.AppLogger
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

    private val LOG = "DropboxRepo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val AUTH_URL     = "https://www.dropbox.com/oauth2/authorize"
        const val TOKEN_URL    = "https://api.dropboxapi.com/oauth2/token"
        const val REDIRECT_URI = "com.cloudinaryfiles.app:/oauth2redirect"
        const val SCOPES       = "files.content.read files.metadata.read"
    }

    fun fetchAllAssets(account: NamedAccount): Flow<RepositoryResult> = flow {
        AppLogger.i(LOG, "══ fetchAllAssets START ══════════════════════════════════")
        AppLogger.i(LOG, "  Account id        : ${account.id}")
        AppLogger.i(LOG, "  Account name      : '${account.name}'")
        AppLogger.i(LOG, "  Provider key      : ${account.providerKey}")
        AppLogger.i(LOG, "  oauthClientId     : ${AppLogger.mask(account.oauthClientId)}")
        AppLogger.i(LOG, "  oauthClientSecret : ${AppLogger.mask(account.oauthClientSecret)}")
        AppLogger.i(LOG, "  oauthAccessToken  : ${AppLogger.mask(account.oauthAccessToken)}")
        AppLogger.i(LOG, "  oauthRefreshToken : ${AppLogger.mask(account.oauthRefreshToken)}")

        val now = System.currentTimeMillis() / 1000
        val expiry = account.oauthTokenExpiry
        AppLogger.i(LOG, "  Token expiry epoch: $expiry (now=$now, diff=${expiry - now}s → " +
                if (expiry == 0L) "never set" else if (now < expiry - 60) "VALID" else "EXPIRED" + ")")

        emit(RepositoryResult.Progress(0, "Connecting to Dropbox…"))
        try {
            AppLogger.d(LOG, "Calling freshToken()…")
            val token = freshToken(account)

            if (token == null) {
                AppLogger.e(LOG, "freshToken() returned null — no access token")
                throw Exception("No access token. Please reconnect your Dropbox account.")
            }
            AppLogger.i(LOG, "freshToken() OK — token len=${token.length}")

            val allAssets = mutableListOf<CloudinaryAsset>()
            var hasMore = true
            var cursor: String? = null
            var pageNum = 0

            while (hasMore) {
                pageNum++
                val (url, bodyStr) = if (cursor == null)
                    "https://api.dropboxapi.com/2/files/list_folder" to
                        """{"path":"","recursive":true,"limit":2000}"""
                else
                    "https://api.dropboxapi.com/2/files/list_folder/continue" to
                        """{"cursor":"$cursor"}"""

                AppLogger.d(LOG, "list_folder page $pageNum — cursor=${cursor?.take(20)?.plus("…") ?: "null"}")
                AppLogger.request(LOG, "POST", url)

                val json = withRetryBlocking(maxAttempts = 3) { attempt ->
                    if (attempt > 1) AppLogger.w(LOG, "list_folder: retry attempt $attempt")
                    val body = bodyStr.toRequestBody("application/json".toMediaType())
                    val req  = Request.Builder().url(url).post(body)
                        .header("Authorization", "Bearer $token").build()
                    val t0 = System.currentTimeMillis()
                    val resp     = client.newCall(req).execute()
                    val elapsed  = System.currentTimeMillis() - t0
                    val respBody = resp.body?.use { it.string() } ?: ""
                    AppLogger.response(LOG, "POST", url, resp.code, respBody.take(400), elapsed)

                    val j = JSONObject(respBody)
                    if (!resp.isSuccessful) {
                        val errSum = j.optString("error_summary", "API error (HTTP ${resp.code})")
                        AppLogger.e(LOG, "list_folder FAILED: $errSum")
                        AppLogger.e(LOG, "  Full body: $respBody")

                        // Diagnose common issues
                        when {
                            resp.code == 401 -> AppLogger.e(LOG, "  ⚠ 401 — token invalid or revoked. User must re-authenticate.")
                            errSum.contains("path/not_found", true) -> AppLogger.e(LOG, "  ⚠ Path not found")
                            errSum.contains("expired_access_token", true) -> AppLogger.e(LOG, "  ⚠ Access token expired")
                        }
                        throw Exception(errSum)
                    }
                    j
                }

                val entries = json.getJSONArray("entries")
                AppLogger.i(LOG, "  Page $pageNum: ${entries.length()} entries, has_more=${json.optBoolean("has_more")}")

                var skippedFolders = 0
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    if (entry.optString(".tag") != "file") { skippedFolders++; continue }

                    val name     = entry.getString("name")
                    val path     = entry.getString("path_lower")
                    val ext      = name.substringAfterLast(".", "").lowercase()
                    val size     = entry.optLong("size", 0L)
                    val modified = entry.optString("server_modified")
                    val resType  = extensionToResourceType(ext)

                    AppLogger.v(LOG, "  entry[$i]: name='$name' path=$path ext=$ext size=$size type=$resType")

                    // Get a 4-hour temporary direct link
                    val streamUrl = try {
                        AppLogger.d(LOG, "  Getting temp link for: $path")
                        withRetryBlocking(maxAttempts = 2) { attempt ->
                            val tmpLinkBody = """{"path":"$path"}""".toRequestBody("application/json".toMediaType())
                            val tmpReq  = Request.Builder()
                                .url("https://api.dropboxapi.com/2/files/get_temporary_link")
                                .post(tmpLinkBody)
                                .header("Authorization", "Bearer $token")
                                .build()
                            val t0 = System.currentTimeMillis()
                            val tmpResp = client.newCall(tmpReq).execute()
                            val elapsed = System.currentTimeMillis() - t0
                            val tmpBody = tmpResp.body?.use { it.string() } ?: ""
                            if (!tmpResp.isSuccessful) {
                                AppLogger.w(LOG, "  get_temporary_link failed (attempt $attempt): HTTP ${tmpResp.code} — $tmpBody")
                            }
                            AppLogger.response(LOG, "POST", "get_temporary_link", tmpResp.code, null, elapsed)
                            val tmpJson = JSONObject(tmpBody)
                            tmpJson.optString("link", "")
                        }
                    } catch (e: Exception) {
                        AppLogger.w(LOG, "  get_temporary_link EXCEPTION for $path: ${e.message}")
                        ""
                    }
                    AppLogger.v(LOG, "  streamUrl for $name: ${if (streamUrl.isBlank()) "<empty>" else "obtained"}")

                    val mediaMeta  = entry.optJSONObject("media_info")?.optJSONObject("metadata")
                    val durationMs = mediaMeta?.optLong("duration", 0L) ?: 0L
                    val durationSec = if (durationMs > 0) durationMs / 1000.0 else null

                    allAssets += CloudinaryAsset(
                        assetId      = "dropbox:$path",
                        publicId     = path,
                        format       = ext,
                        resourceType = resType,
                        type         = "upload",
                        createdAt    = modified,
                        bytes        = size,
                        url          = streamUrl,
                        secureUrl    = streamUrl,
                        displayName  = name.substringBeforeLast("."),
                        duration     = durationSec
                    )
                }
                if (skippedFolders > 0) AppLogger.d(LOG, "  Skipped $skippedFolders folder entries")

                hasMore = json.optBoolean("has_more", false)
                cursor  = json.optString("cursor").ifEmpty { null }
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} files…"))
            }

            AppLogger.i(LOG, "══ fetchAllAssets SUCCESS — ${allAssets.size} total files ══")
            emit(RepositoryResult.Success(allAssets))

        } catch (e: Exception) {
            AppLogger.e(LOG, "══ fetchAllAssets FAILED ══", e)
            AppLogger.e(LOG, "  Exception type    : ${e.javaClass.name}")
            AppLogger.e(LOG, "  Exception message : ${e.message}")

            when {
                e.message?.contains("UnknownHost", true) == true ||
                e.message?.contains("Unable to resolve", true) == true -> {
                    AppLogger.e(LOG, "  ⚠ DNS failure — cannot resolve api.dropboxapi.com")
                    AppLogger.e(LOG, "  ⚠ Possible causes:")
                    AppLogger.e(LOG, "     1. No internet connection on the device")
                    AppLogger.e(LOG, "     2. VPN/proxy is blocking Dropbox API domains")
                    AppLogger.e(LOG, "     3. Custom DNS is failing to resolve the hostname")
                    AppLogger.e(LOG, "     4. Firewall rule blocking outbound HTTPS on port 443")
                    AppLogger.e(LOG, "  ⚠ Verify: can the device reach https://api.dropboxapi.com/ in a browser?")
                }
                e.message?.contains("timeout", true) == true ->
                    AppLogger.e(LOG, "  ⚠ Timeout — poor network signal or server unreachable")
                e.message?.contains("expired", true) == true ->
                    AppLogger.e(LOG, "  ⚠ Token expired — user needs to re-authenticate")
            }

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
        AppLogger.d(LOG, "freshToken(): checking…")
        if (account.oauthAccessToken.isBlank()) {
            AppLogger.w(LOG, "freshToken(): oauthAccessToken is blank → null")
            return null
        }
        val expiry = account.oauthTokenExpiry
        val now    = System.currentTimeMillis() / 1000
        AppLogger.d(LOG, "freshToken(): expiry=$expiry now=$now diff=${expiry - now}s")

        if (expiry == 0L || now < expiry - 60) {
            AppLogger.d(LOG, "freshToken(): token valid → returning existing")
            return account.oauthAccessToken
        }

        AppLogger.i(LOG, "freshToken(): token expired — refreshing…")
        if (account.oauthRefreshToken.isBlank()) {
            AppLogger.w(LOG, "freshToken(): no refresh token → returning expired token as fallback")
            return account.oauthAccessToken
        }

        return try {
            val newToken = refreshToken(account)
            AppLogger.i(LOG, "freshToken(): refreshed OK (len=${newToken.length})")
            newToken
        } catch (e: Exception) {
            AppLogger.e(LOG, "freshToken(): refresh FAILED → returning old token", e)
            account.oauthAccessToken
        }
    }

    data class TokenResult(val accessToken: String, val refreshToken: String, val expiryEpoch: Long)

    fun exchangeCodeForToken(account: NamedAccount, code: String, port: Int, pkceVerifier: String? = null): TokenResult {
        AppLogger.i(LOG, "exchangeCodeForToken(): port=$port, code=${code.take(10)}…, hasPkce=${pkceVerifier != null}")
        AppLogger.d(LOG, "  TOKEN_URL    : $TOKEN_URL")
        AppLogger.d(LOG, "  redirectUri  : http://127.0.0.1:$port")
        AppLogger.d(LOG, "  clientId     : ${AppLogger.mask(account.oauthClientId)}")
        AppLogger.d(LOG, "  clientSecret : ${if (account.oauthClientSecret.isBlank()) "<none>" else AppLogger.mask(account.oauthClientSecret)}")

        return withRetryBlocking(maxAttempts = 3) { attempt ->
            AppLogger.d(LOG, "exchangeCodeForToken(): attempt $attempt")
            val bodyBuilder = FormBody.Builder()
                .add("code",         code)
                .add("client_id",    account.oauthClientId)
                .add("redirect_uri", "http://127.0.0.1:$port")
                .add("grant_type",   "authorization_code")

            if (pkceVerifier != null) {
                AppLogger.d(LOG, "  Using PKCE code_verifier")
                bodyBuilder.add("code_verifier", pkceVerifier)
            } else if (account.oauthClientSecret.isNotBlank()) {
                AppLogger.d(LOG, "  Using client_secret")
                bodyBuilder.add("client_secret", account.oauthClientSecret)
            } else {
                AppLogger.w(LOG, "  ⚠ Neither PKCE nor client_secret provided — token exchange may fail")
            }

            val body = bodyBuilder.build()
            val t0 = System.currentTimeMillis()
            AppLogger.request(LOG, "POST", TOKEN_URL)
            val resp     = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
            val elapsed  = System.currentTimeMillis() - t0
            val respBody = resp.body?.use { it.string() } ?: ""
            AppLogger.response(LOG, "POST", TOKEN_URL, resp.code, respBody.take(400), elapsed)

            val json = JSONObject(respBody)
            if (!resp.isSuccessful) {
                val errMsg = json.optString("error_description",
                    "Token exchange failed (HTTP ${resp.code})")
                AppLogger.e(LOG, "exchangeCodeForToken(): FAILED — $errMsg")
                AppLogger.e(LOG, "  Full response: $respBody")
                throw Exception(errMsg)
            }

            val result = TokenResult(
                json.getString("access_token"),
                json.optString("refresh_token", ""),
                System.currentTimeMillis() / 1000 + json.optLong("expires_in", 14400)
            )
            AppLogger.i(LOG, "exchangeCodeForToken(): SUCCESS — " +
                    "accessLen=${result.accessToken.length}, " +
                    "hasRefresh=${result.refreshToken.isNotBlank()}, " +
                    "expiresAt=${result.expiryEpoch}")
            result
        }
    }

    private fun refreshToken(account: NamedAccount): String {
        AppLogger.i(LOG, "refreshToken(): calling $TOKEN_URL")
        return withRetryBlocking(maxAttempts = 2) { attempt ->
            AppLogger.d(LOG, "refreshToken(): attempt $attempt")
            val bodyBuilder = FormBody.Builder()
                .add("client_id",     account.oauthClientId)
                .add("refresh_token", account.oauthRefreshToken)
                .add("grant_type",    "refresh_token")

            if (account.oauthClientSecret.isNotBlank()) {
                bodyBuilder.add("client_secret", account.oauthClientSecret)
            }

            val t0 = System.currentTimeMillis()
            val resp     = client.newCall(Request.Builder().url(TOKEN_URL).post(bodyBuilder.build()).build()).execute()
            val elapsed  = System.currentTimeMillis() - t0
            val respBody = resp.body?.use { it.string() } ?: ""
            AppLogger.response(LOG, "POST (refresh)", TOKEN_URL, resp.code, respBody.take(300), elapsed)

            val json = JSONObject(respBody)
            if (!resp.isSuccessful) {
                val err = json.optString("error_description", "Refresh failed (HTTP ${resp.code})")
                AppLogger.e(LOG, "refreshToken(): FAILED — $err")
                throw Exception(err)
            }
            val newTok = json.getString("access_token")
            AppLogger.i(LOG, "refreshToken(): OK (new token len=${newTok.length})")
            newTok
        }
    }

    private fun extensionToResourceType(ext: String) = when {
        ext in setOf("mp3","wav","ogg","flac","aac","m4a","opus") -> "video"
        ext in setOf("mp4","mov","avi","mkv","webm")              -> "video"
        ext in setOf("jpg","jpeg","png","gif","webp","avif")      -> "image"
        else                                                       -> "raw"
    }

    fun deleteFile(account: NamedAccount, path: String): Boolean {
        AppLogger.i(LOG, "deleteFile(): path=$path")
        return try {
            val token = freshToken(account)
            if (token == null) { AppLogger.e(LOG, "deleteFile(): no token"); return false }
            val body = JSONObject().put("path", if (path.startsWith("/")) path else "/$path").toString()
            val req  = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/delete_v2")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                .build()
            val t0   = System.currentTimeMillis()
            val resp = client.newCall(req).execute()
            AppLogger.response(LOG, "POST (delete)", "files/delete_v2", resp.code, null, System.currentTimeMillis() - t0)
            resp.isSuccessful
        } catch (e: Exception) {
            AppLogger.e(LOG, "deleteFile(): EXCEPTION", e)
            false
        }
    }
}
