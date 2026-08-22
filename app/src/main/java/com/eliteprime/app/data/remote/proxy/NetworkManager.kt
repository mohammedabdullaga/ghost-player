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
                    shadowsocksManager.stopProxy()
                    secureTunnelEngine.stop()
                }
            }
            .launchIn(scope)
    }

    fun onConfigUpdated() {
        if (ProxyConfig.isEnabled) {
            if (ProxyConfig.USE_SHADOWSOCKS) {
                shadowsocksManager.restartProxy(context)
            }
        }
    }
}
