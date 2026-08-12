package com.eliteprime.app.data.remote.proxy

import android.content.Context
import android.util.Log
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.util.StringObfuscator
import kotlinx.coroutines.*
import java.io.File
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
    
    // Obfuscated: Elitba7rainVpnService
    private val P_DATA = byteArrayOf(
        0x04.toByte(), 0x1E.toByte(), 0x0C.toByte(), 0x1A.toByte(), 0x03.toByte(), 0x2D.toByte(), 0x5E.toByte(), 0x04.toByte(), 
        0x04.toByte(), 0x3A.toByte(), 0x0B.toByte(), 0x35.toByte(), 0x05.toByte(), 0x1C.toByte(), 0x36.toByte(), 0x2E.toByte(), 
        0x17.toByte(), 0x0F.toByte(), 0x5B.toByte(), 0x53.toByte(), 0x57.toByte()
    )
    private val PASSWORD = StringObfuscator.decode(P_DATA)

    // Obfuscated: chacha20-ietf-poly1305
    private val M_DATA = byteArrayOf(
        0x22.toByte(), 0x1A.toByte(), 0x04.toByte(), 0x0D.toByte(), 0x09.toByte(), 0x2D.toByte(), 0x5B.toByte(), 0x46.toByte(), 
        0x48.toByte(), 0x3A.toByte(), 0x00.toByte(), 0x17.toByte(), 0x13.toByte(), 0x5F.toByte(), 0x15.toByte(), 0x24.toByte(), 
        0x09.toByte(), 0x00.toByte(), 0x03.toByte(), 0x03.toByte(), 0x02.toByte(), 0x03.toByte()
    )
    private val METHOD = StringObfuscator.decode(M_DATA)
    
    // Local Configuration
    private val LOCAL_IP = "127.0.0.1"
    private val LOCAL_PORT = "1080"

    fun startProxy(context: Context) {
        scope.launch {
            if (processRef.get() != null) {
                Log.d(TAG, "Shadowsocks is already running.")
                return@launch
            }

            // Wait for config if not ready
            var proxyHosts = configRepository.getProxyHosts()
            if (proxyHosts.isEmpty()) {
                Log.d(TAG, "Waiting for config to fetch proxy hosts...")
                withTimeoutOrNull(5000) {
                    while (configRepository.getProxyHosts().isEmpty()) delay(500)
                }
                proxyHosts = configRepository.getProxyHosts()
            }
            
            val serverHost = proxyHosts.firstOrNull() ?: "cdn.sharkbh.online"

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

                process.inputStream.bufferedReader().use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        Log.d(TAG, "SS-LOG: $line")
                    }
                }

                val exitCode = process.waitFor()
                Log.w(TAG, "Shadowsocks process exited with code $exitCode")
                processRef.set(null)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Shadowsocks: ${e.message}", e)
                processRef.set(null)
            }
        }
    }

    fun stopProxy() {
        processRef.get()?.let { process ->
            Log.i(TAG, "Stopping Shadowsocks process...")
            process.destroy()
            processRef.set(null)
        }
        scope.coroutineContext.cancelChildren()
    }
    
    fun isRunning(): Boolean = processRef.get() != null
}
