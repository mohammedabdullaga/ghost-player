package com.eliteprime.app.data.config

import android.content.Context
import android.util.Log
import com.eliteprime.app.domain.model.ConfigResponse
import com.eliteprime.app.domain.model.HostEntry
import com.eliteprime.app.util.StringObfuscator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val TAG = "ConfigRepository"
    private var cachedConfig: ConfigResponse? = null
    // Obfuscated https://api.arenaliveapp.top/api/config
    private val CONFIG_DATA = byteArrayOf(
        0x29.toByte(), 0x06.toByte(), 0x11.toByte(), 0x1E.toByte(), 0x12.toByte(), 0x76.toByte(), 0x46.toByte(), 0x59.toByte(), 
        0x04.toByte(), 0x23.toByte(), 0x0C.toByte(), 0x4D.toByte(), 0x14.toByte(), 0x00.toByte(), 0x00.toByte(), 0x25.toByte(), 
        0x04.toByte(), 0x15.toByte(), 0x5B.toByte(), 0x46.toByte(), 0x57.toByte(), 0x57.toByte(), 0x31.toByte(), 0x02.toByte(), 
        0x4B.toByte(), 0x1A.toByte(), 0x0E.toByte(), 0x3C.toByte(), 0x46.toByte(), 0x17.toByte(), 0x15.toByte(), 0x3A.toByte(), 
        0x4A.toByte(), 0x00.toByte(), 0x1A.toByte(), 0x1C.toByte(), 0x03.toByte(), 0x22.toByte(), 0x02.toByte()
    )
    private val CONFIG_ENDPOINT = StringObfuscator.decode(CONFIG_DATA)
    private val prefs = context.getSharedPreferences("config_prefs", Context.MODE_PRIVATE)

    init {
        // Load from disk on startup
        val saved = prefs.getString("cached_config", null)
        if (saved != null) {
            try {
                cachedConfig = json.decodeFromString<ConfigResponse>(saved)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun fetchConfig(): ConfigResponse? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching config from: $CONFIG_ENDPOINT")
        val request = Request.Builder()
            .url(CONFIG_ENDPOINT)
            .get()
            .header("Accept", "application/json")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            Log.d(TAG, "Config response code: $code")
            
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val config = json.decodeFromString<ConfigResponse>(body)
                cachedConfig = config
                // Save to disk
                prefs.edit().putString("cached_config", body).apply()
                return@withContext config
            } else {
                Log.e(TAG, "Failed to fetch config. Code: $code, Body: $body")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching config: ${e.message}", e)
        }
        cachedConfig
    }

    fun getActiveHosts(): List<HostEntry> {
        return cachedConfig?.hosts
            ?.filter { it.status.equals("active", ignoreCase = true) }
            ?.sortedBy { it.priority } ?: emptyList()
    }

    fun getProxyHosts(): List<String> {
        val config = cachedConfig ?: return emptyList()
        val hosts = mutableListOf<String>()
        config.proxy_host?.takeIf { it.isNotBlank() }?.let { hosts.add(it) }
        hosts.addAll(config.proxy_hosts.filter { it.isNotBlank() })
        return hosts
    }

    fun getTmdbApiKey(): String? {
        return cachedConfig?.tmdb_api_key?.takeIf { it.isNotBlank() }
    }
}
