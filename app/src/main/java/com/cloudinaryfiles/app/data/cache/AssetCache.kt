package com.cloudinaryfiles.app.data.cache

import android.content.Context
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class AssetCache(private val context: Context) {
    private val gson = Gson()

    private fun cacheFile(key: String): File {
        val safe = key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(context.filesDir, "asset_cache_$safe.json")
    }

    fun save(key: String, assets: List<CloudinaryAsset>) {
        try {
            cacheFile(key).writeText(gson.toJson(assets))
        } catch (_: Exception) {}
    }

    fun load(key: String): List<CloudinaryAsset>? {
        return try {
            val file = cacheFile(key)
            if (!file.exists()) return null
            val type = object : TypeToken<List<CloudinaryAsset>>() {}.type
            gson.fromJson(file.readText(), type)
        } catch (_: Exception) { null }
    }

    fun clear(key: String) {
        try { cacheFile(key).delete() } catch (_: Exception) {}
    }
}
