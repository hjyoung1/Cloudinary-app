package com.cloudinaryfiles.app.data.repository

import android.util.Base64
import com.cloudinaryfiles.app.AppLogger
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

    private val LOG = "WebDavRepo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun fetchAllAssets(account: NamedAccount): Flow<RepositoryResult> = flow {
        AppLogger.i(LOG, "══ fetchAllAssets START ══")
        AppLogger.i(LOG, "  account id  : ${account.id}")
        AppLogger.i(LOG, "  webDavUrl   : '${account.webDavUrl}'")
        AppLogger.i(LOG, "  webDavUser  : '${account.webDavUser}'")
        AppLogger.i(LOG, "  webDavPass  : ${AppLogger.mask(account.webDavPass)}")

        emit(RepositoryResult.Progress(0, "Connecting to ${account.name}…"))
        try {
            val baseUrl = account.webDavUrl.trimEnd('/')
            if (baseUrl.isBlank()) {
                AppLogger.e(LOG, "webDavUrl is blank — cannot connect")
                throw Exception("WebDAV URL is not configured")
            }

            val auth = if (account.webDavUser.isNotBlank()) {
                val creds = "${account.webDavUser}:${account.webDavPass}"
                val encoded = Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)
                AppLogger.d(LOG, "  Using Basic Auth for user '${account.webDavUser}'")
                "Basic $encoded"
            } else {
                AppLogger.d(LOG, "  No auth — anonymous WebDAV")
                null
            }

            AppLogger.d(LOG, "  Sending PROPFIND to $baseUrl…")
            val entries = withContext(Dispatchers.IO) { propfind(baseUrl, auth) }
            AppLogger.i(LOG, "  PROPFIND returned ${entries.size} entries total")

            val files  = entries.filter { !it.isDir }
            val folders = entries.count { it.isDir }
            AppLogger.i(LOG, "  Files: ${files.size}, Folders: $folders")

            val allAssets = files.mapIndexed { i, e ->
                val ext = e.name.substringAfterLast(".", "").lowercase()
                val streamUrl = buildStreamUrl(baseUrl, e.href, account)
                AppLogger.v(LOG, "  file[$i]: name='${e.name}' href=${e.href} size=${e.size} mime=${e.contentType}")
                CloudinaryAsset(
                    assetId      = "webdav:${e.href}",
                    publicId     = e.href.trimStart('/'),
                    format       = ext,
                    resourceType = contentTypeToResourceType(e.contentType, ext),
                    type         = "upload",
                    createdAt    = e.lastModified,
                    bytes        = e.size,
                    url          = streamUrl,
                    secureUrl    = streamUrl,
                    displayName  = e.name.substringBeforeLast(".")
                )
            }

            AppLogger.i(LOG, "══ fetchAllAssets SUCCESS — ${allAssets.size} files ══")
            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            AppLogger.e(LOG, "══ fetchAllAssets FAILED ══", e)
            AppLogger.e(LOG, "  type   : ${e.javaClass.name}")
            AppLogger.e(LOG, "  message: ${e.message}")
            when {
                e.message?.contains("UnknownHost", true) == true ->
                    AppLogger.e(LOG, "  ⚠ DNS failure — check webDavUrl: '${account.webDavUrl}'")
                e.message?.contains("401", true) == true ->
                    AppLogger.e(LOG, "  ⚠ 401 Unauthorized — check username/password")
                e.message?.contains("403", true) == true ->
                    AppLogger.e(LOG, "  ⚠ 403 Forbidden — user lacks permission to list this path")
                e.message?.contains("404", true) == true ->
                    AppLogger.e(LOG, "  ⚠ 404 — WebDAV path not found: '${account.webDavUrl}'")
                e.message?.contains("timeout", true) == true ->
                    AppLogger.e(LOG, "  ⚠ Timeout — server is slow or unreachable")
            }
            emit(RepositoryResult.Error("WebDAV: ${e.message}"))
        }
    }

    private fun buildStreamUrl(base: String, href: String, account: NamedAccount): String {
        val url = if (href.startsWith("http")) href else "$base$href"
        return if (account.webDavUser.isNotBlank()) {
            try {
                val u    = URI(url)
                val auth = "${account.webDavUser}:${account.webDavPass}"
                "${u.scheme}://$auth@${u.host}${if (u.port != -1) ":${u.port}" else ""}${u.path}"
            } catch (e: Exception) {
                AppLogger.w(LOG, "buildStreamUrl(): URI parse failed for '$url' — using as-is")
                url
            }
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

        AppLogger.request(LOG, "PROPFIND", url)
        val t0   = System.currentTimeMillis()
        val resp = client.newCall(reqBuilder.build()).execute()
        val elapsed = System.currentTimeMillis() - t0
        AppLogger.response(LOG, "PROPFIND", url, resp.code, null, elapsed)

        if (!resp.isSuccessful) {
            val errBody = resp.body?.use { it.string() } ?: ""
            AppLogger.e(LOG, "PROPFIND failed: HTTP ${resp.code} ${resp.message}")
            AppLogger.e(LOG, "  Response body: ${errBody.take(400)}")
            throw Exception("HTTP ${resp.code}: ${resp.message}")
        }

        val xml = resp.body?.use { it.string() } ?: ""
        AppLogger.d(LOG, "PROPFIND response: ${xml.length} chars")
        return parseMultiStatus(xml)
    }

    private fun parseMultiStatus(xml: String): List<DavEntry> {
        val entries = mutableListOf<DavEntry>()
        try {
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
                            "displayname"       -> displayName = text
                            "getcontentlength"  -> contentLength = text.toLongOrNull() ?: 0L
                            "getcontenttype"    -> contentType = text
                            "getlastmodified"   -> lastModified = text
                            "href"              -> if (href.isBlank()) href = text
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
            AppLogger.d(LOG, "parseMultiStatus(): parsed ${entries.size} entries")
        } catch (e: Exception) {
            AppLogger.e(LOG, "parseMultiStatus(): XML parse error", e)
            AppLogger.e(LOG, "  Raw XML snippet: ${xml.take(500)}")
            throw e
        }
        return entries
    }

    private fun parseHttpDate(raw: String): String = try {
        ZonedDateTime.parse(raw, DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH))
            .toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    } catch (_: Exception) { raw }

    private fun contentTypeToResourceType(mime: String, ext: String) = when {
        mime.startsWith("audio") || ext in setOf("mp3","wav","ogg","flac","aac","m4a") -> "video"
        mime.startsWith("video") || ext in setOf("mp4","mov","avi","mkv","webm")       -> "video"
        mime.startsWith("image") -> "image"
        else -> "raw"
    }
}
