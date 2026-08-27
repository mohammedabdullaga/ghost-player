package com.eliteprime.app.data.remote.proxy

import android.util.Log
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStreamingManager @Inject constructor() {
    private val TAG = "SecureStreamingManager"
    private var proxyJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        init {
            try {
                System.loadLibrary("securestreaming")
            } catch (e: Exception) {
                Log.e("SecureStreaming", "Failed to load native library", e)
            }
        }
    }

    /**
     * Starts the secure streaming engine with IPTV-optimized settings.
     */
    fun startProxy() {
        if (proxyJob != null && proxyJob?.isActive == true) {
            Log.d(TAG, "Secure Streaming engine is already running.")
            return
        }

        // IPTV Optimized Arguments:
        // -i 127.0.0.1 -p 1080 (Local listener)
        // -b 65536 (64KB buffer for high bitrate streams)
        // -d1 (Disorder at pos 1)
        // -s1 (Split at pos 1)
        // -Kh,t,u (Desync HTTP, HTTPS, and UDP)
        // --dns 8.8.8.8,1.1.1.1
        // -An (Auto none - just apply the desync)
        val args = arrayOf(
            "securestreaming",
            "-i", "127.0.0.1",
            "-p", "1080",
            "-b", "524288", // Increased to 512KB for massive catalog transfers
            "-d1",
            "-s1",
            "-Kh,t,u",
            "--dns", "8.8.8.8,1.1.1.1",
            "-An",
            "-x", "0" // Silence verbose logs to improve performance
        )

        proxyJob = scope.launch {
            try {
                Log.i(TAG, "Starting Secure Streaming engine with args: ${args.joinToString(" ")}")
                val result = jniStartProxy(args)
                Log.i(TAG, "Secure Streaming engine stopped with code: $result")
            } catch (e: Exception) {
                Log.e(TAG, "Error running Secure Streaming engine", e)
            }
        }
    }

    fun stopProxy() {
        Log.i(TAG, "Stopping Secure Streaming engine...")
        jniStopProxy()
        
        // Wait briefly for the JNI thread to exit, then force close if needed
        scope.launch {
            delay(1000)
            if (proxyJob?.isActive == true) {
                Log.w(TAG, "Secure Streaming engine didn't stop gracefully, forcing close.")
                jniForceClose()
                proxyJob?.cancel()
            }
            proxyJob = null
        }
    }

    fun restartProxy() {
        scope.launch {
            stopProxy()
            delay(500)
            startProxy()
        }
    }

    fun isRunning(): Boolean = proxyJob?.isActive == true

    private external fun jniStartProxy(args: Array<String>): Int
    private external fun jniStopProxy(): Int
    private external fun jniForceClose(): Int
}
