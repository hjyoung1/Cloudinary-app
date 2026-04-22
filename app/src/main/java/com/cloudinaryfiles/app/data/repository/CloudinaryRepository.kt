package com.cloudinaryfiles.app.data.repository

import android.util.Base64
import com.cloudinaryfiles.app.AppLogger
import com.cloudinaryfiles.app.data.api.CloudinaryApi
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.model.CloudinaryCredentials
import com.cloudinaryfiles.app.data.model.CloudinarySearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

sealed class RepositoryResult {
    data class Progress(val loaded: Int, val message: String) : RepositoryResult()
    data class Success(val assets: List<CloudinaryAsset>)     : RepositoryResult()
    data class Error(val message: String)                      : RepositoryResult()
}

class CloudinaryRepository {

    private val LOG = "CloudinaryRepo"

    private fun buildApi(credentials: CloudinaryCredentials): CloudinaryApi {
        AppLogger.d(LOG, "buildApi(): cloudName='${credentials.cloudName}', apiKey=${AppLogger.mask(credentials.apiKey)}")

        val token = Base64.encodeToString(
            "${credentials.apiKey}:${credentials.apiSecret}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        val loggingInterceptor = HttpLoggingInterceptor { message ->
            AppLogger.v(LOG, "[OkHttp] $message")
        }.apply { level = HttpLoggingInterceptor.Level.BASIC }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Basic $token")
                    .build()
                AppLogger.d(LOG, "→ ${req.method} ${AppLogger.redactUrl(req.url.toString())}")
                val t0   = System.currentTimeMillis()
                val resp = chain.proceed(req)
                val ms   = System.currentTimeMillis() - t0
                AppLogger.d(LOG, "← HTTP ${resp.code} [${ms}ms]")
                resp
            }
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.cloudinary.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudinaryApi::class.java)
    }

    fun fetchAllAssets(credentials: CloudinaryCredentials): Flow<RepositoryResult> = flow {
        AppLogger.i(LOG, "══ fetchAllAssets START ══════════════════════════════════")
        AppLogger.i(LOG, "  cloudName : '${credentials.cloudName}'")
        AppLogger.i(LOG, "  apiKey    : ${AppLogger.mask(credentials.apiKey)}")
        AppLogger.i(LOG, "  apiSecret : ${AppLogger.mask(credentials.apiSecret)}")

        val api = buildApi(credentials)
        val all = mutableListOf<CloudinaryAsset>()

        // ── Video / Audio ────────────────────────────────────────────────────
        AppLogger.i(LOG, "── Phase 1: Video/Audio resources ──")
        emit(RepositoryResult.Progress(0, "Loading audio & video…"))
        var cursor: String? = null
        var page = 0

        do {
            page++
            AppLogger.d(LOG, "  Video page $page — cursor=${cursor?.take(15)?.plus("…") ?: "null"}")

            // NOTE: 'duration' is NOT a valid with_field for the Cloudinary Search API.
            // Supported options: context, tags, image_metadata, image_analysis,
            //                    metadata, quality_analysis, accessibility_analysis
            // Using 'duration' causes API 400. We remove it here and get duration
            // from the asset's native 'duration' field that comes in the search response.
            val req = CloudinarySearchRequest(
                expression  = "resource_type:video",
                withField   = emptyList(),    // ← 'duration' was here and caused the 400 error!
                nextCursor  = cursor
            )

            AppLogger.d(LOG, "  Search request: expression='${req.expression}', withField=${req.withField}, nextCursor=${req.nextCursor?.take(15)}")

            val resp = try {
                api.searchResources(cloudName = credentials.cloudName, request = req)
            } catch (e: Exception) {
                AppLogger.e(LOG, "  searchResources() EXCEPTION (page $page)", e)
                emit(RepositoryResult.Error("Network error: ${e.message}"))
                return@flow
            }

            AppLogger.d(LOG, "  searchResources() response: HTTP ${resp.code()}")
            if (!resp.isSuccessful) {
                val errBody = resp.errorBody()?.string() ?: ""
                AppLogger.e(LOG, "  searchResources() FAILED — HTTP ${resp.code()}")
                AppLogger.e(LOG, "  Error body: $errBody")

                // Diagnose the specific error we saw in the screenshot
                when {
                    errBody.contains("with_field", true) ->
                        AppLogger.e(LOG, "  ⚠ Invalid 'with_field' option detected! Check CloudinarySearchRequest.withField list.")
                    errBody.contains("duration", true) ->
                        AppLogger.e(LOG, "  ⚠ 'duration' is NOT a valid with_field option for Cloudinary Search API." +
                                " Remove it from withField. Duration is returned natively in search results.")
                    resp.code() == 400 ->
                        AppLogger.e(LOG, "  ⚠ 400 Bad Request — check search expression and parameters")
                    resp.code() == 401 ->
                        AppLogger.e(LOG, "  ⚠ 401 Unauthorized — check API key / secret")
                    resp.code() == 404 ->
                        AppLogger.e(LOG, "  ⚠ 404 — check cloud name: '${credentials.cloudName}'")
                }
                emit(RepositoryResult.Error("API ${resp.code()}: ${errBody.take(200)}"))
                return@flow
            }

            val body = resp.body()
            if (body == null) {
                AppLogger.w(LOG, "  searchResources() body is null — stopping")
                break
            }
            AppLogger.i(LOG, "  Video page $page: ${body.resources.size} assets, nextCursor=${body.nextCursor?.take(15)?.plus("…") ?: "null"}")
            body.resources.forEachIndexed { i, a ->
                AppLogger.v(LOG, "    [$i] ${a.publicId} (${a.format}) ${a.bytes}B dur=${a.duration}s")
            }

            all.addAll(body.resources)
            cursor = body.nextCursor
            page++
            emit(RepositoryResult.Progress(all.size, "Loaded ${all.size} assets…"))
        } while (cursor != null)

        AppLogger.i(LOG, "  Video/audio phase done — ${all.size} assets so far")

        // ── Images ───────────────────────────────────────────────────────────
        AppLogger.i(LOG, "── Phase 2: Image resources ──")
        emit(RepositoryResult.Progress(all.size, "Loading images…"))
        cursor = null
        var imgPage = 0
        do {
            imgPage++
            AppLogger.d(LOG, "  Image page $imgPage — cursor=${cursor?.take(15)?.plus("…") ?: "null"}")
            val resp = try {
                api.getImageResources(cloudName = credentials.cloudName, nextCursor = cursor)
            } catch (e: Exception) {
                AppLogger.w(LOG, "  getImageResources() EXCEPTION (non-fatal, skipping images)", e)
                break
            }
            AppLogger.d(LOG, "  getImageResources() → HTTP ${resp.code()}")
            if (!resp.isSuccessful) {
                AppLogger.w(LOG, "  getImageResources() error ${resp.code()} — skipping: ${resp.errorBody()?.string()?.take(200)}")
                break
            }
            val body = resp.body() ?: break
            AppLogger.i(LOG, "  Image page $imgPage: ${body.resources.size} assets")
            all.addAll(body.resources)
            cursor = body.nextCursor
            emit(RepositoryResult.Progress(all.size, "Loaded ${all.size} assets…"))
        } while (cursor != null)

        AppLogger.i(LOG, "  Images phase done — ${all.size} assets so far")

        // ── Raw files (PDFs etc.) ─────────────────────────────────────────────
        AppLogger.i(LOG, "── Phase 3: Raw resources ──")
        emit(RepositoryResult.Progress(all.size, "Loading raw files…"))
        cursor = null
        var rawPage = 0
        do {
            rawPage++
            AppLogger.d(LOG, "  Raw page $rawPage — cursor=${cursor?.take(15)?.plus("…") ?: "null"}")
            val resp = try {
                api.getRawResources(cloudName = credentials.cloudName, nextCursor = cursor)
            } catch (e: Exception) {
                AppLogger.w(LOG, "  getRawResources() EXCEPTION (non-fatal, skipping)", e)
                break
            }
            AppLogger.d(LOG, "  getRawResources() → HTTP ${resp.code()}")
            if (!resp.isSuccessful) {
                AppLogger.w(LOG, "  getRawResources() error ${resp.code()} — skipping")
                break
            }
            val body = resp.body() ?: break
            AppLogger.i(LOG, "  Raw page $rawPage: ${body.resources.size} assets")
            all.addAll(body.resources)
            cursor = body.nextCursor
            emit(RepositoryResult.Progress(all.size, "Loaded ${all.size} assets…"))
        } while (cursor != null)

        AppLogger.i(LOG, "══ fetchAllAssets SUCCESS — ${all.size} total assets ══")
        emit(RepositoryResult.Success(all))
    }
    .flowOn(Dispatchers.IO)
    suspend fun deleteAssets(credentials: CloudinaryCredentials, assets: List<CloudinaryAsset>): Boolean {
        AppLogger.i(LOG, "deleteAssets(): ${assets.size} assets to delete")
        val api = buildApi(credentials)
        val byType = assets.groupBy { it.resourceType.ifBlank { "image" } }
        AppLogger.d(LOG, "  by type: ${byType.map { "${it.key}=${it.value.size}" }}")
        var success = true
        for ((type, list) in byType) {
            val publicIds = list.map { it.publicId }
            AppLogger.d(LOG, "  deleting $type: $publicIds")
            try {
                val req  = mapOf("public_ids" to publicIds)
                val resp = api.deleteResources(credentials.cloudName, type, req)
                AppLogger.d(LOG, "  deleteResources($type) → HTTP ${resp.code()}")
                if (!resp.isSuccessful) {
                    AppLogger.e(LOG, "  deleteResources($type) FAILED: ${resp.errorBody()?.string()?.take(200)}")
                    success = false
                }
            } catch (e: Exception) {
                AppLogger.e(LOG, "  deleteResources($type) EXCEPTION", e)
                success = false
            }
        }
        AppLogger.i(LOG, "deleteAssets() → success=$success")
        return success
    }
}
