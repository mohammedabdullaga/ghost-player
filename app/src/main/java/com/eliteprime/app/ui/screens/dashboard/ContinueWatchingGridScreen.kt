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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay

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
    var historyToDelete by remember { mutableStateOf<PlaybackHistory?>(null) }

    val filteredHistory = remember(uiState.continueWatching, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> uiState.continueWatching
            HistoryFilter.MOVIES -> uiState.continueWatching.filter { it.type == "movie" }
            HistoryFilter.SERIES -> uiState.continueWatching.filter { it.type == "series" || it.type == "series_episode" }
        }
    }

    val itemFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(filteredHistory) {
        if (isTv && filteredHistory.isNotEmpty() && !initialFocusDone) {
            // Quick repeat attempts to capture focus as soon as the first item is composed
            repeat(10) {
                try {
                    val firstId = filteredHistory.first().id
                    itemFocusRequesters[firstId]?.requestFocus()
                    initialFocusDone = true
                    return@LaunchedEffect
                } catch (e: Exception) {}
                delay(50)
            }
        }
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Black) },
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
                        PaneHeader(title = stringResource(R.string.generic_filters))
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                FilterItem(
                                    label = stringResource(R.string.filter_all),
                                    isSelected = selectedFilter == HistoryFilter.ALL,
                                    onClick = { selectedFilter = HistoryFilter.ALL }
                                )
                            }
                            item {
                                FilterItem(
                                    label = stringResource(R.string.filter_movies),
                                    isSelected = selectedFilter == HistoryFilter.MOVIES,
                                    onClick = { selectedFilter = HistoryFilter.MOVIES }
                                )
                            }
                            item {
                                FilterItem(
                                    label = stringResource(R.string.filter_series),
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
                            Text(stringResource(R.string.history_empty_filter), color = Color.White.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (isTv) 220.dp else 160.dp),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(filteredHistory, key = { it.id }) { history ->
                                val focusRequester = itemFocusRequesters.getOrPut(history.id) { FocusRequester() }
                                ContinueWatchingCard(
                                    history = history,
                                    modifier = Modifier.focusRequester(focusRequester),
                                    onClick = {
                                        when (history.type) {
                                            "movie" -> onNavigateToMovieDetails(history.streamId)
                                            "series", "series_episode" -> onNavigateToSeriesDetails(history.seriesId ?: history.streamId)
                                        }
                                    },
                                    onLongClick = { historyToDelete = history }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (historyToDelete != null) {
            val entry = historyToDelete!!
            if (isTv) {
                TvConfirmDialog(
                    title = stringResource(R.string.history_remove_title),
                    message = stringResource(R.string.history_remove_msg, entry.name),
                    confirmText = stringResource(R.string.generic_remove),
                    confirmColor = Color.Red,
                    icon = Icons.Default.Delete,
                    onConfirm = {
                        viewModel.removeFromHistory(entry)
                        historyToDelete = null
                    },
                    onDismiss = { historyToDelete = null }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { historyToDelete = null },
                    title = { Text(stringResource(R.string.history_remove_title)) },
                    text = { Text(stringResource(R.string.history_remove_msg, historyToDelete!!.name)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.removeFromHistory(historyToDelete!!)
                            historyToDelete = null
                        }) {
                            Text(stringResource(R.string.generic_remove), color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { historyToDelete = null }) {
                            Text(stringResource(R.string.generic_cancel), color = Color.White.copy(alpha = 0.6f))
                        }
                    },
                    containerColor = Color(0xFF1A1C1E),
                    titleContentColor = Color.White,
                    textContentColor = Color.White.copy(alpha = 0.7f)
                )
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
    val isTv = LocalContext.current.isTelevisionDevice()
    if (isTv) {
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(48.dp).onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Transparent,
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
            )
        ) {
            FilterItemContent(label, isSelected, isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) BorderStroke(1.dp, AppColors.ArenaOrange.copy(alpha = 0.85f)) else null
        ) {
            FilterItemContent(label, isSelected, false)
        }
    }
}

@Composable
private fun FilterItemContent(label: String, isSelected: Boolean, isFocused: Boolean) {
    val contentColor = when {
        isFocused -> Color.Black
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.6f)
    }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(if (isSelected || isFocused) AppColors.ArenaOrange else Color.Transparent, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = contentColor,
            fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
