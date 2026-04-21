package com.cloudinaryfiles.app.data.repository

import com.cloudinaryfiles.app.AppLogger
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

    private val LOG = "OneDriveRepo"

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
        AppLogger.i(LOG, "══ fetchAllAssets START ══")
        AppLogger.i(LOG, "  account id       : ${account.id}")
        AppLogger.i(LOG, "  oauthClientId    : ${AppLogger.mask(account.oauthClientId)}")
        AppLogger.i(LOG, "  oauthAccessToken : ${AppLogger.mask(account.oauthAccessToken)}")
        AppLogger.i(LOG, "  oauthRefreshToken: ${AppLogger.mask(account.oauthRefreshToken)}")

        emit(RepositoryResult.Progress(0, "Connecting to OneDrive…"))
        try {
            val token = freshToken(account) ?: run {
                AppLogger.e(LOG, "freshToken() returned null")
                throw Exception("No access token. Please reconnect.")
            }
            AppLogger.i(LOG, "freshToken() OK (len=${token.length})")

            val allAssets = mutableListOf<CloudinaryAsset>()
            var nextUrl: String? = "https://graph.microsoft.com/v1.0/me/drive/root/search(q='')" +
                    "?\$select=id,name,size,createdDateTime,file,@microsoft.graph.downloadUrl&\$top=1000"
            var pageNum = 0

            while (nextUrl != null) {
                pageNum++
                AppLogger.d(LOG, "  Graph API page $pageNum: ${nextUrl.take(100)}")
                AppLogger.request(LOG, "GET", nextUrl)

                val t0   = System.currentTimeMillis()
                val json = getJson(nextUrl, token)
                AppLogger.d(LOG, "  Graph request took ${System.currentTimeMillis() - t0}ms")

                val items = json.optJSONArray("value")
                AppLogger.i(LOG, "  page $pageNum: ${items?.length() ?: 0} items")

                if (items == null) break
                var skipped = 0
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    if (!item.has("file")) { skipped++; continue }
                    val id   = item.getString("id")
                    val name = item.getString("name")
                    val ext  = name.substringAfterLast(".", "").lowercase()
                    val size = item.optLong("size", 0L)
                    val created = item.optString("createdDateTime")
                    val mime = item.optJSONObject("file")?.optString("mimeType") ?: ""
                    val downloadUrl = item.optString("@microsoft.graph.downloadUrl").ifEmpty {
                        AppLogger.w(LOG, "  ⚠ No @microsoft.graph.downloadUrl for $id — falling back to API URL")
                        "https://graph.microsoft.com/v1.0/me/drive/items/$id/content"
                    }
                    AppLogger.v(LOG, "  item[$i]: name='$name' ext=$ext size=$size mime=$mime")
                    allAssets += CloudinaryAsset(
                        assetId      = "onedrive:$id",
                        publicId     = id,
                        format       = ext,
                        resourceType = mimeToResourceType(mime, ext),
                        type         = "upload",
                        createdAt    = created,
                        bytes        = size,
                        url          = downloadUrl,
                        secureUrl    = downloadUrl,
                        displayName  = name.substringBeforeLast(".")
                    )
                }
                if (skipped > 0) AppLogger.d(LOG, "  Skipped $skipped folders/non-files")

                nextUrl = json.optString("@odata.nextLink").ifEmpty { null }
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} files…"))
            }

            AppLogger.i(LOG, "══ fetchAllAssets SUCCESS — ${allAssets.size} files ══")
            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            AppLogger.e(LOG, "══ fetchAllAssets FAILED ══", e)
            AppLogger.e(LOG, "  type   : ${e.javaClass.name}")
            AppLogger.e(LOG, "  message: ${e.message}")
            when {
                e.message?.contains("401", true) == true ->
                    AppLogger.e(LOG, "  ⚠ 401 — token expired or revoked. Re-authenticate.")
                e.message?.contains("UnknownHost", true) == true ->
                    AppLogger.e(LOG, "  ⚠ DNS failure — no network or graph.microsoft.com blocked")
            }
            emit(RepositoryResult.Error("OneDrive: ${e.message}"))
        }
    }

    fun freshToken(account: NamedAccount): String? {
        AppLogger.d(LOG, "freshToken(): checking…")
        if (account.oauthAccessToken.isBlank()) {
            AppLogger.w(LOG, "freshToken(): blank → null")
            return null
        }
        val expiry = account.oauthTokenExpiry; val now = System.currentTimeMillis() / 1000
        AppLogger.d(LOG, "freshToken(): expiry=$expiry now=$now diff=${expiry - now}s")
        if (expiry == 0L || now < expiry - 60) {
            AppLogger.d(LOG, "freshToken(): token valid")
            return account.oauthAccessToken
        }
        AppLogger.i(LOG, "freshToken(): token expired — refreshing…")
        if (account.oauthRefreshToken.isBlank()) {
            AppLogger.w(LOG, "freshToken(): no refresh token → using expired token")
            return account.oauthAccessToken
        }
        return try {
            val t = refreshToken(account)
            AppLogger.i(LOG, "freshToken(): refreshed OK")
            t
        } catch (e: Exception) {
            AppLogger.e(LOG, "freshToken(): refresh failed → using old token", e)
            account.oauthAccessToken
        }
    }

    data class TokenResult(val accessToken: String, val refreshToken: String, val expiryEpoch: Long)

    fun exchangeCodeForToken(account: NamedAccount, code: String, port: Int): TokenResult {
        AppLogger.i(LOG, "exchangeCodeForToken(): port=$port code=${code.take(10)}…")
        val body = FormBody.Builder()
            .add("code",         code)
            .add("client_id",    account.oauthClientId)
            .add("redirect_uri", "http://127.0.0.1:$port")
            .add("grant_type",   "authorization_code")
            .add("scope",        SCOPES)
            .apply { if (account.oauthClientSecret.isNotBlank()) add("client_secret", account.oauthClientSecret) }
            .build()
        AppLogger.request(LOG, "POST", TOKEN_URL)
        val t0   = System.currentTimeMillis()
        val resp = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
        val rawBody = resp.body?.use { it.string() } ?: ""
        AppLogger.response(LOG, "POST", TOKEN_URL, resp.code, rawBody.take(300), System.currentTimeMillis() - t0)
        val json = JSONObject(rawBody)
        if (!resp.isSuccessful) {
            val err = json.optString("error_description", "Token exchange failed (HTTP ${resp.code})")
            AppLogger.e(LOG, "exchangeCodeForToken(): FAILED — $err")
            throw Exception(err)
        }
        val result = TokenResult(
            json.getString("access_token"),
            json.optString("refresh_token", ""),
            System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)
        )
        AppLogger.i(LOG, "exchangeCodeForToken(): SUCCESS — accessLen=${result.accessToken.length} hasRefresh=${result.refreshToken.isNotBlank()}")
        return result
    }

    private fun refreshToken(account: NamedAccount): String {
        AppLogger.i(LOG, "refreshToken(): calling $TOKEN_URL")
        val body = FormBody.Builder()
            .add("client_id",     account.oauthClientId)
            .add("refresh_token", account.oauthRefreshToken)
            .add("grant_type",    "refresh_token")
            .add("scope",         SCOPES)
            .apply { if (account.oauthClientSecret.isNotBlank()) add("client_secret", account.oauthClientSecret) }
            .build()
        val t0   = System.currentTimeMillis()
        val resp = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
        val rawBody = resp.body?.use { it.string() } ?: ""
        AppLogger.response(LOG, "POST (refresh)", TOKEN_URL, resp.code, rawBody.take(200), System.currentTimeMillis() - t0)
        val json = JSONObject(rawBody)
        if (!resp.isSuccessful) {
            AppLogger.e(LOG, "refreshToken(): FAILED — ${json.optString("error_description")}")
        }
        return json.optString("access_token").ifEmpty { account.oauthAccessToken }
    }

    private fun getJson(url: String, token: String): JSONObject {
        val req  = Request.Builder().url(url).get().header("Authorization", "Bearer $token").build()
        val t0   = System.currentTimeMillis()
        val resp = client.newCall(req).execute()
        val body = resp.body?.use { it.string() } ?: ""
        AppLogger.response(LOG, "GET", url, resp.code, body.take(200), System.currentTimeMillis() - t0)
        if (!resp.isSuccessful) {
            AppLogger.e(LOG, "getJson() error body: $body")
            throw Exception("HTTP ${resp.code}: ${body.take(200)}")
        }
        return JSONObject(body)
    }

    private fun mimeToResourceType(mime: String, ext: String) = when {
        mime.startsWith("audio") || ext in setOf("mp3","wav","ogg","flac","aac","m4a") -> "video"
        mime.startsWith("video") || ext in setOf("mp4","mov","avi","mkv","webm")       -> "video"
        mime.startsWith("image") -> "image"
        else -> "raw"
    }
}
