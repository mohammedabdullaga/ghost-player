package com.eliteprime.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigResponse(
    val hosts: List<HostEntry> = emptyList(),
    val tmdb_api_key: String = "",
    val proxy_host: String? = null,
    val proxy_hosts: List<String> = emptyList(),
    val update: AppUpdateConfig? = null
)

@Serializable
data class AppUpdateConfig(
    val version_code: Int = 0,
    val latest_version_code: Int = 0,
    val version_name: String = "",
    val latest_version_name: String = "",
    val download_url: String = "",
    val apk_url: String = "",
    val force_update: Boolean = false,
    val release_notes: String = "",
    val min_supported_version_code: Int = 0,
    val sha256: String? = null
) {
    val resolvedVersionCode: Int
        get() = if (version_code > 0) version_code else latest_version_code

    val resolvedVersionName: String
        get() = version_name.ifBlank { latest_version_name }

    val resolvedApkUrl: String
        get() = download_url.ifBlank { apk_url }
}

@Serializable
data class HostEntry(
    val url: String,
    val priority: Int = 0,
    val status: String = "active"
)
