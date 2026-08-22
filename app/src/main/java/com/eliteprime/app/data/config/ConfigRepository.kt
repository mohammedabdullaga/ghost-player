package com.eliteprime.app.data.config

import android.content.Context
import android.util.Log
import com.eliteprime.app.domain.model.ConfigResponse
import com.eliteprime.app.domain.model.HostEntry
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
    private val CONFIG_ENDPOINT = "https://api.dropremax.site/api/config"
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
        val configHosts = cachedConfig?.hosts
            ?.filter { it.status.equals("active", ignoreCase = true) }
            ?.sortedBy { it.priority } ?: emptyList()

        if (configHosts.isEmpty()) {
            Log.w(TAG, "No active hosts in config, using hardcoded test fallbacks.")
            return listOf(
                HostEntry(url = "http://ttre.online", priority = 1)
            )
        }
        return configHosts
    }

    fun getProxyHosts(): List<String> {
        val hosts = mutableListOf<String>()
        val config = cachedConfig
        if (config != null) {
            config.proxy_host?.takeIf { it.isNotBlank() }?.let { hosts.add(it) }
            hosts.addAll(config.proxy_hosts.filter { it.isNotBlank() })
        }
        if (hosts.isEmpty()) {
            // Default hardcoded fallback proxy
            hosts.add("cdn.sharkbh.online")
        }
        return hosts.distinct()
    }

    fun getTmdbApiKey(): String? {
        return cachedConfig?.tmdb_api_key?.takeIf { it.isNotBlank() }
    }
}
