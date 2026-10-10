package com.ghost.tv.ui.tv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ghost.tv.domain.model.DownloadContentType
import com.ghost.tv.player.GlobalPlayerState
import com.ghost.tv.player.PlayerEngine
import com.ghost.tv.ui.screens.dashboard.ContinueWatchingGridScreen
import com.ghost.tv.ui.screens.dashboard.DashboardViewModel
import com.ghost.tv.ui.screens.downloads.DownloadsScreen
import com.ghost.tv.ui.screens.login.LoginScreen
import com.ghost.tv.ui.screens.login.LoginViewModel
import com.ghost.tv.ui.screens.player.PlayerScreen
import com.ghost.tv.ui.screens.player.PlayerViewModel
import com.ghost.tv.ui.tv.screens.*
import com.ghost.tv.ui.tv.screens.sports.SportsScreen
import com.ghost.tv.util.DeviceRenderProfile

/**
 * TV Specific Navigation.
 * Standardized for TV UX using TV Material components.
 */
@Composable
fun TvAppNavigation(
    navController: NavHostController,
    playerEngine: PlayerEngine,
    globalPlayerState: GlobalPlayerState,
    modifier: Modifier = Modifier,
    startDestination: String = "login"
) {
    val isLite = DeviceRenderProfile.isLiteMode()
    val defaultLanding = if (isLite) "hub" else "home"

    val navigateFromHubToLeaf = { leafRoute: String ->
        globalPlayerState.hide()
        playerEngine.stop()
        navController.navigate(leafRoute) {
            popUpTo("hub") { inclusive = false; saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val stopPlayerAndNavigate = { route: String ->
        globalPlayerState.hide()
        playerEngine.stop()
        navController.navigate(route) {
            popUpTo(defaultLanding) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigateToHomeOrHub = {
        globalPlayerState.hide()
        playerEngine.stop()
        navController.navigate(defaultLanding) {
            popUpTo(defaultLanding) { inclusive = false; saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable("login") {
            TvLoginScreen(onLoginSuccess = {
                navController.navigate(defaultLanding) {
                    popUpTo("login") { inclusive = true }
                }
            })
        }

        composable("hub") {
            TvLiteHubScreen(
                onNavigateToLive = { navigateFromHubToLeaf("live") },
                onNavigateToMovies = { navigateFromHubToLeaf("movies") },
                onNavigateToSeries = { navigateFromHubToLeaf("series") },
                onNavigateToSports = { navigateFromHubToLeaf("sports") },
                onNavigateToSearch = { navigateFromHubToLeaf("search") },
                onNavigateToSettings = { navigateFromHubToLeaf("settings") }
            )
        }

        composable("sports") {
            SportsScreen(
                onBack = { navController.popBackStack() },
                onPlayChannel = { channel ->
                    navController.navigate("player/${channel.id}/live/${Uri.encode(channel.name)}?from=sports")
                }
            )
        }

        composable("home") {
            // Navigation to other sections (Live TV, Movies, Series,
            // Downloads, Search, Settings) now happens exclusively via
            // TvMainShell's TvSideNavRail, which wraps this screen from
            // the outside — TvHomeScreen no longer takes or needs those
            // callbacks itself.
            TvHomeScreen(
                onNavigateToTrending = { navController.navigate("trending") },
                onNavigateToContinueWatching = { navController.navigate("continue_watching") },
                onMovieClick = { movie -> navController.navigate("movie_details/${movie.id}") },
                onSeriesClick = { series -> navController.navigate("series_details/${series.id}") },
                onResumeHistoryItem = { history ->
                    when (history.type) {
                        "movie" -> navController.navigate("movie_details/${history.streamId}")
                        "series_episode", "series" -> {
                            val seriesId = history.seriesId ?: history.streamId
                            navController.navigate("series_details/$seriesId")
                        }
                    }
                }
            )
        }

        composable("trending") {
            TvTrendingScreen(
                onBack = { navController.popBackStack() },
                onMovieClick = { movie -> navController.navigate("movie_details/${movie.id}") },
                onSeriesClick = { series -> navController.navigate("series_details/${series.id}") }
            )
        }

        composable("live") {
            TvLiveTvScreen(
                onBack = { navController.popBackStack() },
                onPlayChannel = { channel ->
                    navController.navigate("player/${channel.id}/live/${Uri.encode(channel.name)}?from=live_guide")
                }
            )
        }

        composable("movies") {
            TvMovieScreen(
                onBack = { navController.popBackStack() },
                onMovieClick = { movie ->
                    navController.navigate("movie_details/${movie.id}")
                }
            )
        }

        composable("series") {
            TvSeriesScreen(
                onBack = { navController.popBackStack() },
                onSeriesClick = { series ->
                    navController.navigate("series_details/${series.id}")
                }
            )
        }

        composable(
            "movie_details/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) {
            TvMovieDetailsScreen(
                onBack = { navController.popBackStack() },
                onPlay = { m ->
                    navController.navigate("player/${m.id}/movie/${Uri.encode(m.name)}")
                },
                onMovieClick = { m ->
                    navController.navigate("movie_details/${m.id}")
                }
            )
        }

        composable(
            "series_details/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) {
            TvSeriesDetailsScreen(
                onBack = { navController.popBackStack() },
                onPlayEpisode = { episode, title ->
                    navController.navigate("player/${episode.id}/series/${Uri.encode(title)}")
                },
                onSeriesClick = { series ->
                    navController.navigate("series_details/${series.id}")
                }
            )
        }

        composable("search") {
            TvSearchScreen(
                onBack = { navController.popBackStack() },
                onMovieClick = { movie -> navController.navigate("movie_details/${movie.id}") },
                onSeriesClick = { series -> navController.navigate("series_details/${series.id}") },
                onChannelClick = { channel -> navController.navigate("player/${channel.id}/live/${Uri.encode(channel.name)}") }
            )
        }

        composable("settings") {
            TvSettingsScreen(
                onBack = navigateToHomeOrHub,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("downloads") {
            TvDownloadsScreen(
                onBack = { navController.popBackStack() },
                onPlayDownload = { download ->
                    val path = download.outputUri ?: download.outputDisplayPath ?: ""
                    if (path.isNotEmpty()) {
                        val uri = if (path.contains("://")) path else "file://$path"
                        val pType = if (download.contentType == DownloadContentType.MOVIE) "movie" else "series"
                        navController.navigate("player/${download.contentId}/$pType/${Uri.encode(download.contentName)}?url=${Uri.encode(uri)}")
                    }
                }
            )
        }

        composable("continue_watching") {
            TvHistoryGridScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMovieDetails = { id -> navController.navigate("movie_details/$id") },
                onNavigateToSeriesDetails = { id -> navController.navigate("series_details/$id") }
            )
        }

        composable(
            "player/{id}/{type}/{title}?url={url}&from={from}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("type") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("from") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            val playerViewModel: PlayerViewModel = hiltViewModel()
            PlayerScreen(
                viewModel = playerViewModel,
                playerEngine = playerViewModel.playerEngine,
                onBack = { navController.popBackStack() }
            )
        }
    }
}