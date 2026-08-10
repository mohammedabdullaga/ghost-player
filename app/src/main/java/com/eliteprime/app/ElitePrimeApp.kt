package com.eliteprime.app

import android.app.Application
import com.eliteprime.app.data.repository.PreferenceRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ElitePrimeApp : Application() {
    @Inject lateinit var preferenceRepository: PreferenceRepository

    override fun onCreate() {
        super.onCreate()
        preferenceRepository.applySavedLocale()
    }
}