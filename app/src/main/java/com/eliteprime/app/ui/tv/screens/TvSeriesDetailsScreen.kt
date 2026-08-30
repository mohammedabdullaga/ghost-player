package com.eliteprime.app.ui.tv.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Episode
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.details.SeriesDetailsViewModel
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.util.DeviceRenderProfile
import com.eliteprime.app.util.formatPositionMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EPISODE_MARKER_REGEX = Regex("""S\d{1,2}E\d{1,3}""", RegexOption.IGNORE_CASE)

private fun cleanEpisodeTitle(rawTitle: String): String {
    val match = EPISODE_MARKER_REGEX.findAll(rawTitle).lastOrNull() ?: return rawTitle
    val rest = rawTitle.substring(match.range.last + 1).trim(' ', '-', '·', ':', '.')
    return rest.ifBlank { rawTitle }
}

private val SERIES_TAG_PREFIX_REGEX = Regex("""^[A-Z0-9]+(?:[\s\-][A-Z0-9]+)*\s*-\s*""")

private fun cleanSeriesTitle(rawTitle: String): String {
    val stripped = SERIES_TAG_PREFIX_REGEX.replaceFirst(rawTitle, "").trim()
    return stripped.ifBlank { rawTitle }
}

@Composable
fun TvSeriesDetailsScreen(
    onBack: () -> Unit,
    onPlayEpisode: (Episode, String) -> Unit,
    onSeriesClick: (Series) -> Unit = {},
    viewModel: SeriesDetailsViewModel = hiltViewModel()
) {
    val series by viewModel.series.collectAsState()
    val seasons by viewModel.seasons.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val resumeEpisode by viewModel.resumeEpisode.collectAsState()
    val relatedSeries by viewModel.relatedSeries.collectAsState()

    val playFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var episodesSectionY by remember { mutableStateOf(0) }

    BackHandler { onBack() }

    LaunchedEffect(series) {
        if (series != null) {
            delay(300)
            try { playFocusRequester.requestFocus() } catch (e: Exception) {}
            // Removed the initial scroll that was cutting off the title
        }
    }

    AppBackground(isTransparent = true) {
        if (series == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TvColors.Primary)
            }
        } else {
            val s = series!!
            val cleanedSeriesName = remember(s.name) { cleanSeriesTitle(s.name) }

            // Full-bleed backdrop specific to this series, sitting behind
            // everything. Now enabled in Lite Mode as well.
            AsyncImage(
                model = s.backdropUrl ?: s.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.55f
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                            startY = 80f,
                            endY = 850f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // --- 1. HERO SECTION — text block (left, ~62% width) +
                // poster card (right), both over the full-bleed backdrop.
                // Title sized down significantly and de-weighted from
                // Black to ExtraBold — the previous 34sp/Black combo in
                // solid orange on black produced a "glowing, oversized"
                // look on the actual TV panel that wasn't obvious from
                // code alone.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .padding(top = 110.dp) // Lower the info block from the top edge
                ) {
                    // HERO CONTENT - Left aligned Column
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .padding(end = 32.dp)
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                // Report slightly less height to pull the Seasons/Episodes section up, 
                                // closing the "huge gap" between the info text and the content below.
                                layout(placeable.width, placeable.height - 40) {
                                    placeable.place(0, 0)
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = cleanedSeriesName,
                            color = AppColors.ArenaOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            s.genre?.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (s.rating > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Default.Star, null, tint = TvColors.StarGold, modifier = Modifier.size(13.dp))
                                    Text(text = "%.1f".format(s.rating), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Text(
                            text = s.plot ?: "No description available.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val playText = if (resumeEpisode != null)
                                "Play episode ${resumeEpisode!!.episodeNumber}"
                            else
                                "Play episode 1"

                            TvSurfaceCard(
                                onClick = {
                                    if (resumeEpisode != null) {
                                        onPlayEpisode(resumeEpisode!!, s.name)
                                    } else {
                                        selectedSeason?.episodes?.firstOrNull()?.let { onPlayEpisode(it, s.name) }
                                    }
                                },
                                modifier = Modifier
                                    .height(40.dp)
                                    .focusRequester(playFocusRequester),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White,
                                    focusedContainerColor = Color.White
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(text = playText, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            val isFavorite = s.isFavorite
                            TvActionButton(
                                icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                onClick = { viewModel.toggleFavorite() },
                                tint = if (isFavorite) TvColors.Primary else Color.White
                            )
                            TvActionButton(icon = Icons.Default.CheckCircleOutline, onClick = { })
                            TvActionButton(icon = Icons.Default.MoreHoriz, onClick = { })
                        }
                    }

                    // Floating Poster overlay - rendered on the right.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(180.dp)
                            .aspectRatio(2f / 3f)
                            .offset(y = (-100).dp) // Stay anchored near the top screen edge despite the parent's padding
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, 0) {
                                    placeable.place(0, 0)
                                }
                            }
                    ) {
                        TvSurfaceCard(
                            onClick = { },
                            modifier = Modifier.fillMaxSize(),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = s.cover ?: s.backdropUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Spacer removed to further reduce gap as requested
                // --- 2. SEASONS + EPISODES ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .onGloballyPositioned { coords ->
                            episodesSectionY = coords.boundsInParent().top.toInt()
                        },
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(seasons) { swE ->
                            val isSelected = selectedSeason?.season?.id == swE.season.id
                            TvSeasonTab(
                                label = swE.season.name,
                                isSelected = isSelected,
                                onClick = {
                                    viewModel.selectSeason(swE)
                                    scope.launch {
                                        scrollState.animateScrollTo(episodesSectionY)
                                    }
                                }
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 6.dp, bottom = 56.dp)
                    ) {
                        selectedSeason?.episodes?.let { episodes ->
                            items(episodes) { episode ->
                                TvEpisodeCard(
                                    episode = episode,
                                    fallbackImageUrl = s.cover ?: s.backdropUrl,
                                    isResumeEpisode = resumeEpisode?.id == episode.id,
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
private fun TvActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.18f),
            focusedContainerColor = Color.White
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp).align(Alignment.Center),
            tint = if (isFocused) Color.Black else tint
        )
    }
}

@Composable
private fun TvSeasonTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
            focusedContainerColor = Color.White
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (isFocused) Color.Black else if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun TvEpisodeCard(
    episode: Episode,
    fallbackImageUrl: String? = null,
    isResumeEpisode: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val displayTitle = remember(episode.title) { cleanEpisodeTitle(episode.title) }

    Column(
        modifier = Modifier
            .width(260.dp)
            .onFocusChanged { isFocused = it.isFocused },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TvSurfaceCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(episode.coverUrl ?: fallbackImageUrl)
                        .size(Size(640, 360))
                        .precision(Precision.INEXACT)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isResumeEpisode) {
                    Surface(
                        color = AppColors.ArenaOrange,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.padding(8.dp).align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = stringResource(R.string.generic_status_watching),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (episode.watchProgress > 0 && episode.totalDuration > 0) {
                    val progress = episode.watchProgress.toFloat() / episode.totalDuration
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomStart)
                            .background(Color.Red.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(Color.Red)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = "EPISODE ${episode.episodeNumber}",
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = displayTitle,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            episode.duration?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }
    }
}