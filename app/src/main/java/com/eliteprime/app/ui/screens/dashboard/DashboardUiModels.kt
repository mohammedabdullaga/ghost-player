package com.eliteprime.app.ui.screens.dashboard

data class DashboardUiState(
    val isLoading: Boolean = true,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val providerHealth: DashboardProviderHealth = DashboardProviderHealth()
)

data class DashboardProviderHealth(
    val expirationDate: Long? = null,
    val status: String = "",
    val maxConnections: Int = 0
)
