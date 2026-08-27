package com.eliteprime.app.domain.model

/**
 * High-speed in-memory storage for proxy configuration.
 */
object ProxyConfig {
    @Volatile
    var isEnabled: Boolean = false
}
