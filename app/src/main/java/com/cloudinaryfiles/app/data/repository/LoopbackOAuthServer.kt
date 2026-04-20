package com.cloudinaryfiles.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ServerSocket

/**
 * Starts a local HTTP server on a random port.
 * Used for Google Drive OAuth2 loopback flow (Desktop app type).
 * Google allows http://127.0.0.1 redirect for Desktop OAuth clients.
 */
class LoopbackOAuthServer {

    private var serverSocket: ServerSocket? = null

    /** Bind to a random available port and return it. */
    fun start(): Int {
        serverSocket = ServerSocket(0)
        return serverSocket!!.localPort
    }

    /**
     * Suspends until the browser redirects to http://127.0.0.1:port?code=...
     * Sends a friendly "you can close this tab" page to the browser.
     */
    suspend fun waitForCode(): String = withContext(Dispatchers.IO) {
        val server = serverSocket ?: throw Exception("Server not started")
        server.soTimeout = 120_000 // 2-minute timeout
        server.accept().use { socket ->
            socket.soTimeout = 15_000
            val reader = socket.getInputStream().bufferedReader()
            // Read the request line (contains the code in the URL)
            val requestLine = reader.readLine() ?: ""
            // Drain remaining headers so browser doesn't stall waiting for us to read
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            // Parse code from "GET /?code=XXXX&... HTTP/1.1"
            val code = Regex("[?&]code=([^&\\s]+)").find(requestLine)?.groupValues?.get(1)
                ?: Regex("[?&]error=([^&\\s]+)").find(requestLine)?.groupValues?.get(1)
                    ?.let { throw Exception("OAuth error: $it") }
                ?: throw Exception("No authorization code received")

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
            code
        }
    }

    fun stop() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
