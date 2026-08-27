package com.eliteprime.app.data.remote.proxy

import android.content.Context
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.domain.model.ProxyConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceRepository: PreferenceRepository,
    private val configRepository: ConfigRepository,
    private val secureStreamingManager: SecureStreamingManager
) {
    private val TAG = "NetworkManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun init() {
        Log.i(TAG, "Initializing NetworkManager (Proxy Effective State Controller)")
        combine(
            configRepository.isByeDpiAvailable,
            preferenceRepository.isSecureConnectionEnabled
        ) { isAvailable, isUserEnabled ->
            val effectiveEnabled = isAvailable && isUserEnabled
            val logMsg = "ByeDPI check: remote=$isAvailable, user=$isUserEnabled, effective=$effectiveEnabled"
            Log.i(TAG, logMsg)
            FirebaseCrashlytics.getInstance().log(logMsg)
            effectiveEnabled
        }
        .onEach { effectiveEnabled ->
            Log.i(TAG, "Applying Proxy Effective State: $effectiveEnabled")
            ProxyConfig.isEnabled = effectiveEnabled
            if (effectiveEnabled) {
                secureStreamingManager.startProxy()
            } else {
                secureStreamingManager.stopProxy()
            }
        }
        .launchIn(scope)
    }

    fun onConfigUpdated() {
        // Effective state is already handled by the Flow combination in init()
        // But if we want to force a restart when config specifically changes:
        if (ProxyConfig.isEnabled) {
            secureStreamingManager.restartProxy()
        }
    }
}
