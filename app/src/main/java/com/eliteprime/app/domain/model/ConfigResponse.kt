package com.eliteprime.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigResponse(
    val hosts: List<HostEntry> = emptyList(),
    val tmdb_api_key: String = "",
    val proxy_host: String? = null,
    val proxy_hosts: List<String> = emptyList()
)

@Serializable
data class HostEntry(
    val url: String,
    val priority: Int = 0,
    val status: String = "active"
)
