package com.cloudinaryfiles.app.data.repository

import com.cloudinaryfiles.app.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ServerSocket

private const val LOG = "LoopbackOAuth"

/**
 * Starts a local HTTP server on a random port.
 * Used for Google Drive OAuth2 loopback flow (Desktop app type).
 * Google allows http://127.0.0.1 redirect for Desktop OAuth clients.
 */
class LoopbackOAuthServer {

    private var serverSocket: ServerSocket? = null

    /** Bind to a random available port and return it. */
    fun start(): Int {
        AppLogger.d(LOG, "start(): binding to a random local port…")
        serverSocket = ServerSocket(0)
        val port = serverSocket!!.localPort
        AppLogger.i(LOG, "start(): listening on http://127.0.0.1:$port")
        return port
    }

    /**
     * Suspends until the browser redirects to http://127.0.0.1:port?code=...\
     * Sends a friendly "you can close this tab" page to the browser.
     */
    suspend fun waitForCode(): String = withContext(Dispatchers.IO) {
        val server = serverSocket ?: run {
            AppLogger.e(LOG, "waitForCode(): ServerSocket is null — start() was not called!")
            throw Exception("Server not started")
        }
        AppLogger.d(LOG, "waitForCode(): waiting for browser redirect (timeout=120s)…")
        server.soTimeout = 120_000

        server.accept().use { socket ->
            AppLogger.i(LOG, "waitForCode(): connection accepted from ${socket.inetAddress}")
            socket.soTimeout = 15_000
            val reader = socket.getInputStream().bufferedReader()

            val requestLine = reader.readLine() ?: ""
            AppLogger.d(LOG, "waitForCode(): request line = '$requestLine'")

            // Drain remaining headers
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            // Parse code from "GET /?code=XXXX&... HTTP/1.1"
            val code = Regex("[?&]code=([^&\\s]+)").find(requestLine)?.groupValues?.get(1)
                ?: Regex("[?&]error=([^&\\s]+)").find(requestLine)?.groupValues?.get(1)
                    ?.let { err ->
                        AppLogger.e(LOG, "waitForCode(): OAuth error from provider: $err")
                        throw Exception("OAuth error: $err")
                    }
                ?: run {
                    AppLogger.e(LOG, "waitForCode(): no 'code' or 'error' parameter in redirect URL")
                    AppLogger.e(LOG, "  Full request line: $requestLine")
                    throw Exception("No authorization code received")
                }

            AppLogger.i(LOG, "waitForCode(): authorization code received (${code.take(10)}…)")

            val html = """<html><head><style>
                body{font-family:sans-serif;background:#0E0C1C;color:white;display:flex;
                justify-content:center;align-items:center;height:100vh;margin:0}
                .box{text-align:center;padding:40px;border-radius:16px;background:#1a1830}
                h2{color:#7C4DFF;margin-bottom:8px}p{color:#888;margin:0}
                </style></head><body><div class="box">
                <h2>&#10003; Authorization Complete</h2>
                <p>You can close this tab and return to CloudVault.</p>
                </div></body></html>"""

            val out = socket.getOutputStream()
            out.write(
                "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html"
                    .toByteArray(Charsets.UTF_8)
            )
            out.flush()
            AppLogger.d(LOG, "waitForCode(): success response sent to browser")
            code
        }
    }

    fun stop() {
        AppLogger.d(LOG, "stop(): closing ServerSocket")
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
