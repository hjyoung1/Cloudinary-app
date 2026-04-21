package com.cloudinaryfiles.app

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CloudVault AppLogger
 *
 * Writes every log line to two locations:
 *   1. Internal storage  → <app-files-dir>/cloudvault_logs/cloudvault_YYYY-MM-DD.log
 *      Pull with: adb pull /data/data/com.cloudinaryfiles.app/files/cloudvault_logs/
 *   2. External files dir → Android/data/com.cloudinaryfiles.app/files/cloudvault_logs/
 *      Pull with: adb pull /sdcard/Android/data/com.cloudinaryfiles.app/files/cloudvault_logs/
 *      Or open in Android Studio → Device File Explorer
 *
 * Rotation: each file is capped at MAX_LOG_SIZE_BYTES; keeps last MAX_LOG_FILES files.
 * Thread-safe: all disk I/O runs on a dedicated single-thread executor.
 */
object AppLogger {

    private const val TAG             = "CloudVault"
    private const val MAX_LOG_SIZE    = 8 * 1024 * 1024L   // 8 MB per file
    private const val MAX_LOG_FILES   = 7
    private const val LOG_DIR_NAME    = "cloudvault_logs"

    private val executor  = Executors.newSingleThreadExecutor()
    private val ready     = AtomicBoolean(false)
    private val dateFmt   = SimpleDateFormat("yyyy-MM-dd",          Locale.US)
    private val timeFmt   = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var internalLogDir:  File? = null
    @Volatile private var externalLogDir:  File? = null

    // ─────────────────────────────────────────────────────────────────────────
    //  Init  (call once from Application.onCreate)
    // ─────────────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        internalLogDir = File(context.filesDir, LOG_DIR_NAME).also { it.mkdirs() }
        try {
            externalLogDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
                .also { it.mkdirs() }
        } catch (e: Exception) {
            Log.w(TAG, "External log dir unavailable: ${e.message}")
        }
        ready.set(true)

        val appVerName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }

        val sep = "═".repeat(64)
        i("AppLogger", sep)
        i("AppLogger", "  CloudVault — Logging started")
        i("AppLogger", "  Time       : ${timeFmt.format(Date())}")
        i("AppLogger", "  Device     : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        i("AppLogger", "  Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        i("AppLogger", "  App version: $appVerName")
        i("AppLogger", "  Package    : ${context.packageName}")
        i("AppLogger", "  Internal   : ${internalLogDir?.absolutePath}")
        i("AppLogger", "  External   : ${externalLogDir?.absolutePath ?: "N/A"}")
        i("AppLogger", sep)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun v(tag: String, msg: String)                     = write("V", tag, msg, null)
    fun d(tag: String, msg: String)                     = write("D", tag, msg, null)
    fun i(tag: String, msg: String)                     = write("I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = write("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = write("E", tag, msg, t)

    /** Log a network request start — URL is printed but credentials are redacted. */
    fun request(tag: String, method: String, url: String) {
        i(tag, "→ $method ${redactUrl(url)}")
    }

    /** Log a network response. */
    fun response(tag: String, method: String, url: String, code: Int, bodySnippet: String?, elapsedMs: Long) {
        val ok = code in 200..299
        val level = if (ok) "I" else "E"
        val snippet = bodySnippet?.take(600)?.replace("\n", " ")?.trim() ?: ""
        write(level, tag, "← HTTP $code [${elapsedMs}ms] ${redactUrl(url)}" +
                if (snippet.isNotEmpty()) "\n   Body: $snippet" else "", null)
    }

    /** Redact access_token / secret from URLs before logging. */
    fun redactUrl(url: String): String =
        url.replace(Regex("access_token=[^&]+"), "access_token=***REDACTED***")
           .replace(Regex("token=[^&]+"),        "token=***REDACTED***")

    /** Mask a secret — show only first 4 and last 4 chars. */
    fun mask(secret: String?): String {
        if (secret.isNullOrBlank()) return "<empty>"
        if (secret.length <= 8)     return "***"
        return "${secret.take(4)}…${secret.takeLast(4)} (len=${secret.length})"
    }

    fun getInternalLogDir(): File? = internalLogDir
    fun getExternalLogDir(): File? = externalLogDir

    /** Returns sorted list of all log files (newest first). */
    fun allLogFiles(): List<File> = listOfNotNull(internalLogDir, externalLogDir)
        .flatMap { it.listFiles()?.toList() ?: emptyList() }
        .distinctBy { it.name }
        .sortedByDescending { it.lastModified() }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal
    // ─────────────────────────────────────────────────────────────────────────

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        // Always mirror to Logcat (visible in Android Studio / adb logcat)
        when (level) {
            "V" -> Log.v(tag, msg, t)
            "D" -> Log.d(tag, msg, t)
            "I" -> Log.i(tag, msg, t)
            "W" -> Log.w(tag, msg, t)
            "E" -> Log.e(tag, msg, t)
            else -> Log.d(tag, msg, t)
        }

        if (!ready.get()) return   // logger not yet initialized — only logcat output

        // File write on background thread to never block callers
        val timestamp = timeFmt.format(Date())
        val dateStr   = dateFmt.format(Date())
        val line = buildLogLine(timestamp, level, tag, msg, t)

        executor.execute {
            listOfNotNull(internalLogDir, externalLogDir).forEach { dir ->
                appendToFile(dir, dateStr, line)
            }
        }
    }

    private fun buildLogLine(ts: String, level: String, tag: String, msg: String, t: Throwable?): String =
        buildString {
            append("$ts [$level] $tag: $msg")
            if (t != null) {
                append("\n    !! ${t.javaClass.name}: ${t.message}")
                t.stackTrace.take(12).forEach { frame -> append("\n       at $frame") }
                var cause = t.cause; var depth = 0
                while (cause != null && depth < 3) {
                    append("\n    Caused by: ${cause.javaClass.name}: ${cause.message}")
                    cause.stackTrace.take(6).forEach { frame -> append("\n       at $frame") }
                    cause = cause.cause; depth++
                }
            }
            append("\n")
        }

    private fun appendToFile(dir: File, dateStr: String, line: String) {
        try {
            val logFile = File(dir, "cloudvault_$dateStr.log")

            // Rotate when the file exceeds the size cap
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                val rotated = File(dir, "cloudvault_${dateStr}_${System.currentTimeMillis()}.log")
                logFile.renameTo(rotated)
            }

            FileWriter(logFile, true).use { w -> w.write(line) }

            // Prune old files
            val files = dir.listFiles()
                ?.filter { it.name.startsWith("cloudvault_") && it.name.endsWith(".log") }
                ?.sortedBy { it.lastModified() }
                ?: return
            if (files.size > MAX_LOG_FILES) {
                files.dropLast(MAX_LOG_FILES).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AppLogger: disk write failed in ${dir.path}: ${e.message}")
        }
    }
}
