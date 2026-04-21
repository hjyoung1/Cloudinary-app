package com.cloudinaryfiles.app.data.repository

import com.cloudinaryfiles.app.data.model.CloudinaryAsset
import com.cloudinaryfiles.app.data.preferences.NamedAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Native S3 / S3-compatible repository using AWS Signature V4.
 * Works with: AWS S3, Wasabi, Cloudflare R2, MinIO, Backblaze B2 (S3 mode),
 *             DigitalOcean Spaces, Linode Object Storage, Scaleway, Storj, etc.
 */
class S3Repository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ─── Public API ─────────────────────────────────────────────────────────

    fun fetchAllAssets(account: NamedAccount): Flow<RepositoryResult> = flow {
        emit(RepositoryResult.Progress(0, "Connecting to ${account.name}…"))
        try {
            val allAssets = mutableListOf<CloudinaryAsset>()
            var continuationToken: String? = null
            val baseUrl = resolveBaseUrl(account)

            do {
                val xml = withContext(Dispatchers.IO) {
                    listObjects(account, baseUrl, continuationToken)
                }
                val (objects, nextToken) = parseListObjectsV2(xml)
                allAssets += objects.map { obj ->
                    assetFromS3Object(obj, account, baseUrl)
                }
                continuationToken = nextToken
                emit(RepositoryResult.Progress(allAssets.size, "Loaded ${allAssets.size} objects…"))
            } while (continuationToken != null)

            emit(RepositoryResult.Success(allAssets))
        } catch (e: Exception) {
            emit(RepositoryResult.Error("S3 error: ${e.message}"))
        }
    }

    /** Generate a presigned GET URL valid for 1 hour */
    fun presignedGetUrl(account: NamedAccount, objectKey: String): String {
        val baseUrl = resolveBaseUrl(account)
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val expiresSeconds = 3600
        val host = baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')

        val encodedKey = objectKey.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        val region = account.s3Region
        val service = "s3"
        val scope = "$dateStamp/$region/$service/aws4_request"

        val queryParams = listOf(
            "X-Amz-Algorithm" to "AWS4-HMAC-SHA256",
            "X-Amz-Credential" to "${account.s3AccessKey}/$scope",
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to "$expiresSeconds",
            "X-Amz-SignedHeaders" to "host"
        ).sortedBy { it.first }

        val queryString = queryParams.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

        val canonicalRequest = listOf(
            "GET",
            "/${account.s3Bucket}/$encodedKey",
            queryString,
            "host:$host\n",
            "host",
            "UNSIGNED-PAYLOAD"
        ).joinToString("\n")

        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest)}"
        val signingKey  = signingKey(account.s3SecretKey, dateStamp, region, service)
        val signature   = hmacSHA256Hex(signingKey, stringToSign)

        return "$baseUrl/${account.s3Bucket}/$encodedKey?$queryString&X-Amz-Signature=$signature"
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private fun resolveBaseUrl(account: NamedAccount): String {
        val ep = account.s3Endpoint.trim().trimEnd('/')
        if (ep.isEmpty()) return "https://s3.${account.s3Region}.amazonaws.com"
        return if (ep.startsWith("http")) ep else "https://$ep"
    }

    private fun listObjects(
        account: NamedAccount,
        baseUrl: String,
        continuationToken: String?
    ): String {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val amzDate   = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

        val host = baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val bucket = account.s3Bucket

        // Build canonical query string
        val queryMap = mutableMapOf("list-type" to "2", "max-keys" to "1000")
        if (continuationToken != null) queryMap["continuation-token"] = continuationToken
        val canonicalQueryString = queryMap.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }

        val path = if (account.s3ForcePathStyle) "/$bucket" else "/"
        val canonicalHeaders = "host:$host\nx-amz-date:$amzDate\n"
        val signedHeaders = "host;x-amz-date"
        val payloadHash = sha256Hex("")

        val canonicalRequest = "GET\n$path\n$canonicalQueryString\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        val region  = account.s3Region
        val scope   = "$dateStamp/$region/s3/aws4_request"
        val strToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest)}"
        val sigKey  = signingKey(account.s3SecretKey, dateStamp, region, "s3")
        val sig     = hmacSHA256Hex(sigKey, strToSign)

        val url = if (account.s3ForcePathStyle) "$baseUrl/$bucket?$canonicalQueryString"
                  else "$baseUrl?$canonicalQueryString"

        val req = Request.Builder()
            .url(url)
            .get()
            .header("Host", host)
            .header("x-amz-date", amzDate)
            .header("Authorization",
                "AWS4-HMAC-SHA256 Credential=${account.s3AccessKey}/$scope, " +
                "SignedHeaders=$signedHeaders, Signature=$sig")
            .build()

        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
        return resp.body?.use { it.string() } ?: ""
    }

    private data class S3Object(
        val key: String,
        val size: Long,
        val lastModified: String,
        val eTag: String
    )

    private fun parseListObjectsV2(xml: String): Pair<List<S3Object>, String?> {
        val objects = mutableListOf<S3Object>()
        var nextToken: String? = null

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var currentTag = ""
        var key = ""; var size = 0L; var lastMod = ""; var eTag = ""
        var inContents = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = tag
                    if (tag == "Contents") { inContents = true; key = ""; size = 0L; lastMod = ""; eTag = "" }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (inContents) when (currentTag) {
                        "Key"          -> key = text
                        "Size"         -> size = text.toLongOrNull() ?: 0L
                        "LastModified" -> lastMod = text
                        "ETag"         -> eTag = text.trim('"')
                    } else if (currentTag == "NextContinuationToken") {
                        nextToken = text
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tag == "Contents" && inContents) {
                        if (key.isNotBlank() && !key.endsWith("/"))
                            objects += S3Object(key, size, lastMod, eTag)
                        inContents = false
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }
        return objects to nextToken
    }

    private fun assetFromS3Object(obj: S3Object, account: NamedAccount, baseUrl: String): CloudinaryAsset {
        val ext = obj.key.substringAfterLast(".", "").lowercase()
        // Store the raw object key in secureUrl; ViewModel will presign on demand
        val rawUrl = "$baseUrl/${account.s3Bucket}/${obj.key}"
        return CloudinaryAsset(
            assetId   = "s3:${account.id}:${obj.key}",
            publicId  = obj.key,
            format    = ext,
            resourceType = extensionToResourceType(ext),
            type      = "upload",
            createdAt = obj.lastModified,
            bytes     = obj.size,
            url       = rawUrl,
            secureUrl = rawUrl,   // will be replaced with presigned URL at play time
            displayName = obj.key.substringAfterLast("/").substringBeforeLast(".")
        )
    }

    // ─── Crypto helpers ──────────────────────────────────────────────────────

    private fun hmacSHA256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSHA256Hex(key: ByteArray, data: String): String =
        hmacSHA256(key, data).joinToString("") { "%02x".format(it) }

    private fun signingKey(secretKey: String, dateStamp: String, region: String, service: String): ByteArray {
        val kDate    = hmacSHA256("AWS4$secretKey".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion  = hmacSHA256(kDate, region)
        val kService = hmacSHA256(kRegion, service)
        return hmacSHA256(kService, "aws4_request")
    }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun extensionToResourceType(ext: String) = when {
        ext in setOf("mp3","wav","ogg","flac","aac","m4a","opus") -> "video"
        ext in setOf("mp4","mov","avi","mkv","webm") -> "video"
        ext in setOf("jpg","jpeg","png","gif","webp","svg","avif") -> "image"
        else -> "raw"
    }
}
