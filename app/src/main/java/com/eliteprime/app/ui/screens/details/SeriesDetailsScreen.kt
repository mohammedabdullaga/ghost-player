package com.eliteprime.app.ui.screens.details

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.eliteprime.app.data.local.entity.Episode
import com.eliteprime.app.data.local.entity.Season
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.MobileBackButton
import com.eliteprime.app.ui.theme.AppColors

@Composable
fun SeriesDetailsScreen(
    series: Series,
    onBack: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    viewModel: SeriesDetailsViewModel = hiltViewModel()
) {
    val seasons by viewModel.seasons.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()

    LaunchedEffect(seasons) {
        if (selectedSeason == null && seasons.isNotEmpty()) {
            viewModel.selectSeason(seasons.first())
        }
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cinematic Backdrop
            AsyncImage(
                model = series.backdropUrl ?: series.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF070B14), Color(0xFF070B14).copy(alpha = 0.8f), Color.Transparent)
                    )
                )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Poster & Back
                Box(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(24.dp))
                ) {
                    AsyncImage(
                        model = series.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    MobileBackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
                }

                // Right Panel: Info & Episodes
                LazyColumn(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .padding(start = 16.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SeriesInfoBlock(series)
                    }

                    item {
                        SeasonSelector(
                            seasons = seasons,
                            selectedSeason = selectedSeason,
                            onSeasonSelected = { viewModel.selectSeason(it) }
                        )
                    }

                    selectedSeason?.let { season ->
                        itemsIndexed(season.episodes) { index, episode ->
                            EpisodeCard(
                                episode = episode,
                                index = index + 1,
                                onClick = { onPlayEpisode(episode) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesInfoBlock(series: Series) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "SERIES", color = AppColors.ArenaOrange, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Text(text = series.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (series.rating > 0) {
                Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("%.1f".format(series.rating), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            series.genre?.let {
                Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }

        if (!series.plot.isNullOrBlank()) {
            Text(
                text = series.plot!!,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeasonSelector(
    seasons: List<Season>,
    selectedSeason: Season?,
    onSeasonSelected: (Season) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Surface(
                onClick = { expanded = true },
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedSeason?.name ?: "Select Season", color = Color.White, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF1A1A1A))
            ) {
                seasons.forEach { season ->
                    DropdownMenuItem(
                        text = { Text(season.name, color = Color.White) },
                        onClick = { onSeasonSelected(season); expanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("${selectedSeason?.episodes?.size ?: 0} EPISODES", color = AppColors.ArenaOrange, fontWeight = FontWeight.Black, fontSize = 11.sp)
    }
}

@Composable
private fun EpisodeCard(episode: Episode, index: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.fillMaxHeight().aspectRatio(1.6f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                AsyncImage(model = episode.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(bottomEnd = 8.dp), modifier = Modifier.align(Alignment.TopStart)) {
                    Text(index.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("E${episode.episodeNumber} · ${episode.title}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                episode.duration?.let { Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) }
            }
            Icon(Icons.Default.PlayArrow, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(24.dp))
        }
    }
}
