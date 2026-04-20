package com.cloudinaryfiles.app.data.model

import com.google.gson.annotations.SerializedName

data class CloudinaryAsset(
    @SerializedName("asset_id")   val assetId: String = "",
    @SerializedName("public_id")  val publicId: String = "",
    val format: String = "",
    val version: Long = 0L,
    @SerializedName("resource_type") val resourceType: String = "",
    val type: String = "upload",
    @SerializedName("created_at") val createdAt: String = "",
    val bytes: Long = 0L,
    val url: String = "",
    @SerializedName("secure_url") val secureUrl: String = "",
    val tags: List<String> = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val folder: String? = null,
    @SerializedName("display_name") val displayName: String? = null
) {
    val fileName: String
        get() {
            val name = publicId.substringAfterLast("/")
            return if (format.isNotEmpty()) "$name.$format" else name
        }

    val displayTitle: String
        get() = displayName
            ?: publicId.substringAfterLast("/").replace(Regex("[_-]"), " ")

    val sizeInMB: Double
        get() = bytes / (1024.0 * 1024.0)

    val formattedSize: String
        get() = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(sizeInMB)} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }

    val isAudio: Boolean
        get() = format.lowercase() in setOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "opus", "wma")

    val isVideo: Boolean
        get() = !isAudio && format.lowercase() in setOf("mp4", "mov", "avi", "mkv", "webm", "flv", "wmv", "3gp")

    val isImage: Boolean
        get() = format.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "tiff", "avif", "heic")

    val isPdf: Boolean
        get() = format.lowercase() == "pdf"

    val isOther: Boolean
        get() = !isAudio && !isVideo && !isImage && !isPdf

    val fileTypeLabel: String
        get() = when {
            isAudio -> "Audio"
            isVideo -> "Video"
            isImage -> "Image"
            isPdf -> "PDF"
            else -> format.uppercase()
        }

    val thumbnailUrl: String
        get() {
            // For images, request a small thumbnail via Cloudinary transformations
            if (isImage && secureUrl.contains("image/upload")) {
                return secureUrl.replace("image/upload/", "image/upload/w_400,h_240,c_fill,q_70/")
            }
            // For video, get the first frame as image
            if (isVideo && secureUrl.contains("video/upload")) {
                val withoutExt = secureUrl.substringBeforeLast(".")
                return "$withoutExt.jpg".replace("video/upload/", "video/upload/w_400,h_240,c_fill,so_0,q_70/")
            }
            return ""
        }
}

data class CloudinaryResponse(
    val resources: List<CloudinaryAsset> = emptyList(),
    @SerializedName("next_cursor") val nextCursor: String? = null,
    @SerializedName("total_count") val totalCount: Int? = null,
    val error: CloudinaryError? = null
)

data class CloudinaryError(
    val message: String = ""
)

data class CloudinaryCredentials(
    val cloudName: String,
    val apiKey: String,
    val apiSecret: String
)
