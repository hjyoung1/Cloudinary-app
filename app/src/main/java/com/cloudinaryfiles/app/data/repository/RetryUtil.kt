package com.cloudinaryfiles.app.data.repository

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
            return block(attempt)
        } catch (e: Exception) {
            if (attempt == maxAttempts || !isRetryable(e)) throw e
            delay(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        }
    }
    throw IllegalStateException("Retry exhausted") // unreachable
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
            return block(attempt)
        } catch (e: Exception) {
            if (attempt == maxAttempts || !isRetryable(e)) throw e
            Thread.sleep(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        }
    }
    throw IllegalStateException("Retry exhausted")
}

private fun isRetryable(e: Exception): Boolean = when (e) {
    is UnknownHostException -> true
    is SocketTimeoutException -> true
    is IOException -> {
        val msg = e.message?.lowercase() ?: ""
        msg.contains("timeout") || msg.contains("reset") || msg.contains("refused")
    }
    else -> false
}
