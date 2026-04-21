package com.cloudinaryfiles.app.data.repository

import android.util.Base64
import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.preferences.NamedAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Direct WebDAV: Nextcloud, OwnCloud, pCloud, Yandex Disk, Koofr, generic WebDAV. */
class WebDavDirectRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun fetchAllAssets(account: NamedAccount): Flow<RepositoryResult> = flow {
        emit(RepositoryResult.Progress(0, "Connecting to ${account.name}…"))
        try {
            val baseUrl = account.webDavUrl.trimEnd('/')
            val auth = if (account.webDavUser.isNotBlank())
                "Basic " + Base64.encodeToString(
                    "${account.webDavUser}:${account.webDavPass}".toByteArray(), Base64.NO_WRAP)
            else null
            val entries = withContext(Dispatchers.IO) { propfind(baseUrl, auth) }
            val allAssets = entries.filter { !it.isDir }.map { e ->
                val ext = e.name.substringAfterLast(".", "").lowercase()
                val streamUrl = buildStreamUrl(baseUrl, e.href, account)
                CloudinaryAsset(
                    assetId   = "webdav:${e.href}",
                    publicId  = e.href.trimStart('/'),
                    format    = ext,
                    resourceType = contentTypeToResourceType(e.contentType, ext),
                    type      = "upload",
                    createdAt = e.lastModified,
                    bytes     = e.size,
                    url       = streamUrl,
                    secureUrl = streamUrl,
                    displayName = e.name.substringBeforeLast(".")
                )
            }
            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            emit(RepositoryResult.Error("WebDAV: ${e.message}"))
        }
    }

    private fun buildStreamUrl(base: String, href: String, account: NamedAccount): String {
        val url = if (href.startsWith("http")) href else "$base$href"
        return if (account.webDavUser.isNotBlank()) {
            try {
                val u = URI(url)
                val auth = "${account.webDavUser}:${account.webDavPass}"
                "${u.scheme}://$auth@${u.host}${if (u.port != -1) ":${u.port}" else ""}${u.path}"
            } catch (_: Exception) { url }
        } else url
    }

    private data class DavEntry(
        val href: String, val name: String, val isDir: Boolean,
        val size: Long, val lastModified: String, val contentType: String
    )

    private fun propfind(url: String, auth: String?): List<DavEntry> {
        val body = """<?xml version="1.0"?><D:propfind xmlns:D="DAV:"><D:allprop/></D:propfind>"""
        val reqBuilder = Request.Builder().url(url)
            .method("PROPFIND", body.toRequestBody())
            .header("Depth", "infinity")
            .header("Content-Type", "application/xml")
        auth?.let { reqBuilder.header("Authorization", it) }
        val resp = client.newCall(reqBuilder.build()).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
        return parseMultiStatus(resp.body?.use { it.string() } ?: "")
    }

    private fun parseMultiStatus(xml: String): List<DavEntry> {
        val entries = mutableListOf<DavEntry>()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(xml))
        var tag = ""; var href = ""; var displayName = ""; var contentLength = 0L
        var contentType = ""; var lastModified = ""; var isCollection = false
        var inResponse = false; var inProp = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val localName = parser.name?.substringAfterLast(':') ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = localName
                    when (localName) {
                        "response"   -> { inResponse = true; href=""; displayName=""; contentLength=0L; contentType=""; lastModified=""; isCollection=false }
                        "prop"       -> inProp = true
                        "collection" -> if (inProp) isCollection = true
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (!inProp && inResponse && tag == "href") href = text
                    if (inProp && inResponse) when (tag) {
                        "displayname"      -> displayName = text
                        "getcontentlength" -> contentLength = text.toLongOrNull() ?: 0L
                        "getcontenttype"   -> contentType = text
                        "getlastmodified"  -> lastModified = text
                        "href"             -> if (href.isBlank()) href = text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (localName) {
                        "prop"     -> inProp = false
                        "response" -> {
                            if (inResponse && href.isNotBlank()) {
                                val decoded = try { URI(href).path } catch (_: Exception) { href }
                                val name = decoded.trimEnd('/').substringAfterLast('/').ifBlank { displayName }
                                if (name.isNotBlank())
                                    entries += DavEntry(decoded, if (displayName.isNotBlank()) displayName else name,
                                        isCollection, contentLength, parseHttpDate(lastModified), contentType)
                            }
                            inResponse = false
                        }
                    }
                    tag = ""
                }
            }
            event = parser.next()
        }
        return entries
    }

    private fun parseHttpDate(raw: String): String = try {
        ZonedDateTime.parse(raw, DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH))
            .toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    } catch (_: Exception) { raw }

    private fun contentTypeToResourceType(mime: String, ext: String) = when {
        mime.startsWith("audio") || ext in setOf("mp3","wav","ogg","flac","aac","m4a") -> "video"
        mime.startsWith("video") || ext in setOf("mp4","mov","avi","mkv","webm") -> "video"
        mime.startsWith("image") -> "image"
        else -> "raw"
    }
}
