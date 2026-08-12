package com.eliteprime.app.data.remote.proxy

import android.util.Log
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.remote.util.NetworkUtils
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
    private val AUTH_HEADER = "Proxy-Authorization: Basic ZWxpdHByaW1lOkVlYmgxMjM0"

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
            val upstreamHost = proxyHosts.firstOrNull() ?: "cdn.sharkbh.online"

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
