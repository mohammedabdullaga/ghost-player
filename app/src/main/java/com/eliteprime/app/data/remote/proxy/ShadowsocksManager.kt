package com.eliteprime.app.data.remote.proxy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadowsocksManager @Inject constructor() {
    private val TAG = "ShadowsocksManager"
    private val processRef = AtomicReference<Process?>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // VPS Configuration
    private val SERVER_HOST = "cdn.sharkbh.online"
    private val SERVER_PORT = "8388"
    private val PASSWORD = "Elitba7rainVpnService"
    private val METHOD = "chacha20-ietf-poly1305"
    
    // Local Configuration
    private val LOCAL_IP = "127.0.0.1"
    private val LOCAL_PORT = "1080"

    fun startProxy(context: Context) {
        if (processRef.get() != null) {
            Log.d(TAG, "Shadowsocks is already running.")
            return
        }

        val nativeDir = context.applicationInfo.nativeLibraryDir
        Log.i(TAG, "Native Library Directory: $nativeDir")
        val binaryPath = "$nativeDir/libsslocal.so"
        val binaryFile = File(binaryPath)
        
        Log.i(TAG, "Checking binary at: $binaryPath (exists: ${binaryFile.exists()})")

        if (!binaryFile.exists()) {
            Log.e(TAG, "Shadowsocks binary not found at $binaryPath")
            return
        }

        // Ensure the binary is executable
        if (!binaryFile.canExecute()) {
            Log.w(TAG, "Binary at $binaryPath is not executable. Attempting to fix...")
            binaryFile.setExecutable(true)
        }

        scope.launch {
            try {
                Log.i(TAG, "Starting Shadowsocks daemon: $binaryPath")
                
                val cmd = listOf(
                    binaryPath,
                    "-s", "$SERVER_HOST:$SERVER_PORT",
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
                Log.i(TAG, "Shadowsocks process started successfully")

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
