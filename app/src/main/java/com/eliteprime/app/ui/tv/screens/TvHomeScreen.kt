package com.eliteprime.app.ui.tv.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.dashboard.TrendingItem
import com.eliteprime.app.ui.theme.AppColors
import kotlinx.coroutines.delay

// Unifies "the thing currently focused, whichever row it came from" into
// one shape the hero backdrop/metadata card can render. Previously only
// the Trending row updated the hero (via TrendingItem), so focusing a
// Continue Watching item left the background frozen on stale trending
// art. Both rows now populate this the same way.
private data class TvHeroInfo(
    val title: String,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val voteAverage: Double?,
    val year: String?,
    val genre: String?,
    val typeLabel: String
)

private fun TrendingItem<*>.toHeroInfo(): TvHeroInfo = TvHeroInfo(
    title = title,
    overview = overview,
    backdropUrl = backdropUrl,
    posterUrl = posterUrl,
    voteAverage = voteAverage,
    year = releaseDate?.take(4),
    genre = genres.firstOrNull(),
    typeLabel = if (localMatch is Movie) "MOVIE" else "SERIES"
)

private fun PlaybackHistory.toHeroInfo(): TvHeroInfo = TvHeroInfo(
    title = name,
    overview = null,
    backdropUrl = backdropUrl,
    posterUrl = posterUrl,
    voteAverage = null,
    year = null,
    genre = null,
    typeLabel = "CONTINUE WATCHING"
)

// No nav rail here anymore — TvMainShell's TvSideNavRail is the one and
// only navigation rail for the whole app, including Home now. This
// screen is content-only, matching every other TV screen (TvLiveTvScreen,
// TvMovieScreen, etc.), which also don't render their own rail.
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

    val trendingItems = uiState.trendingMovies.ifEmpty { uiState.trendingSeries }
    val firstItemRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isTrendingLoading) {
        if (!uiState.isTrendingLoading && trendingItems.isNotEmpty()) {
            delay(500)
            try { firstItemRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    val fallbackHero = trendingItems.firstOrNull()?.toHeroInfo()
    val displayHero = focusedHero ?: fallbackHero

    AppBackground(isTransparent = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            // --- DYNAMIC THEATER BACKDROP ---
            Box(modifier = Modifier.fillMaxSize()) {
                Crossfade(targetState = displayHero, animationSpec = tween(1200), label = "heroBg") { hero ->
                    AsyncImage(
                        model = hero?.backdropUrl ?: hero?.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.3f
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent, Color.Black.copy(alpha = 0.95f)),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
            }

            // --- STRUCTURED LAYOUT ---
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .heightIn(min = 110.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (displayHero != null) {
                        TvHorizontalMetadataCard(displayHero)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    when {
                        uiState.isTrendingLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = AppColors.ArenaOrange)
                                    Spacer(Modifier.height(16.dp))
                                    Text("Fetching latest trends...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                            }
                        }
                        trendingItems.isEmpty() && uiState.continueWatching.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Nothing to show yet",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp)
                            ) {
                                if (trendingItems.isNotEmpty()) {
                                    item {
                                        TvHomeRowContainer(title = stringResource(R.string.home_trending_title)) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
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
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                items(uiState.continueWatching.take(10)) { history ->
                                                    TvMiniHistoryCard(
                                                        history = history,
                                                        modifier = Modifier.onFocusChanged { if (it.isFocused) focusedHero = history.toHeroInfo() },
                                                        onClick = { onResumeHistoryItem(history) }
                                                    )
                                                }
                                                item {
                                                    TvMiniViewAllButton(onClick = onNavigateToContinueWatching)
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
        }
    }
}

@Composable
private fun TvHorizontalMetadataCard(hero: TvHeroInfo) {
    Crossfade(targetState = hero, animationSpec = tween(500), label = "metaFade") { target ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = target.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Fixed-width slots, in a fixed order, so each badge always
            // starts at the same X position — previously this Row only
            // rendered whichever badges had a non-null value, so e.g. an
            // item with no rating shifted year/genre/type left into where
            // the rating used to be. Now every slot is always present;
            // only its content (or lack of it) changes.
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaSlot(width = 60.dp) {
                    if (target.voteAverage != null) {
                        Surface(color = Color(0xFFFFD700), shape = RoundedCornerShape(4.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "%.1f".format(target.voteAverage), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                    }
                }
                MetaSlot(width = 52.dp) {
                    if (target.year != null) {
                        Text(text = target.year, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                MetaSlot(width = 90.dp) {
                    if (target.genre != null) {
                        Text(
                            text = target.genre.uppercase(),
                            color = AppColors.ArenaOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Type label is last in the row, so its own width varying
                // (MOVIE vs SERIES vs CONTINUE WATCHING) doesn't push
                // anything else out of position — nothing comes after it.
                Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = target.typeLabel,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (!target.overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = target.overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun MetaSlot(width: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(modifier = Modifier.width(width), contentAlignment = Alignment.CenterStart) {
        content()
    }
}

@Composable
private fun TvHomeRowContainer(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.4f),
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            // The row's focused item scales up (see focusedScale on
            // TvMiniPosterCard/TvMiniHistoryCard) around its own center,
            // so its top edge moves upward when focused — this padding
            // has to be bigger than that scale-up's top overshoot
            // (~7.5dp at the current size/scale values), not just
            // "enough looking" space, or the title gets covered again
            // whenever the first item is focused.
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
        )
        content()
    }
}

@Composable
private fun TvMiniPosterCard(item: TrendingItem<*>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.width(100.dp).aspectRatio(2f / 3f),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White)))
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
    TvSurface(
        onClick = onClick,
        modifier = modifier.width(160.dp).aspectRatio(16f / 9f).onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White)))
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
                Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(AppColors.ArenaOrange))
            }
        }
    }
}

@Composable
private fun TvMiniViewAllButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = Modifier.size(48.dp).onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ChevronRight, null, tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(24.dp))
        }
    }
}