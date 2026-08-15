package com.eliteprime.app.ui.tv.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.ArenaLiveLogo
import com.eliteprime.app.ui.components.NavTabButton
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.home.HomeHeroCard
import com.eliteprime.app.ui.screens.home.TrendingHeroCard
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class TvHomeFocus { NAV_HOME, NAV_LIVE, NAV_MOVIES, NAV_SERIES, TRENDING, CONTINUE, DOWNLOADS }

@Composable
fun TvHomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToTrending: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToContinueWatching: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    val scope = rememberCoroutineScope()
    var showExitDialog by remember { mutableStateOf(false) }

    val navHomeRequester = remember { FocusRequester() }
    val trendingRequester = remember { FocusRequester() }
    
    var lastFocus by rememberSaveable { mutableStateOf(TvHomeFocus.NAV_HOME) }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        ExitConfirmationDialog(
            onConfirm = { activity?.finish() },
            onDismiss = { showExitDialog = false }
        )
    }

    // LANDING FIX: Force focus to HOME tab on start
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            delay(200)
            if (lastFocus == TvHomeFocus.NAV_HOME) {
                navHomeRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }

    AppBackground(isTransparent = true) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP NAV ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArenaLiveLogo(textSize = 24.sp)
                Spacer(modifier = Modifier.width(64.dp))
                
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    NavTabButton(
                        label = stringResource(R.string.nav_home), 
                        isActive = true, 
                        onClick = {},
                        modifier = Modifier.focusRequester(navHomeRequester).onFocusChanged { if(it.isFocused) lastFocus = TvHomeFocus.NAV_HOME }
                    )
                    NavTabButton(
                        label = stringResource(R.string.nav_live_tv), 
                        isActive = false, 
                        onClick = onNavigateToLiveTv,
                        modifier = Modifier.onFocusChanged { if(it.isFocused) lastFocus = TvHomeFocus.NAV_LIVE }
                    )
                    NavTabButton(
                        label = stringResource(R.string.nav_movies), 
                        isActive = false, 
                        onClick = onNavigateToMovies,
                        modifier = Modifier.onFocusChanged { if(it.isFocused) lastFocus = TvHomeFocus.NAV_MOVIES }
                    )
                    NavTabButton(
                        label = stringResource(R.string.nav_series), 
                        isActive = false, 
                        onClick = onNavigateToSeries,
                        modifier = Modifier.onFocusChanged { if(it.isFocused) lastFocus = TvHomeFocus.NAV_SERIES }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvRoundIconButton(icon = Icons.Default.Search, onClick = onNavigateToSearch)
                    TvRoundIconButton(icon = Icons.Default.Tune, onClick = onNavigateToSettings)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = currentTime.value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }

            // --- HERO SECTION ---
            val lastWatched = uiState.continueWatching.firstOrNull()
            val trendingItems = uiState.trendingMovies.ifEmpty { uiState.trendingSeries }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TrendingHeroCard(
                    items = trendingItems,
                    onClick = onNavigateToTrending,
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .focusRequester(trendingRequester)
                        .onFocusChanged { if (it.isFocused) lastFocus = TvHomeFocus.TRENDING }
                )

                Column(modifier = Modifier.weight(0.4f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    HomeHeroCard(
                        title = stringResource(R.string.home_continue_watching),
                        description = lastWatched?.name ?: stringResource(R.string.home_history_empty),
                        backgroundUrl = lastWatched?.backdropUrl ?: lastWatched?.posterUrl,
                        accentColor = Color(0xFFFF4500),
                        onClick = onNavigateToContinueWatching,
                        modifier = Modifier.weight(1f).fillMaxWidth().onFocusChanged { if(it.isFocused) lastFocus = TvHomeFocus.CONTINUE },
                        progress = lastWatched?.let { if (it.durationMs > 0) it.lastPositionMs.toFloat() / it.durationMs.toFloat() else 0f }
                    )

                    HomeHeroCard(
                        title = stringResource(R.string.home_downloads),
                        description = stringResource(R.string.home_downloads_desc),
                        backgroundUrl = uiState.latestDownloadPoster,
                        accentColor = Color(0xFF00BFFF),
                        onClick = onNavigateToDownloads,
                        modifier = Modifier.weight(1f).fillMaxWidth().onFocusChanged { if(it.isFocused) lastFocus = TvHomeFocus.DOWNLOADS },
                        badgeLabel = if (uiState.completedDownloadsCount > 0) "${uiState.completedDownloadsCount} READY" else null
                    )
                }
            }
        }
    }
}

@Composable
private fun ExitConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_logout_confirm_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.exit_confirm_msg)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.generic_yes), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.generic_no), color = Color.White.copy(alpha = 0.6f))
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color(0xFF12151B),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.7f)
    )
}

@Composable
private fun TvRoundIconButton(icon: ImageVector, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = Modifier.size(48.dp).onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(24.dp))
        }
    }
}
