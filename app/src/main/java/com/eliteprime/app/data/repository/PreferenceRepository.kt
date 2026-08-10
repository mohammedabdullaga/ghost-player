package com.eliteprime.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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

    fun applySavedLocale() {
        _language.value = getCurrentLanguage()
    }
}
