package com.eliteprime.app.ui.tv.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.dashboard.TrendingItem
import com.eliteprime.app.ui.tv.core.*
import kotlinx.coroutines.delay

private data class TvHeroInfo(
    val title: String,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val voteAverage: Double?,
    val year: String?,
    val genre: String?,
    val typeLabelRes: Int // Using resource ID
)

private fun TrendingItem<*>.toHeroInfo(): TvHeroInfo = TvHeroInfo(
    title = title,
    overview = overview,
    backdropUrl = backdropUrl,
    posterUrl = posterUrl,
    voteAverage = voteAverage,
    year = releaseDate?.take(4),
    genre = genres.firstOrNull(),
    typeLabelRes = if (localMatch is Movie) R.string.generic_movie else R.string.generic_series
)

private fun PlaybackHistory.toHeroInfo(): TvHeroInfo = TvHeroInfo(
    title = name,
    overview = null,
    backdropUrl = backdropUrl,
    posterUrl = posterUrl,
    voteAverage = null,
    year = null,
    genre = null,
    typeLabelRes = R.string.history_title
)

@Composable
fun TvHomeScreen(
    onNavigateToTrending: () -> Unit,
    onNavigateToContinueWatching: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onResumeHistoryItem: (PlaybackHistory) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var focusedHero by remember { mutableStateOf<TvHeroInfo?>(null) }
    
    // Tab State: "movie" or "series"
    var selectedTrendingType by remember { mutableStateOf("movie") }
    
    val trendingItems = if (selectedTrendingType == "movie") uiState.trendingMovies else uiState.trendingSeries
    val firstItemRequester = remember { FocusRequester() }

    // Update hero when tab changes to the first item of the new list
    LaunchedEffect(selectedTrendingType, uiState.trendingMovies, uiState.trendingSeries) {
        val firstItem = trendingItems.firstOrNull()
        if (firstItem != null) {
            focusedHero = firstItem.toHeroInfo()
        }
    }

    val displayHero = focusedHero ?: trendingItems.firstOrNull()?.toHeroInfo()

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. DYNAMIC THEATER BACKDROP ---
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(targetState = displayHero, animationSpec = tween(1200), label = "heroBg") { hero ->
                AsyncImage(
                    model = hero?.backdropUrl ?: hero?.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent),
                            endX = 800f
                        )
                    )
            )
        }

        // --- 2. STRUCTURED LAYOUT ---
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
            
            // A. TOP METADATA PANEL (Positioned below Shell's Top Bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 0.dp) // Reduced bottom padding
                    .height(160.dp),
                contentAlignment = Alignment.TopStart
            ) {
                if (displayHero != null) {
                    TvHeroMetadataPanel(displayHero)
                }
            }

            // B. CONTENT ROWS
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Top // Changed from Center to bring it closer to top
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp), // Increased slightly for breathing room
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    // TRENDING ROW WITH TABS
                    item {
                        TvHomeRowContainer(
                            title = stringResource(R.string.home_trending_title),
                            headerContent = {
                                Row(
                                    modifier = Modifier.padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TvTrendingTab(
                                        label = stringResource(R.string.nav_movies),
                                        isSelected = selectedTrendingType == "movie",
                                        onClick = { selectedTrendingType = "movie" }
                                    )
                                    TvTrendingTab(
                                        label = stringResource(R.string.nav_series),
                                        isSelected = selectedTrendingType == "series",
                                        onClick = { selectedTrendingType = "series" }
                                    )
                                }
                            }
                        ) {
                            if (trendingItems.isEmpty() && uiState.isTrendingLoading) {
                                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = TvColors.Primary)
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp) // INCREASED PADDING FOR FOCUS ROOM
                                ) {
                                    itemsIndexed(trendingItems) { index, item ->
                                        val modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier
                                        TvMiniPosterCard(
                                            item = item,
                                            modifier = modifier.onFocusChanged { if (it.isFocused) focusedHero = item.toHeroInfo() },
                                            onClick = {
                                                if (item.localMatch is Movie) onMovieClick(item.localMatch)
                                                else if (item.localMatch is Series) onSeriesClick(item.localMatch)
                                                else onNavigateToTrending()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.continueWatching.isNotEmpty()) {
                        item {
                            TvHomeRowContainer(title = stringResource(R.string.home_continue_watching)) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    contentPadding = PaddingValues(horizontal = 24.dp) // INCREASED PADDING FOR FOCUS ROOM
                                ) {
                                    uiState.continueWatching.take(10).forEach { history ->
                                        item {
                                            TvMiniHistoryCard(
                                                history = history, 
                                                modifier = Modifier.onFocusChanged { if (it.isFocused) focusedHero = history.toHeroInfo() },
                                                onClick = { onResumeHistoryItem(history) }
                                            )
                                        }
                                    }
                                    item {
                                        TvViewAllButton(onClick = onNavigateToContinueWatching)
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
private fun TvTrendingTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .height(32.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp))
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.Black else if (isSelected) TvColors.Primary else Color.White.copy(alpha = 0.6f),
            fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun TvHeroMetadataPanel(hero: TvHeroInfo) {
    Crossfade(targetState = hero, animationSpec = tween(500), label = "metaFade") { target ->
        TvGlassPane(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.White.copy(alpha = 0.05f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    TvMetadataInfo(
                        rating = target.voteAverage,
                        year = target.year,
                        genre = target.genre,
                        typeLabel = stringResource(target.typeLabelRes)
                    )
                }

                if (!target.overview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = target.overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TvHomeRowContainer(
    title: String, 
    headerContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp) // INCREASED START PADDING
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TvColors.TextTertiary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            headerContent?.invoke()
        }
        content()
    }
}

@Composable
private fun TvMiniPosterCard(item: TrendingItem<*>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TvSurfaceCard(
        onClick = onClick,
        modifier = modifier.width(125.dp).aspectRatio(2f / 3f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp))
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun TvMiniHistoryCard(history: PlaybackHistory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = modifier.width(195.dp).aspectRatio(16f / 9f).onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = history.backdropUrl ?: history.posterUrl,
                contentDescription = history.name,
                modifier = Modifier.fillMaxSize().alpha(0.8f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = history.name, 
                    color = if (isFocused) Color.Black else Color.White, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 11.sp, 
                    maxLines = 1,
                    modifier = Modifier.padding(8.dp)
                )
            }
            val progress = if (history.durationMs > 0) history.lastPositionMs.toFloat() / history.durationMs else 0f
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f))) {
                Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(TvColors.Primary))
            }
        }
    }
}

@Composable
private fun TvViewAllButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = Modifier.size(64.dp).onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape)
    ) {
        Icon(
            Icons.Default.ChevronRight, 
            null, 
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}
