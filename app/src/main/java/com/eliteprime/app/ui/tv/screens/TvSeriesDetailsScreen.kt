package com.eliteprime.app.ui.tv.screens

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
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Episode
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.details.SeriesDetailsViewModel
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.util.formatPositionMs
import kotlinx.coroutines.delay

private enum class TvSeriesTab { EPISODES, RELATED }

// Provider-supplied episode titles repeat the whole series name/year/
// country before the actual per-episode name, e.g.
// "AR-SUBS - FROM (2022) (US) - S01E01 - <real name>". Rather than
// guessing a fixed text delimiter, this looks for the "SxxExx" marker
// that's always present right before the real name and takes everything
// after its LAST occurrence. Falls back to the untouched raw title if
// the pattern isn't found, so a provider that doesn't follow this
// convention is never made worse.
private val EPISODE_MARKER_REGEX = Regex("""S\d{1,2}E\d{1,3}""", RegexOption.IGNORE_CASE)

private fun cleanEpisodeTitle(rawTitle: String): String {
    val match = EPISODE_MARKER_REGEX.findAll(rawTitle).lastOrNull() ?: return rawTitle
    val rest = rawTitle.substring(match.range.last + 1).trim(' ', '-', '·', ':', '.')
    return rest.ifBlank { rawTitle }
}

// Series names have the same provider-prefix problem: "AR-SUBS - FROM
// (2022) (US)", "AR-EG - اللعبة", etc. — every confirmed example is a
// short all-caps/digit source tag, followed by " - ", followed by the
// real name. Unlike episode titles there's no "SxxExx" anchor to search
// for, so this strips a LEADING run of caps/digits/hyphens/spaces
// followed by " - " instead. This is a heuristic, not a guarantee: a
// legitimate title that itself starts in all-caps immediately followed
// by " - " would also get stripped. Worth confirming after building
// that it doesn't misfire on any real series names.
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

    var currentTab by remember { mutableStateOf(TvSeriesTab.EPISODES) }
    var showSeasonPicker by remember { mutableStateOf(false) }
    val playFocusRequester = remember { FocusRequester() }
    val seasonButtonFocusRequester = remember { FocusRequester() }

    val listState = rememberLazyListState()

    BackHandler { onBack() }

    // Scroll to resume episode on load
    LaunchedEffect(selectedSeason, resumeEpisode) {
        if (selectedSeason != null && resumeEpisode != null) {
            val index = selectedSeason!!.episodes.indexOfFirst { it.id == resumeEpisode!!.id }
            if (index >= 0) {
                delay(600) // Wait for list to be ready
                listState.animateScrollToItem(index)
            }
        }
    }

    LaunchedEffect(series) {
        if (series != null) {
            delay(300)
            try { playFocusRequester.requestFocus() } catch (e: Exception) {}
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

            AsyncImage(
                model = s.backdropUrl ?: s.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.45f
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Black.copy(alpha = 0.8f), Color.Transparent),
                            endX = 1400f
                        )
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // --- 1. LEFT PANEL: INFO ---
                TvGlassPane(
                    modifier = Modifier.weight(0.55f).fillMaxHeight(),
                    backgroundColor = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.width(240.dp).fillMaxHeight()) {
                            AsyncImage(
                                model = s.cover ?: s.backdropUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(28.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = stringResource(R.string.nav_series),
                                        color = TvColors.Primary,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        letterSpacing = 1.2.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = cleanedSeriesName.uppercase(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                var isFavFocused by remember { mutableStateOf(false) }
                                TvSurfaceCard(
                                    onClick = { viewModel.toggleFavorite() },
                                    modifier = Modifier.size(44.dp).onFocusChanged { isFavFocused = it.isFocused },
                                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), focusedContainerColor = Color.White)
                                ) {
                                    Icon(
                                        imageVector = if (s.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp).align(Alignment.Center),
                                        tint = if (isFavFocused) Color.Black else if (s.isFavorite) Color.Red else Color.White
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                s.genre?.takeIf { it.isNotBlank() }?.let {
                                    Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp)) {
                                        Text(
                                            text = it,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = TvColors.StarGold, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = if (s.rating > 0) "%.1f".format(s.rating) else "N/A",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            Text(
                                text = s.plot ?: "No description available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 20.sp
                            )

                            Spacer(Modifier.height(22.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (resumeEpisode != null) {
                                    var isResFocused by remember { mutableStateOf(false) }
                                    TvSurfaceCard(
                                        onClick = { onPlayEpisode(resumeEpisode!!, "${s.name} - ${resumeEpisode!!.title}") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(playFocusRequester)
                                            .onFocusChanged { isResFocused = it.isFocused }
                                            .height(52.dp),
                                        colors = ClickableSurfaceDefaults.colors(containerColor = AppColors.ArenaOrange.copy(alpha = 0.25f), focusedContainerColor = Color.White),
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(26.dp)),
                                        border = ClickableSurfaceDefaults.border(
                                            focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                        )
                                    ) {
                                        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.History, null, modifier = Modifier.size(20.dp), tint = if (isResFocused) Color.Black else Color.White)
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = stringResource(R.string.generic_resume).uppercase() + " · E${resumeEpisode!!.episodeNumber}",
                                                color = if (isResFocused) Color.Black else Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                InfoGridColumn(label = stringResource(R.string.metadata_director), value = s.director ?: stringResource(R.string.generic_unknown), modifier = Modifier.weight(1f))
                                InfoGridColumn(label = stringResource(R.string.metadata_cast), value = s.cast ?: stringResource(R.string.generic_unknown), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // --- 2. RIGHT PANEL: EPISODES ---
                TvGlassPane(
                    modifier = Modifier.weight(0.45f).fillMaxHeight(),
                    backgroundColor = Color.Black.copy(alpha = 0.4f)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        // Season button shows the CURRENT season name
                        // (e.g. "SEASON 1") instead of a static
                        // "Episodes" label — pressing it opens the list
                        // of available seasons below it. Picking one
                        // closes the list again.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TvPillButton(
                                label = selectedSeason?.season?.name ?: stringResource(R.string.generic_series),
                                isSelected = currentTab == TvSeriesTab.EPISODES,
                                modifier = Modifier.focusRequester(seasonButtonFocusRequester),
                                onClick = {
                                    currentTab = TvSeriesTab.EPISODES
                                    showSeasonPicker = !showSeasonPicker
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            TvPillButton(
                                label = stringResource(R.string.detail_tab_related),
                                isSelected = currentTab == TvSeriesTab.RELATED,
                                onClick = {
                                    currentTab = TvSeriesTab.RELATED
                                    showSeasonPicker = false
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            if (currentTab == TvSeriesTab.EPISODES) {
                                selectedSeason?.episodes?.let { episodes ->
                                    Text(
                                        text = "${episodes.size} " + stringResource(R.string.detail_tab_episodes).uppercase(),
                                        color = TvColors.Primary,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }

                        // The actual season list — only visible while
                        // the picker is open, and only when there's
                        // more than one season to choose between.
                        if (currentTab == TvSeriesTab.EPISODES && showSeasonPicker && seasons.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(seasons) { swE ->
                                    val isSel = selectedSeason?.season?.id == swE.season.id
                                    TvPillButton(
                                        label = swE.season.name,
                                        isSelected = isSel,
                                        onClick = {
                                            viewModel.selectSeason(swE)
                                            showSeasonPicker = false
                                            try { seasonButtonFocusRequester.requestFocus() } catch (e: Exception) {}
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        Spacer(Modifier.height(12.dp))

                        if (currentTab == TvSeriesTab.EPISODES) {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                selectedSeason?.episodes?.let { episodes ->
                                    itemsIndexed(episodes) { _, episode ->
                                        val isHighlighted = resumeEpisode?.id == episode.id
                                        TvEpisodeRowSeriesEnlarged(
                                            episode = episode,
                                            isHighlighted = isHighlighted,
                                            onClick = { onPlayEpisode(episode, s.name) }
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 120.dp),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                items(relatedSeries.take(12)) { related ->
                                    TvRelatedSeriesCardSeriesEnlarged(related) { onSeriesClick(related) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPillButton(
    label: String, 
    isSelected: Boolean, 
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Transparent,
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
            border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
        )
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).align(Alignment.Center),
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun InfoGridColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, color = TvColors.TextTertiary, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvEpisodeRowSeriesEnlarged(
    episode: Episode,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val displayTitle = remember(episode.title) { cleanEpisodeTitle(episode.title) }

    TvSurfaceCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isHighlighted) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.04f),
            focusedContainerColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (isHighlighted) Border(BorderStroke(2.dp, AppColors.ArenaOrange.copy(alpha = 0.85f))) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(110.dp, 64.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                AsyncImage(model = episode.coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    Text(
                        text = episode.episodeNumber.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "E${episode.episodeNumber} · $displayTitle",
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (episode.watchProgress > 0 && episode.totalDuration > 0) {
                    val progress = episode.watchProgress.toFloat() / episode.totalDuration
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(3.dp),
                        color = if (isHighlighted) Color.White else TvColors.Primary,
                        trackColor = (if (isFocused) Color.Black else Color.White).copy(alpha = 0.1f)
                    )
                } else if (!episode.duration.isNullOrBlank()) {
                    Text(text = episode.duration!!, color = if (isFocused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }

            if (isHighlighted) {
                TvBadge(
                    text = stringResource(R.string.generic_status_watching),
                    containerColor = TvColors.Primary,
                    contentColor = Color.Black
                )
                Spacer(Modifier.width(12.dp))
            }

            Spacer(Modifier.width(12.dp))

            // Decorative circular play affordance — NOT a separate
            // focusable target. The whole row is already one TV focus
            // target (TvSurfaceCard); nesting a second independently
            // clickable element inside it would fight the single-focus-
            // per-row convention every other row/list in this app uses.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) Color.Black.copy(alpha = 0.15f) else AppColors.ArenaOrange),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun TvRelatedSeriesCardSeriesEnlarged(series: Series, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSurfaceCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f/3f).onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            AsyncImage(model = series.cover ?: series.backdropUrl, contentDescription = null, modifier = Modifier.fillMaxSize().align(Alignment.Center), contentScale = ContentScale.Crop)
        }
        Text(text = series.name, color = if (isFocused) Color.White else Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}