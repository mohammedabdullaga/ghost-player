package com.eliteprime.app.data.config

import android.content.Context
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
    private var cachedConfig: ConfigResponse? = null
    // Obfuscated https://api.arenaliveapp.top/api/config
    private val CONFIG_DATA = byteArrayOf(
        0x21, 0x06, 0x11, 0x1e, 0x1c, 0x76, 0x6e, 0x6e, 0x10, 0x17, 0x0c, 0x2e, 0x00, 0x17, 0x01, 0x1a, 
        0x01, 0x0b, 0x17, 0x05, 0x17, 0x16, 0x11, 0x2c, 0x10, 0x10, 0x11, 0x6f, 0x15, 0x06, 0x0d, 0x2e, 
        0x00, 0x14, 0x0c, 0x6e, 0x02, 0x01, 0x0a, 0x00, 0x08, 0x02
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
        val request = Request.Builder()
            .url(CONFIG_ENDPOINT)
            .get()
            .header("Accept", "application/json")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val config = json.decodeFromString<ConfigResponse>(body)
                cachedConfig = config
                // Save to disk
                prefs.edit().putString("cached_config", body).apply()
                return@withContext config
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
