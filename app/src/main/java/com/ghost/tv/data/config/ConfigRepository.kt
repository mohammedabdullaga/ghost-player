package com.ghost.tv.data.config

import android.content.Context
import android.util.Log
import com.ghost.tv.BuildConfig
import com.ghost.tv.domain.model.AppUpdateConfig
import com.ghost.tv.domain.model.ConfigResponse
import com.ghost.tv.domain.model.HostEntry
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

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val TAG = "ConfigRepository"
    private var cachedConfig: ConfigResponse? = null
    private val CONFIG_ENDPOINT = "https://api.dropremax.site/api/config"

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
    private val _isByeDpiAvailable = MutableStateFlow(true)
    val isByeDpiAvailable = _isByeDpiAvailable.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AppUpdateConfig?>(null)
    val availableUpdate: StateFlow<AppUpdateConfig?> = _availableUpdate.asStateFlow()

    companion object {
        val DEFAULT_HOSTS = listOf(
            HostEntry(url = "http://ghost-con.my", priority = 1, status = "active"),
            HostEntry(url = "https://ghost-con.my", priority = 2, status = "active"),
            HostEntry(url = "http://findmyghostin-my.online", priority = 3, status = "active"),
            HostEntry(url = "https://findmyghostin-my.online", priority = 4, status = "active")
        )
    }

    init {
        Log.d(TAG, "ConfigRepository initialized (session-only)")
        setupRemoteConfig()
    }

    private fun setupRemoteConfig() {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf("byedpi_available" to true))
        
        // Immediately activate last known values from disk
        remoteConfig.activate().addOnCompleteListener { 
            val available = remoteConfig.getBoolean("byedpi_available")
            Log.i(TAG, "Remote Config activated (cached values). byedpi_available=$available")
            _isByeDpiAvailable.value = available
        }

        Log.d(TAG, "Firebase Remote Config initialized with default byedpi_available=true")
        refreshRemoteConfig()
    }

    fun refreshRemoteConfig() {
        Log.d(TAG, "refreshRemoteConfig() called")
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    val available = remoteConfig.getBoolean("byedpi_available")
                    Log.i(TAG, "Remote config fetch success. Updated: $updated, byedpi_available: $available")
                    _isByeDpiAvailable.value = available
                } else {
                    val exception = task.exception
                    Log.e(TAG, "Remote config fetch failed: ${exception?.message}", exception)
                }
            }
    }

    suspend fun fetchConfig(): ConfigResponse? = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchConfig() - Current remote byedpi_available state: ${remoteConfig.getBoolean("byedpi_available")}")
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
                        checkForAppUpdate(config)
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
        val config = cachedConfig ?: return DEFAULT_HOSTS
        val remoteHosts = config.hosts
            .filter { (it.status.isBlank() || it.status.equals("active", ignoreCase = true)) && it.url.isNotBlank() }
            .sortedBy { it.priority }

        if (remoteHosts.isEmpty()) {
            return DEFAULT_HOSTS
        }

        val remoteUrls = remoteHosts.map { normalizeUrl(it.url) }.toSet()
        val missingDefaults = DEFAULT_HOSTS.filter { normalizeUrl(it.url) !in remoteUrls }
        return remoteHosts + missingDefaults
    }

    private fun normalizeUrl(url: String): String {
        return url.trim().lowercase().removeSuffix("/")
    }

    fun getProxyHosts(): List<String> {
        val hosts = mutableListOf<String>()
        val config = cachedConfig
        if (config != null) {
            config.proxy_host?.takeIf { it.isNotBlank() }?.let { hosts.add(it) }
            hosts.addAll(config.proxy_hosts.filter { it.isNotBlank() })
        }
        if (hosts.isEmpty()) {
            hosts.add("ghost-con.my")
            hosts.add("findmyghostin-my.online")
        }
        return hosts.distinct()
    }

    fun getTmdbApiKey(): String? {
        return cachedConfig?.tmdb_api_key?.takeIf { it.isNotBlank() }
    }

    private var isDismissedForSession = false

    fun dismissUpdateForSession() {
        Log.i(TAG, "Update dismissed for current session")
        isDismissedForSession = true
        _availableUpdate.value = null
    }

    fun checkForAppUpdate(config: ConfigResponse? = cachedConfig): AppUpdateConfig? {
        val update = config?.update ?: return null
        val currentVersionCode = BuildConfig.VERSION_CODE
        val isForced = update.force_update || currentVersionCode < update.min_supported_version_code

        return if (update.resolvedVersionCode > currentVersionCode && update.resolvedApkUrl.isNotBlank()) {
            Log.i(TAG, "New app update available: v${update.resolvedVersionName} (code ${update.resolvedVersionCode}) vs current code $currentVersionCode, isForced=$isForced")
            if (!isDismissedForSession || isForced) {
                _availableUpdate.value = update
            }
            update
        } else {
            Log.d(TAG, "No update available. Current version code $currentVersionCode is up to date (server code ${update.resolvedVersionCode}).")
            _availableUpdate.value = null
            null
        }
    }
}
