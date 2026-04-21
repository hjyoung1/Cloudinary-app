package com.cloudinaryfiles.app.data.repository

import com.cloudinaryfiles.app.AppLogger
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val RETRY_LOG = "RetryUtil"

/**
 * Retries a suspend block up to [maxAttempts] times with exponential backoff.
 * Only retries on transient network errors (DNS, timeout, IO).
 */
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 500L,
    maxDelayMs: Long = 5000L,
    block: suspend (attempt: Int) -> T
): T {
    var currentDelay = initialDelayMs
    for (attempt in 1..maxAttempts) {
        try {
            if (attempt > 1) AppLogger.d(RETRY_LOG, "withRetry: attempt $attempt/$maxAttempts")
            return block(attempt)
        } catch (e: Exception) {
            val retryable = isRetryable(e)
            AppLogger.w(RETRY_LOG, "withRetry: attempt $attempt/$maxAttempts FAILED " +
                    "(retryable=$retryable, type=${e.javaClass.simpleName}, msg=${e.message})")
            if (attempt == maxAttempts || !retryable) {
                AppLogger.e(RETRY_LOG, "withRetry: giving up after $attempt attempt(s)")
                throw e
            }
            AppLogger.d(RETRY_LOG, "withRetry: backing off ${currentDelay}ms before retry…")
            delay(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        }
    }
    throw IllegalStateException("Retry exhausted")
}

/** Blocking version for non-suspend contexts (e.g. OkHttp synchronous calls). */
fun <T> withRetryBlocking(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 500L,
    maxDelayMs: Long = 5000L,
    block: (attempt: Int) -> T
): T {
    var currentDelay = initialDelayMs
    for (attempt in 1..maxAttempts) {
        try {
            if (attempt > 1) AppLogger.d(RETRY_LOG, "withRetryBlocking: attempt $attempt/$maxAttempts")
            return block(attempt)
        } catch (e: Exception) {
            val retryable = isRetryable(e)
            AppLogger.w(RETRY_LOG, "withRetryBlocking: attempt $attempt/$maxAttempts FAILED " +
                    "(retryable=$retryable, type=${e.javaClass.simpleName}, msg=${e.message})")
            if (attempt == maxAttempts || !retryable) {
                AppLogger.e(RETRY_LOG, "withRetryBlocking: giving up after $attempt attempt(s)")
                throw e
            }
            AppLogger.d(RETRY_LOG, "withRetryBlocking: sleeping ${currentDelay}ms before retry…")
            Thread.sleep(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        }
    }
    throw IllegalStateException("Retry exhausted")
}

private fun isRetryable(e: Exception): Boolean = when (e) {
    is UnknownHostException  -> true
    is SocketTimeoutException -> true
    is IOException -> {
        val msg = e.message?.lowercase() ?: ""
        msg.contains("timeout") || msg.contains("reset") || msg.contains("refused")
    }
    else -> false
}
