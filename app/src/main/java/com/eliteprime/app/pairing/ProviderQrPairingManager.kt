package com.eliteprime.app.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.eliteprime.app.data.repository.XtreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.EnumMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PAIRING_SESSION_MS = 5 * 60 * 1_000L
private const val PAIRING_QR_SIZE = 384
private const val MAX_FORM_BYTES = 24 * 1024
private const val TAG = "ProviderQrPairing"

@Singleton
class ProviderQrPairingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xtreamRepository: XtreamRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secureRandom = SecureRandom()
    private val _state = MutableStateFlow(ProviderQrPairingState())
    val state: StateFlow<ProviderQrPairingState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var timeoutJob: Job? = null
    private var activeToken: String? = null
    private var activeExpiresAtMs: Long = 0L

    suspend fun startPairing() {
        stopPairing()
        val host = resolveLanIpv4Address()
        if (host.isNullOrBlank()) {
            _state.value = ProviderQrPairingState(
                status = ProviderQrPairingStatus.ERROR,
                message = "Could not find a Wi-Fi/LAN IP address. Make sure the TV is connected to your network."
            )
            return
        }

        val socket = runCatching { ServerSocket(0, 8) }.getOrElse { error ->
            _state.value = ProviderQrPairingState(
                status = ProviderQrPairingStatus.ERROR,
                message = "Could not start pairing server: ${error.message ?: "unknown error"}"
            )
            return
        }
        val token = generateToken()
        val port = socket.localPort
        val url = "http://$host:$port/pair?t=$token"
        val expiresAt = System.currentTimeMillis() + PAIRING_SESSION_MS

        serverSocket = socket
        activeToken = token
        activeExpiresAtMs = expiresAt
        _state.value = ProviderQrPairingState(
            status = ProviderQrPairingStatus.READY,
            url = url,
            qrBitmap = createQrBitmap(url),
            expiresAtMs = expiresAt,
            message = "Scan from a phone on the same Wi-Fi."
        )

        acceptJob = scope.launch {
            acceptLoop(socket)
        }
        timeoutJob = scope.launch {
            delay(PAIRING_SESSION_MS)
            if (_state.value.status == ProviderQrPairingStatus.READY) {
                Log.i(TAG, "Pairing session expired. Auto-renewing QR code...")
                startPairing()
            }
        }
    }

    suspend fun stopPairing(message: String? = null) {
        val oldAcceptJob = acceptJob
        val oldTimeoutJob = timeoutJob
        acceptJob = null
        timeoutJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeToken = null
        activeExpiresAtMs = 0L
        if (oldAcceptJob != null) runCatching { oldAcceptJob.cancelAndJoin() }
        if (oldTimeoutJob != null) runCatching { oldTimeoutJob.cancel() }
        
        _state.value = ProviderQrPairingState(
            status = if (message == null) ProviderQrPairingStatus.IDLE else ProviderQrPairingStatus.ERROR,
            message = message
        )
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            scope.launch {
                runCatching {
                    handleClient(client)
                }.onFailure { error ->
                    Log.w(TAG, "Pairing client request failed", error)
                    runCatching { client.close() }
                }
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(' ')
            if (parts.size < 2) {
                writeResponse(client.getOutputStream(), 400, "text/plain", "Bad request")
                return
            }
            val method = parts[0].uppercase(Locale.US)
            val pathAndQuery = parts[1]
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val key = line.substringBefore(':', "").trim().lowercase(Locale.US)
                val value = line.substringAfter(':', "").trim()
                if (key.isNotBlank()) headers[key] = value
            }

            when {
                method == "GET" && pathAndQuery.startsWith("/pair") -> {
                    val token = queryParams(pathAndQuery)["t"]
                    if (!isTokenValid(token)) {
                        writeHtml(client.getOutputStream(), 403, errorPage("Pairing link expired or invalid. Start a new QR session on the TV."))
                    } else {
                        writeHtml(client.getOutputStream(), 200, formPage(token.orEmpty()))
                    }
                }
                method == "POST" && pathAndQuery.startsWith("/submit") -> {
                    val length = headers["content-length"]?.toIntOrNull()?.coerceAtMost(MAX_FORM_BYTES) ?: 0
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) {
                        val count = reader.read(body, read, length - read)
                        if (count <= 0) break
                        read += count
                    }
                    val form = parseForm(String(body, 0, read))
                    val token = form["token"]
                    if (!isTokenValid(token)) {
                        writeHtml(client.getOutputStream(), 403, errorPage("Pairing session expired. Start a new QR session on the TV."))
                        return
                    }

                    val username = form["username"].orEmpty()
                    val password = form["password"].orEmpty()

                    if (username.isBlank() || password.isBlank()) {
                        writeHtml(client.getOutputStream(), 400, errorPage("Please enter both username and password."))
                        return
                    }

                    _state.value = _state.value.copy(
                        status = ProviderQrPairingStatus.RECEIVING,
                        message = "Phone submitted provider details. Validating..."
                    )

                    scope.launch {
                        val success = xtreamRepository.login(username, password)
                        if (success) {
                            _state.value = _state.value.copy(
                                status = ProviderQrPairingStatus.COMPLETE,
                                message = "Provider added from phone: ArenaLive",
                                username = username,
                                password = password
                            )
                        } else {
                            _state.value = _state.value.copy(
                                status = ProviderQrPairingStatus.READY,
                                message = "Login failed. Please check your credentials."
                            )
                        }
                    }

                    writeHtml(client.getOutputStream(), 200, successPage())
                }
                else -> writeResponse(client.getOutputStream(), 404, "text/plain", "Not found")
            }
        }
    }

    private fun isTokenValid(token: String?): Boolean =
        token != null && token == activeToken && System.currentTimeMillis() < activeExpiresAtMs

    private fun generateToken(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun createQrBitmap(value: String): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.MARGIN] = 1
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, PAIRING_QR_SIZE, PAIRING_QR_SIZE, hints)
        val pixels = IntArray(PAIRING_QR_SIZE * PAIRING_QR_SIZE)
        for (y in 0 until PAIRING_QR_SIZE) {
            for (x in 0 until PAIRING_QR_SIZE) {
                pixels[y * PAIRING_QR_SIZE + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(PAIRING_QR_SIZE, PAIRING_QR_SIZE, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, PAIRING_QR_SIZE, 0, 0, PAIRING_QR_SIZE, PAIRING_QR_SIZE)
        }
    }

    private fun resolveLanIpv4Address(): String? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivity?.activeNetwork
        val capabilities = activeNetwork?.let(connectivity::getNetworkCapabilities)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (isWifi) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            // Check for permission before calling getConnectionInfo to avoid SecurityException
            if (context.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                val ip = wifi?.connectionInfo?.ipAddress?.takeIf { it != 0 }
                if (ip != null) return Formatter.formatIpAddress(ip)
            }
        }
        return NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .firstOrNull { !it.startsWith("127.") }
    }

    private fun queryParams(pathAndQuery: String): Map<String, String> =
        pathAndQuery.substringAfter('?', "").takeIf { it.isNotBlank() }?.let(::parseForm) ?: emptyMap()

    private fun parseForm(body: String): Map<String, String> =
        body.split('&').mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val key = decode(part.substringBefore('=', ""))
            val value = decode(part.substringAfter('=', ""))
            key to value
        }.toMap()

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (e: Exception) {
        value
    }

    private fun writeHtml(output: OutputStream, status: Int, html: String) {
        writeResponse(output, status, "text/html; charset=utf-8", html)
    }

    private fun writeResponse(output: OutputStream, status: Int, contentType: String, body: String) {
        val statusText = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "OK"
        }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = ("HTTP/1.1 $status $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
        runCatching {
            output.write(header)
            output.write(bytes)
            output.flush()
        }
    }

    private fun formPage(token: String): String = """
        <!doctype html>
        <html lang="ar">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>ArenaLive</title>
          <style>
            *{direction:rtl;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;background:#0D0F14;color:#f8fafc;margin:0;padding:24px;display:flex;justify-content:center;align-items:center;min-height:100vh}
            main{width:100%;max-width:440px;background:rgba(31,31,31,0.95);border:1px solid rgba(255,96,41,0.3);border-radius:28px;padding:32px;box-shadow:0 24px 80px rgba(0,0,0,0.5)}
            .logo{font-size:32px;font-weight:800;text-align:center;margin-bottom:8px}
            .logo span{color:#FF6029}
            h2{font-size:18px;font-weight:400;color:rgba(255,255,255,0.6);text-align:center;margin:0 0 24px}
            label{display:block;margin-top:16px;font-size:14px;font-weight:600;color:rgba(255,255,255,0.8)}
            input{width:100%;margin-top:8px;padding:16px;border-radius:14px;border:1px solid rgba(255,255,255,0.1);background:rgba(255,255,255,0.05);color:#fff;font-size:16px;transition:border-color 0.2s}
            input:focus{outline:none;border-color:#FF6029;background:rgba(255,255,255,0.08)}
            button{width:100%;margin-top:28px;padding:18px;border:0;border-radius:16px;background:#FF6029;color:#fff;font-weight:700;font-size:17px;cursor:pointer;transition:transform 0.1s}
            button:active{transform:scale(0.98)}
            .hint{font-size:13px;color:rgba(255,255,255,0.4);text-align:center;margin-top:20px}
          </style>
        </head>
        <body>
        <main>
          <div class="logo">Arena<span>Live</span></div>
          <h2>تسجيل الدخول للاستمتاع بالعالم</h2>
          <form method="post" action="/submit">
            <input type="hidden" name="token" value="${token.escapeHtml()}">
            <label>اسم المستخدم</label>
            <input name="username" autocomplete="username" required>
            <label>كلمة المرور</label>
            <input name="password" type="password" autocomplete="current-password" required>
            <button type="submit">إرسال إلى التلفزيون</button>
            <p class="hint">يتم إرسال بياناتك بشكل آمن عبر شبكتك المحلية.</p>
          </form>
        </main>
        </body>
        </html>
    """.trimIndent()

    private fun successPage(): String = """
        <!doctype html>
        <html lang="ar">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            *{direction:rtl;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:#0D0F14;color:#f8fafc;padding:24px;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0}
            main{max-width:400px;width:100%;background:rgba(31,31,31,0.95);border:1px solid rgba(79,211,154,0.3);border-radius:28px;padding:40px;text-align:center;box-shadow:0 24px 80px rgba(0,0,0,0.5)}
            h1{color:#4FD39A;margin:0 0 12px;font-size:28px}
            p{color:rgba(255,255,255,0.6);line-height:1.8;margin-bottom:0;font-size:16px}
            .logo{font-size:20px;font-weight:700;margin-bottom:32px;opacity:0.8}
            .logo span{color:#FF6029}
          </style>
        </head>
        <body>
        <main>
          <div class="logo">Arena<span>Live</span></div>
          <h1>تم الإرسال إلى التلفزيون!</h1>
          <p>تم استلام بياناتك بنجاح. يرجى متابعة شاشة التلفزيون لاستكمال تسجيل الدخول.</p>
        </main>
        </body>
        </html>
    """.trimIndent()

    private fun errorPage(message: String): String = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:#0D0F14;color:#f8fafc;padding:24px;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0}
            main{max-width:400px;width:100%;background:rgba(31,31,31,0.95);border:1px solid rgba(255,82,82,0.3);border-radius:28px;padding:40px;text-align:center;box-shadow:0 24px 80px rgba(0,0,0,0.5)}
            h1{color:#FF5252;margin:0 0 12px;font-size:28px}
            p{color:rgba(255,255,255,0.6);line-height:1.5}
            button{margin-top:20px;padding:14px 24px;border:0;border-radius:12px;background:rgba(255,255,255,0.1);color:#fff;font-weight:600;cursor:pointer}
          </style>
        </head>
        <body>
        <main>
          <h1>Connection Error</h1>
          <p>${message.escapeHtml()}</p>
          <button onclick="window.history.back()">Go Back</button>
        </main>
        </body>
        </html>
    """.trimIndent()

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}

data class ProviderQrPairingState(
    val status: ProviderQrPairingStatus = ProviderQrPairingStatus.IDLE,
    val url: String? = null,
    val qrBitmap: Bitmap? = null,
    val expiresAtMs: Long = 0L,
    val message: String? = null,
    val username: String? = null,
    val password: String? = null
)

enum class ProviderQrPairingStatus {
    IDLE, READY, RECEIVING, COMPLETE, ERROR
}
