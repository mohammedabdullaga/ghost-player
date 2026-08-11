package com.eliteprime.app.ui.screens.dashboard

import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.eliteprime.app.data.local.entity.Series

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isTrendingLoading: Boolean = true,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val downloadsCount: Int = 0,
    val completedDownloadsCount: Int = 0,
    val latestDownloadPoster: String? = null,
    val providerHealth: DashboardProviderHealth = DashboardProviderHealth(),
    val continueWatching: List<PlaybackHistory> = emptyList(),
    val trendingMovies: List<TrendingItem<Movie>> = emptyList(),
    val trendingSeries: List<TrendingItem<Series>> = emptyList()
)

data class TrendingItem<T>(
    val tmdbId: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val voteAverage: Double,
    val genreIds: List<Int> = emptyList(),
    val genres: List<String> = emptyList(),
    val localMatch: T? = null // Matched Movie or Series entity
)

data class DashboardProviderHealth(
    val expirationDate: Long? = null,
    val status: String = "",
    val maxConnections: Int = 0
)
