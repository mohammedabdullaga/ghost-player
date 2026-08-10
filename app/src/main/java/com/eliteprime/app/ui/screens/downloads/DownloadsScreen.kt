package com.eliteprime.app.ui.screens.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.eliteprime.app.R
import com.eliteprime.app.domain.model.DownloadItem
import com.eliteprime.app.domain.model.DownloadStatus
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
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
                TopAppBar(
                    title = { Text("Downloads", fontWeight = FontWeight.Black) },
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
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (uiState.downloads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No downloads found", color = Color.White.copy(alpha = 0.5f))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (isTv) 240.dp else 200.dp),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.downloads) { download ->
                            DownloadItemCard(
                                item = download,
                                onResume = { viewModel.resumeDownload(download.id) },
                                onCancel = { viewModel.cancelDownload(download.id) },
                                onDelete = { viewModel.deleteDownload(download.id) },
                                onPlay = { onPlayDownload(download) }
                            )
                        }
                    }
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
                        AsyncImage(model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Default.Download, null, modifier = Modifier.align(Alignment.Center), tint = Color.White.copy(alpha = 0.3f))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.contentName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.status.name, color = statusColor(item.status), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

private fun statusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
    DownloadStatus.DOWNLOADING -> AppColors.ArenaOrange
    DownloadStatus.FAILED -> Color(0xFFEF5350)
    DownloadStatus.PENDING -> Color.White.copy(alpha = 0.5f)
    DownloadStatus.PAUSED -> Color(0xFFFFC107)
    DownloadStatus.CANCELLED -> Color.White.copy(alpha = 0.3f)
}
