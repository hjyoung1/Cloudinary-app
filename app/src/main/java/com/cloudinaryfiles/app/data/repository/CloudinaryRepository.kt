package com.cloudinaryfiles.app.data.repository

import android.util.Base64
import com.cloudinaryfiles.app.data.api.CloudinaryApi
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.model.CloudinaryCredentials
import com.cloudinaryfiles.app.data.model.CloudinarySearchRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

sealed class RepositoryResult {
    data class Progress(val loaded: Int, val message: String) : RepositoryResult()
    data class Success(val assets: List<CloudinaryAsset>) : RepositoryResult()
    data class Error(val message: String) : RepositoryResult()
}

class CloudinaryRepository {

    private fun buildApi(credentials: CloudinaryCredentials): CloudinaryApi {
        val token = Base64.encodeToString(
            "${credentials.apiKey}:${credentials.apiSecret}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Basic $token")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.cloudinary.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudinaryApi::class.java)
    }

    fun fetchAllAssets(credentials: CloudinaryCredentials): Flow<RepositoryResult> = flow {
        val api = buildApi(credentials)
        val all = mutableListOf<CloudinaryAsset>()

        // ── Video/Audio resources (MP3s live here) ──
        emit(RepositoryResult.Progress(0, "Loading audio & video…"))
        var cursor: String? = null
        var page = 0
        do {
            val resp = try {
                val req = CloudinarySearchRequest(
                    expression = "resource_type:video",
                    withField = listOf("duration"),
                    nextCursor = cursor
                )
                api.searchResources(cloudName = credentials.cloudName, request = req)
            } catch (e: Exception) {
                emit(RepositoryResult.Error("Network error: ${e.message}"))
                return@flow
            }

            if (!resp.isSuccessful) {
                val errBody = resp.errorBody()?.string() ?: ""
                emit(RepositoryResult.Error("API ${resp.code()}: ${errBody.take(200)}"))
                return@flow
            }

            val body = resp.body() ?: break
            all.addAll(body.resources)
            cursor = body.nextCursor
            page++
            emit(RepositoryResult.Progress(all.size, "Loaded ${all.size} assets…"))
        } while (cursor != null)

        // ── Image resources ──
        emit(RepositoryResult.Progress(all.size, "Loading images…"))
        cursor = null
        do {
            val resp = try {
                api.getImageResources(cloudName = credentials.cloudName, nextCursor = cursor)
            } catch (e: Exception) {
                // Non-fatal: skip images if error
                break
            }
            if (!resp.isSuccessful) break
            val body = resp.body() ?: break
            all.addAll(body.resources)
            cursor = body.nextCursor
            emit(RepositoryResult.Progress(all.size, "Loaded ${all.size} assets…"))
        } while (cursor != null)

        // ── Raw resources (PDFs, etc.) ──
        emit(RepositoryResult.Progress(all.size, "Loading raw files…"))
        cursor = null
        do {
            val resp = try {
                api.getRawResources(cloudName = credentials.cloudName, nextCursor = cursor)
            } catch (e: Exception) {
                break
            }
            if (!resp.isSuccessful) break
            val body = resp.body() ?: break
            all.addAll(body.resources)
            cursor = body.nextCursor
            emit(RepositoryResult.Progress(all.size, "Loaded ${all.size} assets…"))
        } while (cursor != null)

        emit(RepositoryResult.Success(all))
    }

    suspend fun deleteAssets(credentials: CloudinaryCredentials, assets: List<CloudinaryAsset>): Boolean {
        val api = buildApi(credentials)
        // Cloudinary requires deleting by resource_type
        val byType = assets.groupBy { it.resourceType.ifBlank { "image" } }
        var success = true
        for ((type, list) in byType) {
            val publicIds = list.map { it.publicId }
            try {
                val req = mapOf("public_ids" to publicIds)
                val resp = api.deleteResources(credentials.cloudName, type, req)
                if (!resp.isSuccessful) success = false
            } catch (e: Exception) {
                success = false
            }
        }
        return success
    }
}
