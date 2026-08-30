package com.eliteprime.app.ui.tv.screens

import android.app.Activity
import androidx.compose.animation.Crossfade
import android.util.Log
import androidx.activity.compose.BackHandler
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
import com.eliteprime.app.ui.components.TvExitDialog
import com.eliteprime.app.ui.components.TvConfirmDialog
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.dashboard.TrendingItem
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.tv.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Border
import com.eliteprime.app.util.DeviceRenderProfile
import com.eliteprime.app.util.LocalRenderTier
import com.eliteprime.app.util.RenderTier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val activity = context as? Activity
    var showExitDialog by remember { mutableStateOf(false) }
    var historyToRemove by remember { mutableStateOf<PlaybackHistory?>(null) }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        TvExitDialog(
            onConfirm = { activity?.finish() },
            onDismiss = { showExitDialog = false }
        )
    }

    if (historyToRemove != null) {
        val target = historyToRemove!!
        Log.d("TvHome", "Showing removal dialog for: ${target.name}")
        TvConfirmDialog(
            title = stringResource(R.string.history_remove_title),
            message = stringResource(R.string.history_remove_msg, target.name),
            confirmText = stringResource(R.string.generic_remove),
            confirmColor = Color(0xFFEF5350),
            icon = Icons.Default.Delete,
            onConfirm = {
                Log.d("TvHome", "Confirmed removal of: ${target.name}")
                viewModel.removeFromHistory(target)
                historyToRemove = null
            },
            onDismiss = { 
                Log.d("TvHome", "Cancelled removal")
                historyToRemove = null 
            }
        )
    }

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

    val tier = LocalRenderTier.current
    val isReducedTier = tier == RenderTier.REDUCED
    val isLite = DeviceRenderProfile.isLiteMode()

    val content: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // --- HERO METADATA (Upper section) ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 64.dp, end = 24.dp, bottom = 32.dp) // INCREASED START PADDING
                ) {
                    if (displayHero != null) {
                        TvHeroMetadataPanel(displayHero)
                    } else {
                        Spacer(Modifier.height(160.dp))
                    }
                }
            }

            // --- MAIN CONTENT ROWS ---
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. TRENDING ROW (with Tabs)
                    TvHomeRowContainer(
                        title = stringResource(R.string.home_trending_title),
                        headerContent = {
                            Row(
                                modifier = Modifier.padding(start = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TvTrendingTab(
                                    label = stringResource(R.string.filter_movies),
                                    isSelected = selectedTrendingType == "movie",
                                    onClick = { selectedTrendingType = "movie" }
                                )
                                TvTrendingTab(
                                    label = stringResource(R.string.filter_series),
                                    isSelected = selectedTrendingType == "series",
                                    onClick = { selectedTrendingType = "series" }
                                )
                            }
                        }
                    ) {
                        val showLoading = uiState.isTrendingLoading && trendingItems.isEmpty()
                        
                        LaunchedEffect(trendingItems.size) {
                            if (trendingItems.isNotEmpty()) {
                                Log.e("HOME_TRACE", "UI: Showing TRENDING LIST (" + trendingItems.size + " items) at " + System.currentTimeMillis())
                            }
                        }

                        if (showLoading) {
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

                    if (uiState.continueWatching.isNotEmpty()) {
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
                                            onClick = { onResumeHistoryItem(history) },
                                            onLongClick = { historyToRemove = history }
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isLite) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isReducedTier) {
                    AsyncImage(
                        model = displayHero?.backdropUrl ?: displayHero?.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )
                } else {
                    Crossfade(targetState = displayHero, animationSpec = tween(1000), label = "heroBg") { hero ->
                        AsyncImage(
                            model = hero?.backdropUrl ?: hero?.posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.45f
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)), startY = 400f)))
                Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent), endX = 1000f)))
            }
            content()
        } else {
            AppBackground {
                content()
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
            containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
            border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
        )
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
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
    val tier = LocalRenderTier.current
    val isReducedTier = tier == RenderTier.REDUCED

    TvSurfaceCard(
        onClick = onClick,
        modifier = modifier.width(125.dp).aspectRatio(2f / 3f),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (isReducedTier) 1.05f else 1.1f),
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
private fun TvMiniHistoryCard(
    history: PlaybackHistory, 
    modifier: Modifier = Modifier, 
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val tier = LocalRenderTier.current
    val isReducedTier = tier == RenderTier.REDUCED
    var isFocused by remember { mutableStateOf(false) }

    var longPressJob: Job? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    TvSurfaceCard(
        onClick = onClick,
        modifier = modifier
            .width(195.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            if (longPressJob == null) {
                                longPressJob = scope.launch {
                                    delay(800) // Threshold for long press
                                    onLongClick()
                                    longPressJob = null
                                }
                            }
                        }
                        KeyEventType.KeyUp -> {
                            longPressJob?.cancel()
                            longPressJob = null
                        }
                    }
                }
                false
            },
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (isReducedTier) 1.05f else 1.1f),
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
                modifier = Modifier.fillMaxSize().background(remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))) }),
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
