package com.eliteprime.app.domain.model

/**
 * High-speed in-memory storage for proxy configuration.
 */
object ProxyConfig {
    @Volatile
    var isEnabled: Boolean = false
    
    /**
     * Feature flag to toggle between the old HTTPS/GOST proxy and the new Shadowsocks local daemon.
     */
    const val USE_SHADOWSOCKS = true
}
