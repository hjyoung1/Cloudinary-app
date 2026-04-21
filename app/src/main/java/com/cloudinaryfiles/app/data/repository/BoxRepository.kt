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

class BoxRepository {

    private val LOG = "BoxRepo"

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
        AppLogger.i(LOG, "══ fetchAllAssets START ══")
        AppLogger.i(LOG, "  account id       : ${account.id}")
        AppLogger.i(LOG, "  oauthClientId    : ${AppLogger.mask(account.oauthClientId)}")
        AppLogger.i(LOG, "  oauthClientSecret: ${AppLogger.mask(account.oauthClientSecret)}")
        AppLogger.i(LOG, "  oauthAccessToken : ${AppLogger.mask(account.oauthAccessToken)}")

        emit(RepositoryResult.Progress(0, "Connecting to Box…"))
        try {
            val token = account.oauthAccessToken.ifBlank {
                AppLogger.e(LOG, "oauthAccessToken is blank — cannot proceed")
                throw Exception("No access token. Please reconnect.")
            }
            AppLogger.i(LOG, "token len=${token.length}")

            val allAssets = mutableListOf<CloudinaryAsset>()
            var offset = 0; val limit = 1000; var total = Int.MAX_VALUE; var pageNum = 0

            while (offset < total) {
                pageNum++
                val url = "https://api.box.com/2.0/folders/0/items" +
                        "?fields=id,name,type,size,created_at,modified_at&limit=$limit&offset=$offset"
                AppLogger.d(LOG, "  page $pageNum: offset=$offset limit=$limit")
                AppLogger.request(LOG, "GET", url)

                val t0   = System.currentTimeMillis()
                val json = getJson(url, token)
                AppLogger.d(LOG, "  request took ${System.currentTimeMillis() - t0}ms")

                total = json.optInt("total_count", 0)
                val entries = json.optJSONArray("entries")
                AppLogger.i(LOG, "  page $pageNum: total=$total entries=${entries?.length() ?: 0}")

                if (entries == null) { AppLogger.w(LOG, "  entries is null — stopping"); break }

                var skipped = 0
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    if (entry.optString("type") != "file") { skipped++; continue }
                    val id   = entry.getString("id")
                    val name = entry.getString("name")
                    val ext  = name.substringAfterLast(".", "").lowercase()
                    val size = entry.optLong("size", 0L)
                    val created = entry.optString("created_at")
                    val downloadUrl = "https://api.box.com/2.0/files/$id/content"
                    AppLogger.v(LOG, "  file[$i]: id=$id name='$name' ext=$ext size=$size")
                    allAssets += CloudinaryAsset(
                        assetId      = "box:$id",
                        publicId     = id,
                        format       = ext,
                        resourceType = extensionToResourceType(ext),
                        type         = "upload",
                        createdAt    = created,
                        bytes        = size,
                        url          = downloadUrl,
                        secureUrl    = downloadUrl,
                        displayName  = name.substringBeforeLast(".")
                    )
                }
                if (skipped > 0) AppLogger.d(LOG, "  Skipped $skipped folders")
                offset += entries.length()
                if (entries.length() < limit) { AppLogger.d(LOG, "  Last page reached"); break }
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} files…"))
            }

            AppLogger.i(LOG, "══ fetchAllAssets SUCCESS — ${allAssets.size} files ══")
            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            AppLogger.e(LOG, "══ fetchAllAssets FAILED ══", e)
            AppLogger.e(LOG, "  type   : ${e.javaClass.name}")
            AppLogger.e(LOG, "  message: ${e.message}")
            emit(RepositoryResult.Error("Box: ${e.message}"))
        }
    }

    data class TokenResult(val accessToken: String, val refreshToken: String, val expiryEpoch: Long)

    fun exchangeCodeForToken(account: NamedAccount, code: String, port: Int): TokenResult {
        AppLogger.i(LOG, "exchangeCodeForToken(): port=$port code=${code.take(10)}…")
        val body = FormBody.Builder()
            .add("code",          code)
            .add("client_id",     account.oauthClientId)
            .add("client_secret", account.oauthClientSecret)
            .add("redirect_uri",  "http://127.0.0.1:$port")
            .add("grant_type",    "authorization_code")
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
        AppLogger.i(LOG, "exchangeCodeForToken(): SUCCESS (accessLen=${result.accessToken.length})")
        return result
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

    private fun extensionToResourceType(ext: String) = when {
        ext in setOf("mp3","wav","ogg","flac","aac","m4a","opus") -> "video"
        ext in setOf("mp4","mov","avi","mkv","webm")              -> "video"
        ext in setOf("jpg","jpeg","png","gif","webp","avif")      -> "image"
        else -> "raw"
    }
}
