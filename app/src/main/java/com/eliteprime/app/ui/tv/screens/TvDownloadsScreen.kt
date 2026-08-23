package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.downloads.DownloadViewModel
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.util.DeviceRenderProfile

@Composable
fun TvDownloadsScreen(
    onBack: () -> Unit,
    onPlayDownload: (DownloadItem) -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLite = DeviceRenderProfile.isLiteMode()

    BackHandler { onBack() }

    AppBackground(isTransparent = !isLite) {
        Column(modifier = Modifier.fillMaxSize().padding(56.dp)) {
            Text(
                text = stringResource(R.string.downloads_title).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            TvGlassPane(modifier = Modifier.fillMaxSize()) {
                if (uiState.filteredDownloads.isEmpty() && !uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DownloadForOffline, null, tint = Color.White.copy(alpha = 0.1f), modifier = Modifier.size(80.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.downloads_empty), color = TvColors.TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(uiState.filteredDownloads, key = { it.id }) { download ->
                            TvDownloadItemCard(
                                item = download,
                                onPlay = { onPlayDownload(download) },
                                onDelete = { viewModel.deleteDownload(download.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDownloadItemCard(
    item: DownloadItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = { if (item.status == DownloadStatus.COMPLETED) onPlay() },
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (item.status == DownloadStatus.COMPLETED) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.align(Alignment.Center).size(32.dp), tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = item.contentName,
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (item.status == DownloadStatus.COMPLETED) stringResource(R.string.home_downloads_ready) else stringResource(R.string.download_status_downloading),
                    color = if (isFocused) Color.Black.copy(alpha = 0.6f) else TvColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                if (isFocused) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
