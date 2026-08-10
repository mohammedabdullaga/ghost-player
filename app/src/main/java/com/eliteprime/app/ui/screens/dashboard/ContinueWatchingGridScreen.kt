package com.eliteprime.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice

private enum class HistoryFilter { ALL, MOVIES, SERIES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueWatchingGridScreen(
    onBack: () -> Unit,
    onNavigateToMovieDetails: (Long) -> Unit,
    onNavigateToSeriesDetails: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTv = LocalContext.current.isTelevisionDevice()
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    val filteredHistory = remember(uiState.continueWatching, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> uiState.continueWatching
            HistoryFilter.MOVIES -> uiState.continueWatching.filter { it.type == "movie" }
            HistoryFilter.SERIES -> uiState.continueWatching.filter { it.type == "series" || it.type == "series_episode" }
        }
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("CONTINUE WATCHING", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.2f),
                    titleContentColor = Color.White
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Sidebar Filters
                PaneSurface(modifier = Modifier.width(200.dp)) {
                    Column {
                        PaneHeader(title = "FILTERS")
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                FilterItem(
                                    label = "ALL",
                                    isSelected = selectedFilter == HistoryFilter.ALL,
                                    onClick = { selectedFilter = HistoryFilter.ALL }
                                )
                            }
                            item {
                                FilterItem(
                                    label = "MOVIES",
                                    isSelected = selectedFilter == HistoryFilter.MOVIES,
                                    onClick = { selectedFilter = HistoryFilter.MOVIES }
                                )
                            }
                            item {
                                FilterItem(
                                    label = "SERIES",
                                    isSelected = selectedFilter == HistoryFilter.SERIES,
                                    onClick = { selectedFilter = HistoryFilter.SERIES }
                                )
                            }
                        }
                    }
                }

                // Grid Content
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredHistory.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No history found in this category", color = Color.White.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (isTv) 220.dp else 160.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(filteredHistory) { history ->
                                ContinueWatchingCard(
                                    history = history,
                                    onClick = {
                                        when (history.type) {
                                            "movie" -> onNavigateToMovieDetails(history.streamId)
                                            "series", "series_episode" -> onNavigateToSeriesDetails(history.seriesId ?: history.streamId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        color = if (isSelected) AppColors.FocusBrown.copy(alpha = 0.8f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(if (isSelected) AppColors.ArenaOrange else Color.Transparent, RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
