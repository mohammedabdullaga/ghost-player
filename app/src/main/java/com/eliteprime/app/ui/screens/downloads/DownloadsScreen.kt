package com.eliteprime.app.ui.screens.downloads

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.eliteprime.app.R
import com.eliteprime.app.domain.model.DownloadItem
import com.eliteprime.app.domain.model.DownloadStatus
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayDownload: (DownloadItem) -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTv = LocalContext.current.isTelevisionDevice()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.background(Color.Black.copy(alpha = 0.4f))) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.downloads_title), fontWeight = FontWeight.Black, fontSize = 18.sp) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White
                        )
                    )
                    
                    // Category Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DownloadTabButton(
                            label = stringResource(R.string.filter_all),
                            count = uiState.allCount,
                            isSelected = uiState.currentFilter == DownloadFilter.ALL,
                            onClick = { viewModel.setFilter(DownloadFilter.ALL) },
                            isTv = isTv
                        )
                        DownloadTabButton(
                            label = stringResource(R.string.filter_movies),
                            count = uiState.movieCount,
                            isSelected = uiState.currentFilter == DownloadFilter.MOVIES,
                            onClick = { viewModel.setFilter(DownloadFilter.MOVIES) },
                            isTv = isTv
                        )
                        DownloadTabButton(
                            label = stringResource(R.string.filter_series),
                            count = uiState.seriesCount,
                            isSelected = uiState.currentFilter == DownloadFilter.SERIES,
                            onClick = { viewModel.setFilter(DownloadFilter.SERIES) },
                            isTv = isTv
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (uiState.filteredDownloads.isEmpty() && !uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DownloadForOffline, null, tint = Color.White.copy(alpha = 0.1f), modifier = Modifier.size(80.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.downloads_empty), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.ArenaOrange)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (isTv) 240.dp else 180.dp),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.filteredDownloads, key = { it.id }) { download ->
                            DownloadItemCard(
                                item = download,
                                onResume = { viewModel.resumeDownload(download.id) },
                                onCancel = { viewModel.cancelDownload(download.id) },
                                onDelete = { viewModel.deleteDownload(download.id) },
                                onPlay = { onPlayDownload(download) },
                                isTv = isTv
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTabButton(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    isTv: Boolean
) {
    if (isTv) {
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = onClick,
            modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Transparent,
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent)),
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
                if (count > 0) {
                    Surface(
                        color = if (isFocused) Color.Black.copy(alpha = 0.1f) else AppColors.ArenaOrange.copy(alpha = 0.25f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = count.toString(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = if (isFocused) Color.Black else AppColors.ArenaOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        val alpha by animateFloatAsState(if (isSelected) 1f else 0.5f, label = "tabAlpha")
        Surface(
            onClick = onClick,
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(10.dp),
            border = if (isSelected) BorderStroke(1.dp, AppColors.ArenaOrange) else null
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = alpha),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                if (count > 0) {
                    Text(
                        text = "($count)",
                        color = if (isSelected) AppColors.ArenaOrange else Color.White.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItem,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    isTv: Boolean
) {
    if (isTv) {
        TvDownloadCard(item, onResume, onCancel, onDelete, onPlay)
    } else {
        MobileDownloadCard(item, onResume, onCancel, onDelete, onPlay)
    }
}

@Composable
private fun MobileDownloadCard(
    item: DownloadItem,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f))
                ) {
                    if (item.posterUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.posterUrl)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Download, null, modifier = Modifier.align(Alignment.Center), tint = Color.White.copy(alpha = 0.3f))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = if (item.seasonNumber != null && item.episodeNumber != null) {
                        stringResource(R.string.downloads_episode_format, item.seasonNumber, item.episodeNumber, item.contentName)
                    } else {
                        item.contentName
                    }
                    Text(displayName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDownloadStatus(item.status), color = statusColor(item.status), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (item.status == DownloadStatus.DOWNLOADING) {
                val progress = item.totalBytes?.let { if (it > 0) item.bytesWritten.toFloat() / it else 0f } ?: 0f
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AppColors.ArenaOrange,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${formatSpeed(item.downloadSpeedBps)} • ${formatEta(item.estimatedTimeRemainingMs)}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                    Text("${(progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (item.status == DownloadStatus.COMPLETED) {
                    IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, null, tint = AppColors.ArenaOrange) }
                }
                if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.PAUSED) {
                    IconButton(onClick = onResume) { Icon(Icons.Default.Refresh, null, tint = Color.White) }
                }
                if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PENDING) {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = Color.White) }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.7f)) }
            }
        }
    }
}

@Composable
private fun TvDownloadCard(
    item: DownloadItem,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    TvSurface(
        onClick = { if (item.status == DownloadStatus.COMPLETED) onPlay() },
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            focusedContainerColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f))
                ) {
                    if (item.posterUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.posterUrl)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Download, null, modifier = Modifier.align(Alignment.Center), tint = Color.White.copy(alpha = 0.3f))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = if (item.seasonNumber != null && item.episodeNumber != null) {
                        stringResource(R.string.downloads_episode_format, item.seasonNumber, item.episodeNumber, item.contentName)
                    } else {
                        item.contentName
                    }
                    Text(displayName, color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    Text(formatDownloadStatus(item.status), color = statusColor(item.status), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (item.status == DownloadStatus.DOWNLOADING) {
                val progress = item.totalBytes?.let { if (it > 0) item.bytesWritten.toFloat() / it else 0f } ?: 0f
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AppColors.ArenaOrange,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${formatSpeed(item.downloadSpeedBps)} • ${formatEta(item.estimatedTimeRemainingMs)}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                    Text("${(progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                }
            }

            if (isFocused) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.PAUSED) {
                        TvIconButton(Icons.Default.Refresh, stringResource(R.string.generic_resume), onResume)
                    }
                    if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PENDING) {
                        TvIconButton(Icons.Default.Close, stringResource(R.string.generic_cancel), onCancel)
                    }
                    TvIconButton(Icons.Default.Delete, stringResource(R.string.generic_delete), onDelete, tint = Color.Red.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun TvIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Color.White) {
    var isIconFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = Modifier.size(32.dp).padding(2.dp).onFocusChanged { isIconFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = if (isIconFocused) Color.Black else tint, modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatSpeed(bps: Long): String {
    return when {
        bps >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB/s", bps / (1024f * 1024f))
        bps >= 1024 -> String.format(Locale.getDefault(), "%.1f KB/s", bps / 1024f)
        else -> "$bps B/s"
    }
}

private fun formatEta(ms: Long?): String {
    if (ms == null || ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
private fun formatDownloadStatus(status: DownloadStatus): String = when (status) {
    DownloadStatus.PENDING -> stringResource(R.string.download_status_pending)
    DownloadStatus.DOWNLOADING -> stringResource(R.string.download_status_downloading)
    DownloadStatus.PAUSED -> stringResource(R.string.download_status_paused)
    DownloadStatus.COMPLETED -> stringResource(R.string.download_status_completed)
    DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
    DownloadStatus.CANCELLED -> stringResource(R.string.download_status_cancelled)
}

private fun statusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
    DownloadStatus.DOWNLOADING -> AppColors.ArenaOrange
    DownloadStatus.FAILED -> Color(0xFFEF5350)
    DownloadStatus.PENDING -> Color.White.copy(alpha = 0.5f)
    DownloadStatus.PAUSED -> Color(0xFFFFC107)
    DownloadStatus.CANCELLED -> Color.White.copy(alpha = 0.3f)
}
