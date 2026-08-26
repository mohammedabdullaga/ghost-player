package com.eliteprime.app.data.config

import android.content.Context
import android.util.Log
import com.eliteprime.app.domain.model.ConfigResponse
import com.eliteprime.app.domain.model.HostEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.google.firebase.crashlytics.FirebaseCrashlytics
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

    init {
        // Session-based configuration: We do NOT load from disk on startup
        // to ensure the app fetches fresh hosts every time it is opened.
        Log.d(TAG, "ConfigRepository initialized (session-only)")
    }

    suspend fun fetchConfig(): ConfigResponse? = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = 3
        
        while (attempts < maxAttempts) {
            Log.i(TAG, "Fetching remote config (Attempt ${attempts + 1}/$maxAttempts)")
            val request = Request.Builder()
                .url(CONFIG_ENDPOINT)
                .get()
                .header("Accept", "application/json")
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val config = json.decodeFromString<ConfigResponse>(body)
                        cachedConfig = config
                        Log.i(TAG, "Config successfully fetched and parsed")
                        return@withContext config
                    } else {
                        Log.e(TAG, "Failed to fetch config. Code: $code, Body: $body")
                        FirebaseCrashlytics.getInstance().log("Config fetch failed: HTTP $code")
                        FirebaseCrashlytics.getInstance().setCustomKey("config_error_code", code)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching config: ${e.message}", e)
                if (attempts == maxAttempts - 1) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
            attempts++
            if (attempts < maxAttempts) delay(1000)
        }
        cachedConfig
    }

    fun getActiveHosts(): List<HostEntry> {
        val configHosts = cachedConfig?.hosts
            ?.filter { it.status.equals("active", ignoreCase = true) }
            ?.sortedBy { it.priority } ?: emptyList()

        if (configHosts.isEmpty()) {
            Log.w(TAG, "No active hosts in config.")
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
        return hosts.distinct()
    }

    fun getTmdbApiKey(): String? {
        return cachedConfig?.tmdb_api_key?.takeIf { it.isNotBlank() }
    }
}
