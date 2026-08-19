package com.eliteprime.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.eliteprime.app.player.PlayerSubtitleStyle
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

    private val _isSecureConnectionEnabled = MutableStateFlow(prefs.getBoolean("secure_connection_enabled", false))
    val isSecureConnectionEnabled: StateFlow<Boolean> = _isSecureConnectionEnabled

    private val _autoPlayNextEpisode = MutableStateFlow(prefs.getBoolean("auto_play_next_episode", true))
    val autoPlayNextEpisode: StateFlow<Boolean> = _autoPlayNextEpisode

    private val _zapAutoRevert = MutableStateFlow(prefs.getBoolean("zap_auto_revert", false))
    val zapAutoRevert: StateFlow<Boolean> = _zapAutoRevert

    private val _preventStandby = MutableStateFlow(prefs.getBoolean("prevent_standby", true))
    val preventStandby: StateFlow<Boolean> = _preventStandby

    private val _liveBufferSize = MutableStateFlow(prefs.getInt("live_buffer_size", 0)) // 0 = Auto/Default
    val liveBufferSize: StateFlow<Int> = _liveBufferSize

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

    private fun getCurrentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            return locales.get(0)?.language ?: "en"
        }
        return Locale.getDefault().language.let { if (it == "ar") "ar" else "en" }
    }

    fun setLanguage(lang: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(appLocale)
        _language.value = lang
    }

    fun setDownloadTreeUri(uri: String?) {
        prefs.edit().putString("download_tree_uri", uri).apply()
        _downloadTreeUri.value = uri
    }

    fun setSecureConnectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("secure_connection_enabled", enabled).apply()
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
        prefs.edit().putBoolean("prevent_standby", enabled).apply()
        _preventStandby.value = enabled
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

    fun applySavedLocale() {
        _language.value = getCurrentLanguage()
    }
}
