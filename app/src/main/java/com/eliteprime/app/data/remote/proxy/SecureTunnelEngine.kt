package com.eliteprime.app.data.remote.proxy

import android.util.Log
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.remote.util.NetworkUtils
import com.eliteprime.app.util.StringObfuscator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLSocket

@Singleton
class SecureTunnelEngine @Inject constructor(
    private val configRepository: ConfigRepository
) {
    private val TAG = "SecureTunnelEngine"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val UPSTREAM_PORT = 443
    // Obfuscated: Proxy-Authorization: Basic ZWxpdHByaW1lOkVlYmgxMjM0
    private val A_DATA = byteArrayOf(
        0x11, 0x00, 0x0A, 0x1E, 0x18, 0x61, 0x28, 0x03, 0x11, 0x3B, 0x0A, 0x15, 0x1C, 0x0A, 0x04, 0x37, 
        0x0C, 0x0A, 0x5C, 0x0A, 0x12, 0x74, 0x20, 0x07, 0x0C, 0x05, 0x41, 0x12, 0x0E, 0x06, 0x15, 0x37, 
        0x2D, 0x23, 0x04, 0x10, 0x12, 0x7A, 0x09, 0x1C, 0x7D, 0x5B, 0x64, 0x5A, 0x23, 0x15, 0x1D, 0x23, 
        0x0B, 0x01, 0x59
    )
    private val AUTH_HEADER = StringObfuscator.decode(A_DATA)

    fun start(port: Int = 8080) {
        if (_isRunning.value) {
            Log.d(TAG, "Secure Tunnel Engine already running.")
            return
        }
        
        job?.cancel()
        job = scope.launch {
            try {
                Log.d(TAG, "Attempting to start Secure Tunnel Engine on 127.0.0.1:$port")
                serverSocket?.safeClose()
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("127.0.0.1", port))
                }
                _isRunning.value = true
                Log.i(TAG, "Secure Tunnel Engine successfully started on 127.0.0.1:$port")

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server start error: ${e.message}", e)
                _isRunning.value = false
            }
        }
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        var upstreamSocket: Socket? = null
        try {
            clientSocket.soTimeout = 30000
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val headerData = readHeaders(clientIn)
            if (headerData.isEmpty()) return@withContext
            
            val rawRequest = String(headerData, Charsets.ISO_8859_1)
            val firstLine = rawRequest.lineSequence().firstOrNull() ?: ""
            Log.d(TAG, "Relaying request: $firstLine")
            
            val proxyHosts = configRepository.getProxyHosts()
            // Obfuscated cdn.sharkbh.online
            val fallbackData = byteArrayOf(
                0x22, 0x12, 0x0b, 0x42, 0x12, 0x0c, 0x00, 0x13, 0x0a, 0x04, 0x0d, 0x4a, 0x0a, 0x00, 0x09, 0x01, 
                0x0b, 0x04
            )
            val upstreamHost = proxyHosts.firstOrNull() ?: StringObfuscator.decode(fallbackData)

            val sslContext = NetworkUtils.getUnsafeSslContext()
            val rawUpstream = Socket()
            rawUpstream.connect(InetSocketAddress(upstreamHost, UPSTREAM_PORT), 15000)
            rawUpstream.soTimeout = 30000
            
            val tlsUpstream = sslContext.socketFactory.createSocket(rawUpstream, upstreamHost, UPSTREAM_PORT, true) as SSLSocket
            tlsUpstream.startHandshake()
            upstreamSocket = tlsUpstream
            
            val upstreamIn = tlsUpstream.getInputStream()
            val upstreamOut = tlsUpstream.getOutputStream()

            val modifiedRequest = injectAuthHeader(rawRequest)
            upstreamOut.write(modifiedRequest.toByteArray(Charsets.ISO_8859_1))
            upstreamOut.flush()

            val relayJob1 = launch { relay(clientIn, upstreamOut) }
            val relayJob2 = launch { relay(upstreamIn, clientOut) }
            
            joinAll(relayJob1, relayJob2)

        } catch (e: Exception) {
            Log.v(TAG, "Session closed: ${e.message}")
        } finally {
            clientSocket.safeClose()
            upstreamSocket?.safeClose()
        }
    }

    private fun readHeaders(inputStream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1)
        val endOfHeaders = "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
        var matchCount = 0

        try {
            while (inputStream.read(buffer) != -1) {
                output.write(buffer)
                if (buffer[0] == endOfHeaders[matchCount]) {
                    matchCount++
                    if (matchCount == endOfHeaders.size) break
                } else {
                    matchCount = if (buffer[0] == endOfHeaders[0]) 1 else 0
                }
                if (output.size() > 16384) break
            }
        } catch (e: Exception) {}
        return output.toByteArray()
    }

    private fun injectAuthHeader(request: String): String {
        val lines = request.split(Regex("\r?\n")).toMutableList()
        if (lines.isEmpty()) return request
        
        val hasAuth = lines.any { it.startsWith("Proxy-Authorization:", ignoreCase = true) }
        if (!hasAuth && lines.size > 1) {
            lines.add(1, AUTH_HEADER)
        }
        return lines.joinToString("\r\n")
    }

    private fun relay(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384)
        try {
            var length: Int
            while (input.read(buffer).also { length = it } != -1) {
                output.write(buffer, 0, length)
                output.flush()
            }
        } catch (e: Exception) {}
    }

    fun stop() {
        _isRunning.value = false
        job?.cancel()
        serverSocket?.safeClose()
        Log.i(TAG, "Secure Tunnel Engine stopped")
    }

    private fun Socket.safeClose() = try { close() } catch (e: Exception) {}
    private fun ServerSocket.safeClose() = try { close() } catch (e: Exception) {}
}
