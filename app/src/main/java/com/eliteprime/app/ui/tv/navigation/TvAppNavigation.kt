package com.eliteprime.app.ui.tv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.eliteprime.app.domain.model.DownloadContentType
import com.eliteprime.app.player.GlobalPlayerState
import com.eliteprime.app.player.PlayerEngine
import com.eliteprime.app.ui.screens.dashboard.ContinueWatchingGridScreen
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.details.MovieDetailsScreen
import com.eliteprime.app.ui.screens.details.SeriesDetailsScreen
import com.eliteprime.app.ui.screens.downloads.DownloadsScreen
import com.eliteprime.app.ui.screens.home.HomeScreen
import com.eliteprime.app.ui.tv.screens.*
import com.eliteprime.app.ui.screens.login.LoginScreen
import com.eliteprime.app.ui.screens.login.LoginViewModel
import com.eliteprime.app.ui.screens.movies.MovieScreen
import com.eliteprime.app.ui.screens.player.PlayerScreen
import com.eliteprime.app.ui.screens.player.PlayerViewModel
import com.eliteprime.app.ui.screens.search.SearchScreen
import com.eliteprime.app.ui.screens.series.SeriesScreen
import com.eliteprime.app.ui.screens.settings.SettingsScreen
import com.eliteprime.app.ui.screens.trending.TrendingScreen

/**
 * TV Specific Navigation.
 * Initially, this reuses the mobile screens but will be swapped for TV-Material screens one-by-one.
 */
@Composable
fun TvAppNavigation(
    navController: NavHostController,
    playerEngine: PlayerEngine,
    globalPlayerState: GlobalPlayerState,
    modifier: Modifier = Modifier,
    startDestination: String = "login"
) {
    val stopPlayerAndNavigate = { route: String ->
        globalPlayerState.hide()
        playerEngine.stop()
        navController.navigate(route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigateToHome = {
        globalPlayerState.hide()
        playerEngine.stop()
        navController.navigate("home") {
            popUpTo("home") { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable("login") {
            LoginScreen(viewModel = hiltViewModel<LoginViewModel>(), onLoginSuccess = {
                navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("home") {
            TvHomeScreen(
                onNavigateToSearch = { stopPlayerAndNavigate("search") },
                onNavigateToSettings = { stopPlayerAndNavigate("settings") },
                onNavigateToLiveTv = { stopPlayerAndNavigate("live") },
                onNavigateToMovies = { stopPlayerAndNavigate("movies") },
                onNavigateToSeries = { stopPlayerAndNavigate("series") },
                onNavigateToTrending = { navController.navigate("trending") },
                onNavigateToDownloads = { stopPlayerAndNavigate("downloads") },
                onNavigateToContinueWatching = { navController.navigate("continue_watching") }
            )
        }
        composable("trending") {
            TrendingScreen(
                onBack = { navController.popBackStack() },
                onMovieClick = { movie -> navController.navigate("movie_details/${movie.id}") },
                onSeriesClick = { series -> navController.navigate("series_details/${series.id}") }
            )
        }
        composable("live") {
            // TARGET 1: New TV-optimized Live TV Screen
            TvLiveTvScreen(
                onBack = navigateToHome,
                onNavigateToSearch = { stopPlayerAndNavigate("search") },
                onNavigateToMovies = { stopPlayerAndNavigate("movies") },
                onNavigateToSeries = { stopPlayerAndNavigate("series") },
                onPlayChannel = { channel ->
                    navController.navigate("player/${channel.id}/live/${Uri.encode(channel.name)}")
                }
            )
        }
        composable("movies") {
            TvMovieScreen(
                onBack = navigateToHome,
                onMovieClick = { movie ->
                    navController.navigate("movie_details/${movie.id}")
                }
            )
        }
        composable("series") {
            TvSeriesScreen(
                onBack = navigateToHome,
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
                }
            )
        }
        composable("search") {
            TvSearchScreen(
                onBack = navigateToHome,
                onMovieClick = { movie -> navController.navigate("movie_details/${movie.id}") },
                onSeriesClick = { series -> navController.navigate("series_details/${series.id}") },
                onChannelClick = { channel -> navController.navigate("player/${channel.id}/live/${Uri.encode(channel.name)}") }
            )
        }
        composable("settings") {
            TvSettingsScreen(
                onBack = navigateToHome,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable("downloads") {
            DownloadsScreen(
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
            ContinueWatchingGridScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMovieDetails = { id -> navController.navigate("movie_details/$id") },
                onNavigateToSeriesDetails = { id -> navController.navigate("series_details/$id") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = navigateToHome,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(
            "player/{id}/{type}/{title}?url={url}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("type") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("url") { 
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
