package com.ghost.tv.domain.model

/**
 * High-speed in-memory storage for proxy configuration.
 */
object ProxyConfig {
    @Volatile
    var isEnabled: Boolean = false
}
