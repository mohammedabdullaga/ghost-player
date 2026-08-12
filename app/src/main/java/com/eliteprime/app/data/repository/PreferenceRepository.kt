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

    private val _subtitleStyle = MutableStateFlow(loadSubtitleStyle())
    val subtitleStyle: StateFlow<PlayerSubtitleStyle> = _subtitleStyle

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

    fun applySavedLocale() {
        _language.value = getCurrentLanguage()
    }
}
