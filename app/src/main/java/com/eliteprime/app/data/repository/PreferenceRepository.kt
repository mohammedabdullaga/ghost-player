package com.eliteprime.app.data.repository

import android.content.Context
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
    private val _language = MutableStateFlow(getCurrentLanguage())
    val language: StateFlow<String> = _language

    private fun getCurrentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            return locales.get(0)?.language ?: "en"
        }
        // Fallback to device locale or "en"
        return Locale.getDefault().language.let { if (it == "ar") "ar" else "en" }
    }

    fun setLanguage(lang: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(appLocale)
        _language.value = lang
    }

    fun applySavedLocale() {
        // This is handled automatically by AppCompat due to autoStoreLocales=true in Manifest
        // but we update our flow to match reality
        _language.value = getCurrentLanguage()
    }
}
