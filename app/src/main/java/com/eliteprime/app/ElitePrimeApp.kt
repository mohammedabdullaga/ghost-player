package com.eliteprime.app

import android.app.Application
import com.eliteprime.app.data.remote.proxy.NetworkManager
import com.eliteprime.app.data.repository.PreferenceRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ElitePrimeApp : Application() {
    @Inject lateinit var preferenceRepository: PreferenceRepository
    @Inject lateinit var networkManager: NetworkManager

    override fun onCreate() {
        super.onCreate()
        preferenceRepository.applySavedLocale()
        networkManager.init()
    }
}