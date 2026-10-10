package com.ghost.tv.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghost.tv.R
import com.ghost.tv.data.local.entity.PlaybackHistory
import com.ghost.tv.ui.components.AppBackground
import com.ghost.tv.ui.components.ContinueWatchingCard
import com.ghost.tv.ui.screens.dashboard.DashboardViewModel
import com.ghost.tv.ui.tv.core.*
import com.ghost.tv.util.DeviceRenderProfile

@Composable
fun TvHistoryGridScreen(
    onBack: () -> Unit,
    onNavigateToMovieDetails: (Long) -> Unit,
    onNavigateToSeriesDetails: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLite = DeviceRenderProfile.isLiteMode()

    BackHandler { onBack() }

    AppBackground(isTransparent = !isLite) {
        Column(modifier = Modifier.fillMaxSize().padding(56.dp)) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            TvGlassPane(modifier = Modifier.fillMaxSize()) {
                if (uiState.continueWatching.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.home_history_empty), color = TvColors.TextTertiary)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(uiState.continueWatching) { history ->
                            ContinueWatchingCard(
                                history = history,
                                onClick = {
                                    if (history.type == "movie") onNavigateToMovieDetails(history.streamId)
                                    else onNavigateToSeriesDetails(history.streamId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
