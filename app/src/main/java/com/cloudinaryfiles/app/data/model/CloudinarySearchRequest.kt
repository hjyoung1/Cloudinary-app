package com.cloudinaryfiles.app.data.model

import com.google.gson.annotations.SerializedName

data class CloudinarySearchRequest(
    val expression: String,
    @SerializedName("with_field") val withField: List<String> = emptyList(),
    @SerializedName("max_results") val maxResults: Int = 500,
    @SerializedName("next_cursor") val nextCursor: String? = null
)
