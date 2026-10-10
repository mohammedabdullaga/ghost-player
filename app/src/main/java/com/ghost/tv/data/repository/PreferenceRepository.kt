package com.ghost.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ghost.tv.player.PlayerSubtitleStyle
import com.ghost.tv.util.DeviceRenderProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(getCurrentLanguage())
    val language: StateFlow<String> = _language

    private val _downloadTreeUri = MutableStateFlow(prefs.getString("download_tree_uri", null))
    val downloadTreeUri: StateFlow<String?> = _downloadTreeUri

    private val _isSecureConnectionEnabled = MutableStateFlow(prefs.getBoolean("byedpi_user_enabled", prefs.getBoolean("secure_connection_enabled", false)))
    val isSecureConnectionEnabled: StateFlow<Boolean> = _isSecureConnectionEnabled

    private val _autoPlayNextEpisode = MutableStateFlow(prefs.getBoolean("auto_play_next_episode", true))
    val autoPlayNextEpisode: StateFlow<Boolean> = _autoPlayNextEpisode

    private val _zapAutoRevert = MutableStateFlow(prefs.getBoolean("zap_auto_revert", false))
    val zapAutoRevert: StateFlow<Boolean> = _zapAutoRevert

    private val _preventStandby = MutableStateFlow(prefs.getBoolean("prevent_standby", true))
    val preventStandby: StateFlow<Boolean> = _preventStandby

    private val _liveBufferSize = MutableStateFlow(prefs.getInt("live_buffer_size", 0)) // 0 = Auto/Default
    val liveBufferSize: StateFlow<Int> = _liveBufferSize

    private val _isLiteModeEnabled = MutableStateFlow(prefs.getBoolean("lite_mode_enabled", true))
    val isLiteModeEnabled: StateFlow<Boolean> = _isLiteModeEnabled

    val isAppModeConfigured: Boolean
        get() = prefs.contains("lite_mode_enabled")

    private val _decoderMode = MutableStateFlow(prefs.getString("decoder_mode", "AUTO") ?: "AUTO")
    val decoderMode: StateFlow<String> = _decoderMode

    private val _subtitleStyle = MutableStateFlow(loadSubtitleStyle())
    val subtitleStyle: StateFlow<PlayerSubtitleStyle> = _subtitleStyle

    private val _playerEngineMode = MutableStateFlow(prefs.getString("player_engine_mode", "EXO") ?: "EXO")
    val playerEngineMode: StateFlow<String> = _playerEngineMode

    private fun loadSubtitleStyle(): PlayerSubtitleStyle {
        return PlayerSubtitleStyle(
            textScale = prefs.getFloat("subtitle_text_scale", 1.0f),
            foregroundColorArgb = prefs.getInt("subtitle_text_color", 0xFFFFFFFF.toInt()),
            backgroundColorArgb = prefs.getInt("subtitle_background_color", 0x00000000)
        )
    }

    fun setSubtitleStyle(style: PlayerSubtitleStyle) {
        prefs.edit()
            .putFloat("subtitle_text_scale", style.textScale)
            .putInt("subtitle_text_color", style.foregroundColorArgb)
            .putInt("subtitle_background_color", style.backgroundColorArgb)
            .apply()
        _subtitleStyle.value = style
    }

    fun getLastSyncTimestamp(): Long = prefs.getLong("last_sync_timestamp", 0L)

    fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }

    init {
        // Force Lite Mode as default for fresh installs
        if (!prefs.contains("lite_mode_enabled")) {
            prefs.edit().putBoolean("lite_mode_enabled", true).apply()
        }
    }

    private fun getCurrentLanguage(): String {
        // 1. Check SharedPreferences first (Manual user override from Settings)
        val saved = prefs.getString("app_language", null)
        if (!saved.isNullOrBlank()) return saved

        // 2. Respect Device System Language
        val deviceLang = Locale.getDefault().language
        if (!deviceLang.isNullOrBlank()) {
            return deviceLang
        }

        // 3. Fallback to Arabic as default
        return "ar"
    }

    fun setLanguage(lang: String) {
        // Save manually to SharedPreferences
        prefs.edit().putString("app_language", lang).apply()
        
        // Update System/AppCompat
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(appLocale)
        
        // Update StateFlow
        _language.value = lang
    }

    fun setDownloadTreeUri(uri: String?) {
        prefs.edit().putString("download_tree_uri", uri).apply()
        _downloadTreeUri.value = uri
    }

    fun setSecureConnectionEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("byedpi_user_enabled", enabled)
            .putBoolean("secure_connection_enabled", enabled)
            .apply()
        _isSecureConnectionEnabled.value = enabled
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        prefs.edit().putBoolean("auto_play_next_episode", enabled).apply()
        _autoPlayNextEpisode.value = enabled
    }

    fun setZapAutoRevert(enabled: Boolean) {
        prefs.edit().putBoolean("zap_auto_revert", enabled).apply()
        _zapAutoRevert.value = enabled
    }

    fun setPreventStandby(enabled: Boolean) {
        prefs.edit().putBoolean("prevent_standby", enabled).commit()
        _preventStandby.value = enabled
    }

    fun setLiteModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("lite_mode_enabled", enabled).commit()
        _isLiteModeEnabled.value = enabled
        DeviceRenderProfile.setLiteMode(enabled)
    }

    fun setLiveBufferSize(sizeMs: Int) {
        prefs.edit().putInt("live_buffer_size", sizeMs).apply()
        _liveBufferSize.value = sizeMs
    }

    fun setDecoderMode(mode: String) {
        prefs.edit().putString("decoder_mode", mode).apply()
        _decoderMode.value = mode
    }

    fun setPlayerEngineMode(mode: String) {
        prefs.edit().putString("player_engine_mode", mode).apply()
        _playerEngineMode.value = mode
    }

    fun getLastUpdateCheckTimestamp(): Long = prefs.getLong("last_update_check_timestamp", 0L)

    fun setLastUpdateCheckTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_update_check_timestamp", timestamp).apply()
    }

    fun getCachedGenres(type: String): String? = prefs.getString("cached_genres_\$type", null)

    fun setCachedGenres(type: String, json: String) {
        prefs.edit().putString("cached_genres_\$type", json).apply()
    }

    fun applySavedLocale() {
        val currentLang = getCurrentLanguage()
        _language.value = currentLang
        
        // Sync AppCompatDelegate with our manual preference if they differ or if system is empty
        val systemLocales = AppCompatDelegate.getApplicationLocales()
        if (systemLocales.isEmpty || systemLocales.get(0)?.language != currentLang) {
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(currentLang)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }
}
