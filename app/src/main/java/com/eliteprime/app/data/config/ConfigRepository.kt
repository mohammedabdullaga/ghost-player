package com.eliteprime.app.data.config

import android.content.Context
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
    private var cachedConfig: ConfigResponse? = null
    private val CONFIG_ENDPOINT = "https://api.arenaliveapp.top/api/config"

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
}
