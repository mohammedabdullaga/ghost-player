package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Episode
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.details.SeriesDetailsViewModel
import com.eliteprime.app.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun TvSeriesDetailsScreen(
    onBack: () -> Unit,
    onPlayEpisode: (Episode, String) -> Unit,
    viewModel: SeriesDetailsViewModel = hiltViewModel()
) {
    val series by viewModel.series.collectAsState()
    val seasons by viewModel.seasons.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    
    val firstEpisodeFocusRequester = remember { FocusRequester() }

    BackHandler {
        onBack()
    }

    LaunchedEffect(series, selectedSeason) {
        if (series != null && selectedSeason != null) {
            delay(500)
            try {
                firstEpisodeFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    AppBackground(isTransparent = true) {
        if (series != null) {
            val s = series!!
            
            // Backdrop
            AsyncImage(
                model = s.backdropUrl ?: s.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.3f
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Info Section
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .padding(48.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = s.name,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = s.plot ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        var isFav by remember { mutableStateOf(s.isFavorite) }
                        TvSurface(
                            onClick = { 
                                viewModel.toggleFavorite()
                                isFav = !isFav
                            },
                            modifier = Modifier.size(48.dp),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                focusedContainerColor = Color.White
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    null,
                                    tint = if (isFav) Color.Red else Color.White
                                )
                            }
                        }
                    }
                }

                // Episodes Section
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(24.dp)
                ) {
                    // Seasons Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        items(seasons) { swE ->
                            val isSelected = selectedSeason?.season?.id == swE.season.id
                            TvSurface(
                                onClick = { viewModel.selectSeason(swE) },
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f),
                                    focusedContainerColor = Color.White
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp))
                            ) {
                                Text(
                                    text = "Season ${swE.season.seasonNumber}",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Episodes List
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        selectedSeason?.episodes?.let { episodes ->
                            itemsIndexed(episodes) { index, episode ->
                                val modifier = if (index == 0) Modifier.focusRequester(firstEpisodeFocusRequester) else Modifier
                                TvEpisodeRow(
                                    episode = episode,
                                    modifier = modifier,
                                    onClick = { onPlayEpisode(episode, s.name) }
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
private fun TvEpisodeRow(
    episode: Episode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    TvSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp, 45.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = episode.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    Icons.Default.PlayArrow,
                    null,
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = "E${episode.episodeNumber} · ${episode.title}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!episode.plot.isNullOrBlank()) {
                    Text(
                        text = episode.plot!!,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
