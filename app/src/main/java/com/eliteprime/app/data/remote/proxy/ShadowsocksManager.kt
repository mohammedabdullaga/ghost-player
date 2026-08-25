package com.eliteprime.app.data.remote.proxy

import android.content.Context
import android.util.Log
import com.eliteprime.app.data.config.ConfigRepository
import com.google.firebase.perf.FirebasePerformance
import kotlinx.coroutines.*
import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadowsocksManager @Inject constructor(
    private val configRepository: ConfigRepository
) {
    private val TAG = "ShadowsocksManager"
    private val processRef = AtomicReference<Process?>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // VPS Configuration (Creds hardcoded as requested)
    private val SERVER_PORT = "8388"
    private val PASSWORD = "Elitba7rainVpnService"
    private val METHOD = "chacha20-ietf-poly1305"
    
    // Local Configuration
    private val LOCAL_IP = "127.0.0.1"
    private val LOCAL_PORT = "1080"

    private var currentServerHost: String? = null

    suspend fun ensureProxyRunning(context: Context) {
        if (processRef.get() == null) {
            startProxy(context)
            // Wait briefly for local socket to be bound
            delay(350)
        }
    }

    fun startProxy(context: Context) {
        scope.launch {
            if (processRef.get() != null) {
                Log.d(TAG, "Shadowsocks is already running.")
                return@launch
            }

            val proxyHosts = configRepository.getProxyHosts()
            val serverHost = proxyHosts.firstOrNull()
            if (serverHost == null) {
                Log.e(TAG, "No proxy hosts available. Shadowsocks cannot start.")
                return@launch
            }
            currentServerHost = serverHost

            val nativeDir = context.applicationInfo.nativeLibraryDir
            val binaryPath = "$nativeDir/libsslocal.so"
            val binaryFile = File(binaryPath)
            
            if (!binaryFile.exists()) {
                Log.e(TAG, "Shadowsocks binary not found at $binaryPath. Make sure useLegacyPackaging is enabled.")
                return@launch
            }

            if (!binaryFile.canExecute()) {
                binaryFile.setExecutable(true)
            }

            try {
                val connTrace = FirebasePerformance.getInstance().newTrace("proxy_connection_time")
                connTrace.start()
                var traceStopped = false
                
                Log.i(TAG, "Starting Shadowsocks daemon: $binaryPath on $serverHost")
                
                val cmd = listOf(
                    binaryPath,
                    "-s", "$serverHost:$SERVER_PORT",
                    "-b", "$LOCAL_IP:$LOCAL_PORT",
                    "-k", PASSWORD,
                    "-m", METHOD,
                    "-U",
                    "--dns", "8.8.8.8,1.1.1.1",
                    "-vv"
                )

                val process = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()
                
                processRef.set(process)

                withTimeoutOrNull(12_000) {
                    process.inputStream.bufferedReader().use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            Log.d(TAG, "SS-LOG: $line")
                            
                            // "listening at" indicates the local SOCKS5 server is up and ready
                            if (line.contains("listening at", ignoreCase = true) && !traceStopped) {
                                connTrace.putAttribute("outcome", "success")
                                connTrace.stop()
                                traceStopped = true
                            }
                        }
                    }
                }

                // If loop finished or timed out without success
                if (!traceStopped) {
                    val outcome = if (process.isAlive) "timeout" else "process_exited"
                    connTrace.putAttribute("outcome", outcome)
                    connTrace.stop()
                    traceStopped = true
                    
                    if (outcome == "process_exited") {
                        val exitCode = try { process.exitValue() } catch(e: Exception) { -1 }
                        Log.e(TAG, "Proxy process exited early with code $exitCode")
                    } else {
                        Log.w(TAG, "Proxy connection timed out (no 'listening at' log found)")
                    }
                }

                val exitCode = process.waitFor()
                Log.w(TAG, "Shadowsocks process exited with code $exitCode")
                processRef.set(null)

            } catch (e: Exception) {
                if (e !is InterruptedIOException) {
                    Log.e(TAG, "Failed to start Shadowsocks: ${e.message}", e)
                }
                processRef.set(null)
            }
        }
    }

    fun restartProxy(context: Context) {
        val proxyHosts = configRepository.getProxyHosts()
        val targetHost = proxyHosts.firstOrNull()
        
        if (targetHost == null) {
            Log.e(TAG, "No proxy hosts available for restart.")
            stopProxy()
            return
        }
        if (targetHost == currentServerHost && processRef.get() != null) {
            Log.d(TAG, "Shadowsocks already running with active host: $targetHost. No restart needed.")
            return
        }

        scope.launch {
            stopProxy()
            delay(300)
            startProxy(context)
        }
    }

    fun stopProxy() {
        processRef.get()?.let { process ->
            Log.i(TAG, "Stopping Shadowsocks process...")
            process.destroy()
            processRef.set(null)
        }
    }
    
    fun isRunning(): Boolean = processRef.get() != null
}
