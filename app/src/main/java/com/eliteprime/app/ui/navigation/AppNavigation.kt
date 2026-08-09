package com.eliteprime.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.eliteprime.app.ui.screens.home.HomeScreen
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.live.MobileLiveTvScreen
import com.eliteprime.app.ui.screens.live.LiveViewModel
import com.eliteprime.app.ui.screens.login.LoginScreen
import com.eliteprime.app.ui.screens.login.LoginViewModel
import com.eliteprime.app.ui.screens.movies.MovieScreen
import com.eliteprime.app.ui.screens.movies.MovieViewModel
import com.eliteprime.app.ui.screens.series.SeriesScreen
import com.eliteprime.app.ui.screens.series.SeriesViewModel
import com.eliteprime.app.ui.screens.search.SearchScreen
import com.eliteprime.app.ui.screens.search.SearchViewModel
import com.eliteprime.app.ui.screens.player.PlayerScreen
import com.eliteprime.app.ui.screens.player.PlayerViewModel
import com.eliteprime.app.ui.screens.settings.SettingsScreen
import com.eliteprime.app.ui.screens.settings.SettingsViewModel
import com.eliteprime.app.ui.screens.details.MovieDetailsScreen
import com.eliteprime.app.ui.screens.details.MovieDetailViewModel
import com.eliteprime.app.ui.screens.details.SeriesDetailsScreen
import com.eliteprime.app.ui.screens.details.SeriesDetailsViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = "login"
) {
    val navigateToHome = {
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
            HomeScreen(
                viewModel = hiltViewModel<DashboardViewModel>(),
                onNavigateToSearch = { navController.navigate("search") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToLiveTv = { navController.navigate("live") },
                onNavigateToMovies = { navController.navigate("movies") },
                onNavigateToSeries = { navController.navigate("series") },
                onNavigateToDownloads = { navController.navigate("downloads") }
            )
        }
        composable("live") {
            MobileLiveTvScreen(
                onBack = navigateToHome,
                onNavigateToSearch = { navController.navigate("search") },
                onNavigateToMovies = { navController.navigate("movies") },
                onNavigateToSeries = { navController.navigate("series") },
                onPlayChannel = { channel ->
                    navController.navigate("player/${channel.id}/live/${channel.name}")
                }
            )
        }
        composable("movies") {
            MovieScreen(
                onBack = navigateToHome,
                onNavigateToSearch = { navController.navigate("search") },
                onNavigateToLiveTv = { navController.navigate("live") },
                onNavigateToSeries = { navController.navigate("series") },
                onMovieClick = { movie ->
                    navController.navigate("movie_details/${movie.id}")
                }
            )
        }
        composable("series") {
            SeriesScreen(
                onBack = navigateToHome,
                onNavigateToSearch = { navController.navigate("search") },
                onNavigateToLiveTv = { navController.navigate("live") },
                onNavigateToMovies = { navController.navigate("movies") },
                onSeriesClick = { series ->
                    navController.navigate("series_details/${series.id}")
                }
            )
        }
        composable(
            "movie_details/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) {
            val viewModel: MovieDetailViewModel = hiltViewModel()
            val movie by viewModel.movie.collectAsState()
            movie?.let { m ->
                MovieDetailsScreen(
                    movie = m,
                    onBack = { navController.popBackStack() },
                    onPlay = {
                        navController.navigate("player/${m.id}/movie/${m.name}")
                    }
                )
            }
        }
        composable(
            "series_details/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) {
            val viewModel: SeriesDetailsViewModel = hiltViewModel()
            val series by viewModel.series.collectAsState()
            series?.let { s ->
                SeriesDetailsScreen(
                    series = s,
                    onBack = { navController.popBackStack() },
                    onPlayEpisode = { episode ->
                        navController.navigate("player/${episode.id}/series/${episode.title}")
                    }
                )
            }
        }
        composable("search") {
            SearchScreen(
                onBack = navigateToHome,
                onMovieClick = { movie -> navController.navigate("movie_details/${movie.id}") },
                onSeriesClick = { series -> navController.navigate("series_details/${series.id}") },
                onChannelClick = { channel -> navController.navigate("player/${channel.id}/live/${channel.name}") }
            )
        }
        composable("downloads") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Downloads Coming Soon", color = Color.White)
            }
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
            "player/{id}/{type}/{title}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("type") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val playerViewModel: PlayerViewModel = hiltViewModel()
            PlayerScreen(
                viewModel = playerViewModel, 
                playerEngine = playerViewModel.playerEngine,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
