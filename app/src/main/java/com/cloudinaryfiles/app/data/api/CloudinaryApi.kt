package com.cloudinaryfiles.app.data.api

import com.cloudinaryfiles.app.data.model.CloudinaryResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudinaryApi {

    @GET("v1_1/{cloudName}/resources/video")
    suspend fun getVideoResources(
        @Path("cloudName") cloudName: String,
        @Query("max_results") maxResults: Int = 500,
        @Query("next_cursor") nextCursor: String? = null,
        @Query("type") type: String = "upload",
        @Query("tags") tags: Boolean = true,
        @Query("context") context: Boolean = true,
        @Query("image_metadata") imageMetadata: Boolean = true
    ): Response<CloudinaryResponse>

    @GET("v1_1/{cloudName}/resources/image")
    suspend fun getImageResources(
        @Path("cloudName") cloudName: String,
        @Query("max_results") maxResults: Int = 500,
        @Query("next_cursor") nextCursor: String? = null,
        @Query("type") type: String = "upload",
        @Query("tags") tags: Boolean = true,
        @Query("context") context: Boolean = true
    ): Response<CloudinaryResponse>

    @GET("v1_1/{cloudName}/resources/raw")
    suspend fun getRawResources(
        @Path("cloudName") cloudName: String,
        @Query("max_results") maxResults: Int = 500,
        @Query("next_cursor") nextCursor: String? = null,
        @Query("type") type: String = "upload"
    ): Response<CloudinaryResponse>
}
