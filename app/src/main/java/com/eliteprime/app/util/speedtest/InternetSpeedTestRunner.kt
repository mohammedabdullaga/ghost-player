package com.eliteprime.app.util.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.util.concurrent.TimeUnit

enum class InternetSpeedTestTransport {
    WIFI,
    ETHERNET,
    CELLULAR,
    OTHER
}

data class InternetSpeedTestSnapshot(
    val megabitsPerSecond: Double,
    val recommendedMaxVideoHeight: Int?,
    val measuredAtMs: Long,
    val transport: InternetSpeedTestTransport
)

sealed interface InternetSpeedTestResult {
    data class Success(val snapshot: InternetSpeedTestSnapshot) : InternetSpeedTestResult
    data class Error(val message: String) : InternetSpeedTestResult
}

@Singleton
class InternetSpeedTestRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun run(): InternetSpeedTestResult = withContext(Dispatchers.IO) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@withContext InternetSpeedTestResult.Error("Network service unavailable")
        val network = connectivityManager.activeNetwork
            ?: return@withContext InternetSpeedTestResult.Error("No active network connection")
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return@withContext InternetSpeedTestResult.Error("Unable to inspect active network")

        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> InternetSpeedTestTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> InternetSpeedTestTransport.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> InternetSpeedTestTransport.CELLULAR
            else -> InternetSpeedTestTransport.OTHER
        }
        
        val measuredAtMs = System.currentTimeMillis()

        try {
            val megabitsPerSecond = measureMegabitsPerSecond()
            InternetSpeedTestResult.Success(
                InternetSpeedTestSnapshot(
                    megabitsPerSecond = megabitsPerSecond,
                    recommendedMaxVideoHeight = recommendMaxVideoHeight(megabitsPerSecond),
                    measuredAtMs = measuredAtMs,
                    transport = transport
                )
            )
        } catch (e: Exception) {
            InternetSpeedTestResult.Error(e.message ?: "Speed test failed")
        }
    }

    private fun measureMegabitsPerSecond(bytesToDownload: Long = 8_000_000L): Double {
        val request = Request.Builder()
            .url("https://speed.cloudflare.com/__down?bytes=$bytesToDownload&seed=${SystemClock.elapsedRealtime()}")
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .build()
            
        val startedAtNs = SystemClock.elapsedRealtimeNanos()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("No data received")
            
            var totalRead = 0L
            body.source().use { source ->
                val sink = Buffer()
                while (true) {
                    val read = source.read(sink, 16384)
                    if (read == -1L) break
                    totalRead += read
                    sink.clear()
                }
            }
            
            if (totalRead <= 0L) throw Exception("No data read")
            
            val elapsedSeconds = (SystemClock.elapsedRealtimeNanos() - startedAtNs) / 1_000_000_000.0
            return ((totalRead * 8.0) / elapsedSeconds) / 1_000_000.0
        }
    }

    private fun recommendMaxVideoHeight(megabitsPerSecond: Double): Int? {
        return when {
            megabitsPerSecond < 6.0 -> 480
            megabitsPerSecond < 12.0 -> 720
            megabitsPerSecond < 25.0 -> 1080
            megabitsPerSecond < 40.0 -> 2160
            else -> null
        }
    }
}
