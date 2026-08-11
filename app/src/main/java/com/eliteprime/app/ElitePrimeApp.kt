package com.eliteprime.app

import android.app.Application
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.remote.proxy.NetworkManager
import com.eliteprime.app.data.repository.PreferenceRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ElitePrimeApp : Application() {
    @Inject lateinit var preferenceRepository: PreferenceRepository
    @Inject lateinit var networkManager: NetworkManager
    @Inject lateinit var configRepository: ConfigRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        preferenceRepository.applySavedLocale()
        
        // Ensure config is fetched as early as possible
        appScope.launch {
            configRepository.fetchConfig()
            networkManager.init()
        }
    }
}
