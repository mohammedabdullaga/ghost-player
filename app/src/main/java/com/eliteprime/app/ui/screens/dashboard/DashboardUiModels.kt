package com.eliteprime.app.ui.screens.dashboard

import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.eliteprime.app.data.local.entity.Series

data class DashboardUiState(
    val isLoading: Boolean = true,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val providerHealth: DashboardProviderHealth = DashboardProviderHealth(),
    val continueWatching: List<PlaybackHistory> = emptyList(),
    val trendingMovies: List<Movie> = emptyList(),
    val trendingSeries: List<Series> = emptyList()
)

data class DashboardProviderHealth(
    val expirationDate: Long? = null,
    val status: String = "",
    val maxConnections: Int = 0
)
