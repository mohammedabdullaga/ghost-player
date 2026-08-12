package com.eliteprime.app.ui.screens.details

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.eliteprime.app.R
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.data.local.entity.Episode
import com.eliteprime.app.data.local.entity.Season
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.domain.model.DownloadItem
import com.eliteprime.app.domain.model.DownloadStatus
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.formatPositionMs
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SeriesDetailTab { EPISODES, RELATED }

@Composable
fun SeriesDetailsScreen(
    onBack: () -> Unit,
    onPlayEpisode: (Episode, String) -> Unit,
    onPlayLocal: (Long, String, String) -> Unit = { _, _, _ -> },
    onSeriesClick: (Series) -> Unit = {},
    viewModel: SeriesDetailsViewModel = hiltViewModel()
) {
    val seriesState by viewModel.series.collectAsState()
    val series = seriesState
    val seasons by viewModel.seasons.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val resumeEpisode by viewModel.resumeEpisode.collectAsState()
    val episodeDownloads by viewModel.episodeDownloads.collectAsState()
    val relatedSeries by viewModel.relatedSeries.collectAsState()
    val isTv = LocalContext.current.isTelevisionDevice()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    
    val listState = rememberLazyListState()
    var currentTab by remember { mutableStateOf(SeriesDetailTab.EPISODES) }
    val episodesTabFocusRequester = remember { FocusRequester() }
    
    // Focus restoration states
    val episodeFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    var lastFocusedEpisodeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }

    // Logic to request initial focus or restore focus
    LaunchedEffect(selectedSeason, resumeEpisode, currentTab) {
        if (isTv && currentTab == SeriesDetailTab.EPISODES) {
            val season = selectedSeason
            if (season != null && season.episodes.isNotEmpty()) {
                val targetId = if (!initialFocusDone) {
                    resumeEpisode?.id ?: season.episodes.first().id
                } else {
                    lastFocusedEpisodeId ?: resumeEpisode?.id ?: season.episodes.first().id
                }

                if (targetId != null) {
                    scope.launch {
                        // Repeat check to ensure item is composed and ready for focus
                        repeat(5) {
                            val index = season.episodes.indexOfFirst { it.id == targetId }
                            if (index >= 0) {
                                // Scroll to it first
                                if (!listState.layoutInfo.visibleItemsInfo.any { it.index == index + 2 }) {
                                    listState.scrollToItem(index + 2)
                                }
                                delay(100)
                                try {
                                    episodeFocusRequesters[targetId]?.requestFocus()
                                    initialFocusDone = true
                                    return@launch
                                } catch (e: Exception) {}
                            }
                            delay(200)
                        }
                    }
                }
            }
        }
    }

    // Restore focus on every resume (e.g., coming back from player)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (isTv && currentTab == SeriesDetailTab.EPISODES) {
            val season = selectedSeason
            if (season != null && season.episodes.isNotEmpty()) {
                val targetId = lastFocusedEpisodeId ?: resumeEpisode?.id ?: season.episodes.first().id
                if (targetId != null) {
                    scope.launch {
                        repeat(5) {
                            val index = season.episodes.indexOfFirst { it.id == targetId }
                            if (index >= 0) {
                                if (!listState.layoutInfo.visibleItemsInfo.any { it.index == index + 2 }) {
                                    listState.scrollToItem(index + 2)
                                }
                                delay(100)
                                try {
                                    episodeFocusRequesters[targetId]?.requestFocus()
                                    return@launch
                                } catch (e: Exception) {}
                            }
                            delay(200)
                        }
                    }
                }
            }
        }
    }

    // Scroll to resume episode
    LaunchedEffect(selectedSeason, resumeEpisode) {
        val swE = selectedSeason
        val resume = resumeEpisode
        if (swE != null && resume != null && currentTab == SeriesDetailTab.EPISODES) {
            val index = swE.episodes.indexOfFirst { it.id == resume.id }
            if (index >= 0) {
                listState.animateScrollToItem(index + 2)
            }
        }
    }

    if (series == null) {
        Box(modifier = Modifier.fillMaxSize().background(DetailSurface.backdrop), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.ArenaOrange, strokeWidth = 2.5.dp)
        }
        return
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        listOf(DetailSurface.backdrop, DetailSurface.backdrop.copy(alpha = 0.75f), DetailSurface.backdrop.copy(alpha = 0.95f))
                    )
                )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                PosterPanel(
                    posterUrl = series.cover ?: series.backdropUrl, 
                    onBack = onBack, 
                    modifier = Modifier.weight(0.38f),
                    isTv = isTv
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .padding(start = DetailSpacing.md, end = DetailSpacing.xl, top = DetailSpacing.xl, bottom = DetailSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DetailSpacing.sm)
                ) {
                    item {
                        InfoCard {
                            Text(
                                text = series.genre?.uppercase() ?: stringResource(R.string.generic_series), 
                                style = EyebrowStyle, 
                                color = AppColors.ArenaOrange
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Text(
                                    text = series.name,
                                    color = DetailSurface.textPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 28.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                DetailFavButton(
                                    isFavorite = series.isFavorite,
                                    onClick = { viewModel.toggleFavorite() },
                                    isTv = isTv
                                )
                            }

                            if (resumeEpisode != null) {
                                val dl = episodeDownloads[resumeEpisode!!.id]
                                val isDl = dl?.status == DownloadStatus.COMPLETED
                                val hasProgress = resumeEpisode!!.watchProgress > 10000L
                                val buttonText = when {
                                    isDl -> stringResource(R.string.player_play_downloaded_episode, resumeEpisode!!.seasonNumber ?: 0, resumeEpisode!!.episodeNumber ?: 0)
                                    hasProgress -> stringResource(R.string.player_resume_episode_from, resumeEpisode!!.seasonNumber ?: 0, resumeEpisode!!.episodeNumber ?: 0, formatPositionMs(resumeEpisode!!.watchProgress))
                                    else -> stringResource(R.string.player_play_episode, resumeEpisode!!.seasonNumber ?: 0, resumeEpisode!!.episodeNumber ?: 0)
                                }

                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    if (isTv) {
                                        TvSurface(
                                            onClick = { 
                                                if (isDl) onPlayLocal(resumeEpisode!!.id, dl.outputUri ?: dl.outputDisplayPath!!, "${series.name} - ${resumeEpisode!!.title}") 
                                                else onPlayEpisode(resumeEpisode!!, "${series.name} - ${resumeEpisode!!.title}") 
                                            },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = if (isDl) Color(0xFF4CAF50) else AppColors.ArenaOrange,
                                                focusedContainerColor = Color.White
                                            ),
                                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                                        ) {
                                            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                Icon(Icons.Default.PlayArrow, null, tint = Color.Unspecified)
                                                Spacer(Modifier.width(8.dp))
                                                Text(buttonText, fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.Unspecified)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { 
                                                if (isDl) onPlayLocal(resumeEpisode!!.id, dl.outputUri ?: dl.outputDisplayPath!!, "${series.name} - ${resumeEpisode!!.title}") 
                                                else onPlayEpisode(resumeEpisode!!, "${series.name} - ${resumeEpisode!!.title}") 
                                            },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if (isDl) Color(0xFF4CAF50) else AppColors.ArenaOrange),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(buttonText, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            TagRow(genre = null, rating = series.rating)
                            
                            if (!series.plot.isNullOrBlank()) {
                                Text(
                                    text = series.plot!!,
                                    color = DetailSurface.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            MetadataGrid(
                                listOfNotNull(
                                    series.releaseDate?.take(4)?.let { stringResource(R.string.metadata_year) to it },
                                    series.director?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.metadata_director) to it },
                                    series.cast?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.metadata_cast) to it }
                                )
                            )
                        }
                    }

                    item {
                        DetailTabSection(
                            seasons = seasons,
                            selectedSeason = selectedSeason,
                            onSeasonSelected = { viewModel.selectSeason(it) },
                            currentTab = currentTab,
                            onTabChange = { currentTab = it },
                            isTv = isTv,
                            episodesTabFocusRequester = episodesTabFocusRequester
                        )
                    }

                    if (currentTab == SeriesDetailTab.EPISODES) {
                        selectedSeason?.let { swE ->
                            itemsIndexed(swE.episodes) { index, episode ->
                                val isHighlighted = resumeEpisode?.id == episode.id
                                val dl = episodeDownloads[episode.id]
                                val focusRequester = episodeFocusRequesters.getOrPut(episode.id) { FocusRequester() }
                                EpisodeRow(
                                    episode = episode,
                                    index = index + 1,
                                    isHighlighted = isHighlighted,
                                    downloadItem = dl,
                                    fallbackImageUrl = series.cover ?: series.backdropUrl,
                                    onClick = { 
                                        if (dl?.status == DownloadStatus.COMPLETED) onPlayLocal(episode.id, dl.outputUri ?: dl.outputDisplayPath!!, "${series.name} - ${episode.title}")
                                        else onPlayEpisode(episode, "${series.name} - ${episode.title}") 
                                    },
                                    onDownloadClick = { viewModel.downloadEpisode(episode) },
                                    isTv = isTv,
                                    focusRequester = focusRequester,
                                    onFocused = { lastFocusedEpisodeId = episode.id }
                                )
                            }
                        }
                    } else {
                        if (relatedSeries.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.detail_no_related_series), color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        } else {
                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                    maxItemsInEachRow = if (isTv) 5 else 3
                                ) {
                                    relatedSeries.forEach { related ->
                                        RelatedPosterCard(
                                            imageUrl = related.cover ?: related.backdropUrl,
                                            title = related.name,
                                            onClick = { onSeriesClick(related) }
                                        )
                                    }
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
private fun DetailTabSection(
    seasons: List<SeasonWithEpisodes>,
    selectedSeason: SeasonWithEpisodes?,
    onSeasonSelected: (SeasonWithEpisodes) -> Unit,
    currentTab: SeriesDetailTab,
    onTabChange: (SeriesDetailTab) -> Unit,
    isTv: Boolean,
    episodesTabFocusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.wrapContentSize()) {
            var expanded by remember { mutableStateOf(false) }
            val isActive = currentTab == SeriesDetailTab.EPISODES
            
            val content = @Composable {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isActive && selectedSeason != null) selectedSeason.season.name else stringResource(R.string.detail_tab_episodes),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            if (isTv) {
                TvSurface(
                    onClick = { 
                        if (isActive) expanded = true
                        else onTabChange(SeriesDetailTab.EPISODES)
                    },
                    modifier = Modifier.height(42.dp).focusRequester(episodesTabFocusRequester),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isActive) AppColors.FocusBrown.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = AppColors.FocusBrown
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
                ) {
                    content()
                }
            } else {
                Surface(
                    onClick = { 
                        if (isActive) expanded = true
                        else onTabChange(SeriesDetailTab.EPISODES)
                    },
                    color = if (isActive) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    content()
                }
            }

            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false }, 
                modifier = Modifier.background(Color(0xFF14171C))
            ) {
                seasons.forEach { swE ->
                    DropdownMenuItem(
                        text = { Text(swE.season.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                        onClick = { 
                            onSeasonSelected(swE)
                            onTabChange(SeriesDetailTab.EPISODES)
                            expanded = false 
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        val isRelatedActive = currentTab == SeriesDetailTab.RELATED
        if (isTv) {
            TvSurface(
                onClick = { onTabChange(SeriesDetailTab.RELATED) },
                modifier = Modifier.height(42.dp),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isRelatedActive) AppColors.FocusBrown.copy(alpha = 0.9f) else Color.Transparent,
                    focusedContainerColor = AppColors.FocusBrown
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.detail_tab_related), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        } else {
            Surface(
                onClick = { onTabChange(SeriesDetailTab.RELATED) },
                modifier = Modifier.height(42.dp),
                color = if (isRelatedActive) AppColors.ArenaOrange else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isRelatedActive) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f))
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.detail_tab_related), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }

        if (currentTab == SeriesDetailTab.EPISODES && selectedSeason != null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(), 
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "${selectedSeason.episodes.size} EPISODES",
                    color = AppColors.ArenaOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PosterPanel(posterUrl: String?, onBack: () -> Unit, modifier: Modifier = Modifier, isTv: Boolean) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(DetailSpacing.md)
            .clip(RoundedCornerShape(DetailRadius.xl))
            .border(BorderStroke(1.dp, DetailSurface.hairline), RoundedCornerShape(DetailRadius.xl))
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.85f)
                )
            )
        )
        
        if (isTv) {
            TvSurface(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(DetailSpacing.md).size(40.dp),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
                ),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            MobileBackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(DetailSpacing.md))
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = DetailSurface.cardFill,
        shape = RoundedCornerShape(DetailRadius.lg),
        border = BorderStroke(1.dp, DetailSurface.hairline)
    ) {
        Column(
            modifier = Modifier.padding(DetailSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DetailSpacing.md),
            content = content
        )
    }
}

@Composable
private fun DetailFavButton(isFavorite: Boolean, onClick: () -> Unit, isTv: Boolean) {
    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) AppColors.ArenaOrange else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .background(DetailSurface.cardFillElevated, CircleShape)
                .border(BorderStroke(1.dp, DetailSurface.hairline), CircleShape)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) AppColors.ArenaOrange else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TagRow(genre: String?, rating: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DetailSpacing.xs)) {
        if (rating > 0) {
            Surface(
                color = DetailSurface.cardFillElevated,
                shape = RoundedCornerShape(DetailRadius.sm),
                border = BorderStroke(1.dp, DetailSurface.hairline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = DetailSpacing.sm, vertical = DetailSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "%.1f".format(rating), color = DetailSurface.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetadataGrid(items: List<Pair<String, String>>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(DetailSpacing.xs)) {
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DetailSurface.hairline))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                rowItems.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(text = label, color = DetailSurface.textTertiary, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.6.sp)
                        Text(text = value, color = DetailSurface.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    index: Int,
    isHighlighted: Boolean,
    downloadItem: DownloadItem?,
    fallbackImageUrl: String?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {}
) {
    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { if (it.isFocused) onFocused() },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isHighlighted) AppColors.ArenaOrange.copy(alpha = 0.1f) else DetailSurface.cardFill,
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(DetailRadius.md)),
            border = ClickableSurfaceDefaults.border(
                border = if (isHighlighted) Border(BorderStroke(1.dp, AppColors.ArenaOrange)) else Border.None,
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            EpisodeRowContent(episode, index, isHighlighted, downloadItem, fallbackImageUrl, onDownloadClick, onClick)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(DetailRadius.md),
            color = if (isHighlighted) AppColors.ArenaOrange.copy(alpha = 0.1f) else DetailSurface.cardFill,
            border = BorderStroke(1.dp, if (isHighlighted) AppColors.ArenaOrange else DetailSurface.hairline)
        ) {
            EpisodeRowContent(episode, index, isHighlighted, downloadItem, fallbackImageUrl, onDownloadClick, onClick)
        }
    }
}

@Composable
private fun EpisodeRowContent(episode: Episode, index: Int, isHighlighted: Boolean, downloadItem: DownloadItem?, fallbackImageUrl: String?, onDownloadClick: () -> Unit, onPlayClick: () -> Unit) {
    val isDownloaded = downloadItem?.status == DownloadStatus.COMPLETED
    val isDownloading = downloadItem?.status == DownloadStatus.DOWNLOADING || downloadItem?.status == DownloadStatus.PENDING

    Row(modifier = Modifier.fillMaxSize().padding(DetailSpacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DetailSpacing.sm)) {
        Box(modifier = Modifier.fillMaxHeight().aspectRatio(1.6f).clip(RoundedCornerShape(DetailRadius.sm)).background(Color.Black)) {
            AsyncImage(
                model = episode.coverUrl ?: fallbackImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (isHighlighted) 1f else 0.8f)
            )
            Surface(
                color = if (isHighlighted) AppColors.ArenaOrange else Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(bottomEnd = DetailRadius.sm),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = index.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "E${episode.episodeNumber} · ${episode.title}",
                color = if (isHighlighted) AppColors.ArenaOrange else DetailSurface.textPrimary,
                fontWeight = if (isHighlighted) FontWeight.Black else FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (isDownloading) {
                val dlProgress = downloadItem!!.totalBytes?.let { if (it > 0) downloadItem.bytesWritten.toFloat() / it else 0f } ?: 0f
                LinearProgressIndicator(
                    progress = { dlProgress },
                    modifier = Modifier.fillMaxWidth(0.8f).height(2.dp).padding(top = 4.dp),
                    color = AppColors.ArenaOrange,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            } else if (episode.watchProgress > 0 && episode.totalDuration > 0) {
                val epProgress = (episode.watchProgress.toFloat() / episode.totalDuration).coerceIn(0f, 1f)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { epProgress },
                    modifier = Modifier.fillMaxWidth(0.6f).height(2.dp),
                    color = AppColors.ArenaOrange,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            } else if (!episode.duration.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = episode.duration!!, color = DetailSurface.textTertiary, fontSize = 11.sp)
            }
        }

        if (isHighlighted) {
            StatusPill(label = stringResource(R.string.generic_status_watching), containerColor = AppColors.ArenaOrange.copy(alpha = 0.2f), contentColor = AppColors.ArenaOrange)
        }

        if (isDownloaded) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
            }
        } else if (!isDownloading) {
            IconButton(onClick = {
                onDownloadClick()
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Download, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }

        Surface(onClick = onPlayClick, modifier = Modifier.size(32.dp), shape = CircleShape, color = if (isDownloaded) Color(0xFF4CAF50) else AppColors.ArenaOrange) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = if (isDownloaded) Icons.Default.FileDownloadDone else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RelatedPosterCard(imageUrl: String?, title: String, onClick: () -> Unit) {
    val isTv = LocalContext.current.isTelevisionDevice()
    Column(
        modifier = Modifier.width(110.dp),
        verticalArrangement = Arrangement.spacedBy(DetailSpacing.xxs)
    ) {
        if (isTv) {
            TvSurface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(DetailRadius.md)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.2f),
                    focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                )
            ) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        } else {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                shape = RoundedCornerShape(DetailRadius.md),
                color = DetailSurface.cardFill,
                border = BorderStroke(1.dp, DetailSurface.hairline)
            ) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(text = title, color = DetailSurface.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
