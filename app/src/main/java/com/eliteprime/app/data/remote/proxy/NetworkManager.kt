package com.eliteprime.app.data.remote.proxy

import android.content.Context
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.domain.model.ProxyConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceRepository: PreferenceRepository,
    private val secureTunnelEngine: SecureTunnelEngine,
    private val shadowsocksManager: ShadowsocksManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    suspend fun ensureShadowsocksStarted() {
        shadowsocksManager.ensureProxyRunning(context)
    }

    fun init() {
        preferenceRepository.isSecureConnectionEnabled
            .onEach { isEnabled ->
                ProxyConfig.isEnabled = isEnabled
                if (isEnabled) {
                    if (ProxyConfig.USE_SHADOWSOCKS) {
                        shadowsocksManager.startProxy(context)
                    } else {
                        secureTunnelEngine.start()
                    }
                } else {
                    // Note: When secure connection is turned off for streams/IPTV,
                    // we do not kill shadowsocks if it is needed for config requests,
                    // but if not using shadowsocks or user switches off, we handle it accordingly.
                    if (!ProxyConfig.USE_SHADOWSOCKS) {
                        secureTunnelEngine.stop()
                    }
                }
            }
            .launchIn(scope)
    }

    fun onConfigUpdated() {
        // If config loaded new proxy hosts, restart shadowsocks daemon with the new hosts
        shadowsocksManager.restartProxy(context)
    }
}
