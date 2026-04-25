package com.cloudinaryfiles.app.data.repository

import com.cloudinaryfiles.app.AppLogger
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.preferences.NamedAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GoogleDriveRepository {

    private val LOG = "GDriveRepo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val AUTH_URL   = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL  = "https://oauth2.googleapis.com/token"
        const val SCOPES     = "https://www.googleapis.com/auth/drive.readonly"

        fun buildAuthUrl(clientId: String, port: Int): String {
            val redirect = "http://127.0.0.1:$port"
            return "$AUTH_URL?client_id=$clientId" +
                    "&redirect_uri=${encode(redirect)}" +
                    "&response_type=code" +
                    "&scope=${encode(SCOPES)}" +
                    "&access_type=offline" +
                    "&prompt=consent"
        }

        private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    }

    // In-memory token cache: accountId -> Pair(accessToken, expiryEpochSec)
    private val tokenCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    // Folder ID -> folder name cache for breadcrumb paths
    private val folderNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

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
        AppLogger.i(LOG, "  Token expiry epoch: $expiry  (now=$now, diff=${expiry - now}s → " +
                if (expiry == 0L) "never set" else if (now < expiry - 60) "VALID" else "EXPIRED" + ")")

        emit(RepositoryResult.Progress(0, "Connecting to Google Drive…"))
        try {
            AppLogger.d(LOG, "Calling freshToken()…")
            val token = freshToken(account)

            if (token == null) {
                AppLogger.e(LOG, "freshToken() returned null — no access token available")
                AppLogger.e(LOG, "  oauthAccessToken blank? ${account.oauthAccessToken.isBlank()}")
                throw Exception("Not authenticated. Please reconnect your Google Drive account.")
            }
            AppLogger.i(LOG, "freshToken() returned a token (len=${token.length})")

            val allAssets = mutableListOf<CloudinaryAsset>()
            var pageToken: String? = null
            var pageNum = 0

            do {
                pageNum++
                val url = buildString {
                    append("https://www.googleapis.com/drive/v3/files")
                    append("?q=trashed%3Dfalse")
                    append("&pageSize=1000")
                    append("&fields=nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime,parents,videoMediaMetadata,thumbnailLink,hasThumbnail,webViewLink)")
                    if (pageToken != null) append("&pageToken=$pageToken")
                }
                AppLogger.d(LOG, "Fetching page $pageNum — pageToken=${pageToken?.take(20)?.plus("…") ?: "null"}")
                AppLogger.request(LOG, "GET", url)

                val json = getJson(url, token)

                val files = json.optJSONArray("files")
                AppLogger.i(LOG, "  Page $pageNum response: ${files?.length() ?: 0} files, " +
                        "nextPageToken=${json.optString("nextPageToken").take(20).ifEmpty { "null" }}")

                if (files == null) {
                    AppLogger.w(LOG, "  files array is null — breaking pagination")
                    break
                }

                var skippedGoogleDocs = 0
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val mime = f.optString("mimeType")
                    if (mime == "application/vnd.google-apps.folder") {
                        // Cache folder names for path resolution
                        folderNameCache[f.getString("id")] = f.optString("name", "")
                        skippedGoogleDocs++
                        continue
                    }
                    if (mime.startsWith("application/vnd.google-apps")) {
                        skippedGoogleDocs++
                        continue
                    }
                    // Apply folder exclusion filter
                    val fileName = f.optString("name", "")
                    if (account.safeExcludedFolders.any { excl ->
                        excl.isNotBlank() && (fileName.startsWith(excl, ignoreCase = true) ||
                        f.optString("id","").contains(excl, ignoreCase = true)) }) continue
                    val id      = f.getString("id")
                    val name    = f.getString("name")
                    val ext     = name.substringAfterLast(".", "").lowercase()
                    val size    = f.optLong("size", 0L)
                    val created = f.optString("createdTime")
                    // streamUrl = clean API URL (no token; auth via header at play time)
                    val streamUrl   = "https://www.googleapis.com/drive/v3/files/$id?alt=media"
                    val webViewLink = f.optString("webViewLink", "")  // used for copy-link
                    val thumbLink = f.optString("thumbnailLink", "").let { lnk ->
                        if (lnk.isNotBlank()) lnk.replace("=s220", "=s800") else lnk
                    }
                    // Build path-like publicId from parent folder name (best-effort)
                    val parentId  = f.optJSONArray("parents")?.optString(0, "") ?: ""
                    val parentName = if (parentId.isNotBlank()) folderNameCache[parentId] ?: parentId.take(8) else ""
                    val pathLikeId = if (parentName.isNotBlank()) "$parentName/$id" else id

                    val mediaMeta = f.optJSONObject("videoMediaMetadata")
                    val durationMs = mediaMeta?.optLong("durationMillis", 0L) ?: 0L
                    val durationSec = if (durationMs > 0) durationMs / 1000.0 else null

                    val resourceType = mimeToResourceType(mime, ext)
                    AppLogger.v(LOG, "  File[$i]: id=$id name='$name' mime=$mime ext=$ext size=$size type=$resourceType dur=${durationSec}s thumb=${thumbLink.take(40).ifEmpty{"none"}}")

                    allAssets += CloudinaryAsset(
                        assetId      = "gdrive:$id",
                        publicId     = pathLikeId,     // "FolderName/fileId" for folder grouping
                        format       = ext,
                        resourceType = resourceType,
                        type         = "upload",
                        createdAt    = created,
                        bytes        = size,
                        url          = streamUrl,
                        secureUrl    = if (webViewLink.isNotBlank()) webViewLink else streamUrl,
                        thumbnailUrl = thumbLink,
                        displayName  = name.substringBeforeLast("."),
                        duration     = durationSec
                    )
                }

                if (skippedGoogleDocs > 0) {
                    AppLogger.d(LOG, "  Skipped $skippedGoogleDocs Google Docs native files")
                }

                pageToken = json.optString("nextPageToken").ifEmpty { null }
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} files…"))
            } while (pageToken != null)

            AppLogger.i(LOG, "══ fetchAllAssets SUCCESS — ${allAssets.size} total files ══")
            emit(RepositoryResult.Success(allAssets))

        } catch (e: Exception) {
            AppLogger.e(LOG, "══ fetchAllAssets FAILED ══", e)
            AppLogger.e(LOG, "  Exception type    : ${e.javaClass.name}")
            AppLogger.e(LOG, "  Exception message : ${e.message}")

            // Diagnose common issues
            when {
                e.message == null ->
                    AppLogger.e(LOG, "  ⚠ e.message is NULL — likely a NullPointerException or API returned unexpected response")
                e.message?.contains("UnknownHost", true) == true ||
                e.message?.contains("Unable to resolve", true) == true -> {
                    AppLogger.e(LOG, "  ⚠ DNS resolution failure — check network/internet connectivity")
                    AppLogger.e(LOG, "  ⚠ If behind VPN or corporate proxy, that may block googleapis.com")
                }
                e.message?.contains("timeout", true) == true ->
                    AppLogger.e(LOG, "  ⚠ Connection timed out — poor network signal?")
                e.message?.contains("Not authenticated", true) == true ->
                    AppLogger.e(LOG, "  ⚠ Token is blank — user needs to re-authenticate")
                e.message?.contains("401", true) == true ->
                    AppLogger.e(LOG, "  ⚠ 401 Unauthorized — access token is invalid or expired")
                e.message?.contains("403", true) == true ->
                    AppLogger.e(LOG, "  ⚠ 403 Forbidden — insufficient OAuth scope or revoked access")
            }

            val msg = when {
                e.message?.contains("UnknownHost", true) == true ||
                e.message?.contains("Unable to resolve", true) == true ->
                    "Google Drive: Can't reach server. Check your internet connection."
                e.message?.contains("timeout", true) == true ->
                    "Google Drive: Connection timed out. Please try again."
                else -> "Google Drive: ${e.message}"
            }
            emit(RepositoryResult.Error(msg))
        }
    }
    .flowOn(Dispatchers.IO)

    fun freshToken(account: NamedAccount): String? {
        AppLogger.d(LOG, "freshToken(): checking token validity…")
        if (account.oauthAccessToken.isBlank()) {
            AppLogger.w(LOG, "freshToken(): oauthAccessToken is blank → returning null")
            return null
        }
        val now = System.currentTimeMillis() / 1000

        // Check in-memory cache first (avoids token endpoint on every file open)
        tokenCache[account.id]?.let { (cachedToken, cachedExpiry) ->
            if (now < cachedExpiry - 60) {
                AppLogger.d(LOG, "freshToken(): using cached token (expires in ${cachedExpiry - now}s)")
                return cachedToken
            }
            AppLogger.d(LOG, "freshToken(): cached token expired, need refresh")
        }

        val expiry = account.oauthTokenExpiry
        AppLogger.d(LOG, "freshToken(): stored expiry=$expiry now=$now diff=${expiry - now}s")

        if (expiry == 0L || now < expiry - 60) {
            AppLogger.d(LOG, "freshToken(): stored token still valid — caching and returning")
            tokenCache[account.id] = account.oauthAccessToken to (if (expiry == 0L) now + 3500L else expiry)
            return account.oauthAccessToken
        }

        AppLogger.i(LOG, "freshToken(): token expired — refreshing…")
        if (account.oauthRefreshToken.isBlank()) {
            AppLogger.w(LOG, "freshToken(): no refresh token → returning existing as fallback")
            return account.oauthAccessToken
        }
        return try {
            val (newToken, newExpiry) = refreshTokenFull(account)
            AppLogger.i(LOG, "freshToken(): refreshed OK (len=${newToken.length}, newExpiry=$newExpiry)")
            tokenCache[account.id] = newToken to newExpiry
            newToken
        } catch (e: Exception) {
            AppLogger.e(LOG, "freshToken(): refresh FAILED → returning stale token", e)
            account.oauthAccessToken
        }
    }

    data class TokenResult(val accessToken: String, val refreshToken: String, val expiryEpoch: Long)

    fun exchangeCodeForToken(account: NamedAccount, code: String, port: Int): TokenResult {
        AppLogger.i(LOG, "exchangeCodeForToken(): port=$port, code=${code.take(10)}…")
        AppLogger.d(LOG, "  clientId     : ${AppLogger.mask(account.oauthClientId)}")
        AppLogger.d(LOG, "  clientSecret : ${AppLogger.mask(account.oauthClientSecret)}")
        AppLogger.d(LOG, "  redirectUri  : http://127.0.0.1:$port")

        return withRetryBlocking(maxAttempts = 3) { attempt ->
            AppLogger.d(LOG, "exchangeCodeForToken(): attempt $attempt")
            val body = FormBody.Builder()
                .add("code",          code)
                .add("client_id",     account.oauthClientId)
                .add("client_secret", account.oauthClientSecret)
                .add("redirect_uri",  "http://127.0.0.1:$port")
                .add("grant_type",    "authorization_code")
                .build()

            val t0 = System.currentTimeMillis()
            AppLogger.request(LOG, "POST", TOKEN_URL)
            val resp     = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
            val elapsed  = System.currentTimeMillis() - t0
            val respBody = resp.body?.use { it.string() } ?: ""
            AppLogger.response(LOG, "POST", TOKEN_URL, resp.code, respBody.take(400), elapsed)

            val json = JSONObject(respBody)
            if (!resp.isSuccessful) {
                val errDesc = json.optString("error_description",
                    json.optString("error", "Token exchange failed (HTTP ${resp.code})"))
                AppLogger.e(LOG, "exchangeCodeForToken(): FAILED — $errDesc")
                throw Exception(errDesc)
            }

            val result = TokenResult(
                json.getString("access_token"),
                json.optString("refresh_token", account.oauthRefreshToken),
                System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)
            )
            AppLogger.i(LOG, "exchangeCodeForToken(): SUCCESS — " +
                    "accessToken len=${result.accessToken.length}, " +
                    "hasRefreshToken=${result.refreshToken.isNotBlank()}, " +
                    "expiresAt=${result.expiryEpoch}")
            result
        }
    }

    private fun refreshTokenFull(account: NamedAccount): Pair<String, Long> {
        AppLogger.i(LOG, "refreshToken(): calling Google token endpoint…")
        return withRetryBlocking(maxAttempts = 2) { attempt ->
            AppLogger.d(LOG, "refreshToken(): attempt $attempt")
            val body = FormBody.Builder()
                .add("client_id",     account.oauthClientId)
                .add("client_secret", account.oauthClientSecret)
                .add("refresh_token", account.oauthRefreshToken)
                .add("grant_type",    "refresh_token")
                .build()
            val t0 = System.currentTimeMillis()
            AppLogger.request(LOG, "POST", TOKEN_URL)
            val resp     = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
            val elapsed  = System.currentTimeMillis() - t0
            val respBody = resp.body?.use { it.string() } ?: ""
            AppLogger.response(LOG, "POST", TOKEN_URL, resp.code, respBody.take(400), elapsed)
            val json = JSONObject(respBody)
            if (!resp.isSuccessful) {
                val err = json.optString("error_description", "Refresh failed (HTTP ${resp.code})")
                AppLogger.e(LOG, "refreshToken(): FAILED — $err")
                throw Exception(err)
            }
            val newToken  = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 3599L)
            val newExpiry = System.currentTimeMillis() / 1000 + expiresIn
            AppLogger.i(LOG, "refreshToken(): new token (len=${newToken.length}, expiry=$newExpiry)")
            newToken to newExpiry
        }
    }

    private fun getJson(url: String, token: String): JSONObject {
        return withRetryBlocking(maxAttempts = 3) { attempt ->
            val t0 = System.currentTimeMillis()
            if (attempt > 1) AppLogger.w(LOG, "getJson(): retry attempt $attempt for ${AppLogger.redactUrl(url)}")
            val req  = Request.Builder()
                .url(url).get()
                .header("Authorization", "Bearer $token")
                .build()
            val resp    = client.newCall(req).execute()
            val elapsed = System.currentTimeMillis() - t0
            val body    = resp.body?.use { it.string() } ?: ""
            AppLogger.response(LOG, "GET", url, resp.code, body.take(300), elapsed)

            if (!resp.isSuccessful) {
                AppLogger.e(LOG, "getJson(): HTTP ${resp.code} error — body: ${body.take(500)}")
                throw Exception("HTTP ${resp.code}: ${body.take(200)}")
            }

            try {
                JSONObject(body)
            } catch (jsonEx: Exception) {
                AppLogger.e(LOG, "getJson(): JSON parse FAILED — raw body: ${body.take(500)}", jsonEx)
                throw jsonEx
            }
        }
    }

    private fun mimeToResourceType(mime: String, ext: String) = when {
        mime.startsWith("audio") || ext in setOf("mp3","wav","ogg","flac","aac","m4a") -> "video"
        mime.startsWith("video") || ext in setOf("mp4","mov","avi","mkv","webm")       -> "video"
        mime.startsWith("image")                                                        -> "image"
        else                                                                            -> "raw"
    }

    fun deleteFile(account: NamedAccount, fileId: String): Boolean {
        AppLogger.i(LOG, "deleteFile(): fileId=$fileId")
        return try {
            val token = freshToken(account)
            if (token == null) {
                AppLogger.e(LOG, "deleteFile(): no token available")
                return false
            }
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .delete()
                .header("Authorization", "Bearer $token")
                .build()
            val t0   = System.currentTimeMillis()
            val resp = client.newCall(req).execute()
            AppLogger.response(LOG, "DELETE", "drive/v3/files/$fileId", resp.code, null, System.currentTimeMillis() - t0)
            resp.isSuccessful.also { ok ->
                if (!ok) AppLogger.e(LOG, "deleteFile(): server returned ${resp.code}")
            }
        } catch (e: Exception) {
            AppLogger.e(LOG, "deleteFile(): EXCEPTION", e)
            false
        }
    }
}
