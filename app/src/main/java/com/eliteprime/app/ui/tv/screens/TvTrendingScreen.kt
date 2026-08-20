package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.dashboard.TrendingItem
import com.eliteprime.app.ui.tv.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TrendingFilter { ALL, MOVIES, SERIES }

@Composable
fun TvTrendingScreen(
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    var selectedFilter by remember { mutableStateOf(TrendingFilter.ALL) }
    var focusedItem by remember { mutableStateOf<TrendingItem<*>?>(null) }
    val gridState = rememberLazyGridState()
    
    val firstFilterRequester = remember { FocusRequester() }
    val firstItemRequester = remember { FocusRequester() }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        delay(500)
        try { firstFilterRequester.requestFocus() } catch (e: Exception) {}
    }

    // Show trending items.
    val filteredList = remember(selectedFilter, uiState.trendingMovies, uiState.trendingSeries) {
        when (selectedFilter) {
            TrendingFilter.ALL -> (uiState.trendingMovies + uiState.trendingSeries).distinctBy { it.tmdbId }.sortedByDescending { it.voteAverage }
            TrendingFilter.MOVIES -> uiState.trendingMovies
            TrendingFilter.SERIES -> uiState.trendingSeries
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. IMMERSIVE BACKDROP ---
        Crossfade(targetState = focusedItem, animationSpec = tween(1000), label = "trendingBg") { item ->
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item?.backdropUrl ?: item?.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            remember {
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                    startY = 300f
                                )
                            }
                        )
                )
            }
        }

        // --- 2. DUAL-PANE LAYOUT ---
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- PANE 1: FILTERS (30%) ---
            TvGlassPane(modifier = Modifier.weight(0.30f).fillMaxHeight()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment, 
                        null, 
                        tint = TvColors.Primary, 
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_trending_title).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(32.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            TvTrendingFilterItem(
                                label = stringResource(R.string.nav_all_trending),
                                isSelected = selectedFilter == TrendingFilter.ALL,
                                modifier = Modifier.focusRequester(firstFilterRequester),
                                onClick = { 
                                    selectedFilter = TrendingFilter.ALL
                                    scope.launch { gridState.scrollToItem(0) }
                                }
                            )
                        }
                        item {
                            TvTrendingFilterItem(
                                label = stringResource(R.string.filter_movies),
                                isSelected = selectedFilter == TrendingFilter.MOVIES,
                                onClick = { 
                                    selectedFilter = TrendingFilter.MOVIES
                                    scope.launch { gridState.scrollToItem(0) }
                                }
                            )
                        }
                        item {
                            TvTrendingFilterItem(
                                label = stringResource(R.string.filter_series),
                                isSelected = selectedFilter == TrendingFilter.SERIES,
                                onClick = { 
                                    selectedFilter = TrendingFilter.SERIES
                                    scope.launch { gridState.scrollToItem(0) }
                                }
                            )
                        }
                    }
                }
            }

            // --- PANE 2: CONTENT GRID (70%) ---
            TvGlassPane(modifier = Modifier.weight(0.70f).fillMaxHeight()) {
                if (uiState.isTrendingLoading && filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TvColors.Primary)
                    }
                } else if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.trending_no_content),
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        itemsIndexed(filteredList) { index, item ->
                            val modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier
                            val cardModifier = modifier.onFocusChanged { if (it.isFocused) focusedItem = item }

                            TvTrendingPosterCard(
                                item = item,
                                modifier = cardModifier,
                                onClick = {
                                    if (item.localMatch is Movie) onMovieClick(item.localMatch)
                                    else if (item.localMatch is Series) onSeriesClick(item.localMatch)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTrendingPosterCard(
    item: TrendingItem<*>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isAvailable = item.localMatch != null

    Column(
        modifier = Modifier.width(134.dp), 
        verticalArrangement = Arrangement.spacedBy(if (isFocused) 14.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TvSurfaceCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(2f/3f).onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = item.posterUrl ?: item.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isAvailable) {
                Surface(
                    color = TvColors.Primary,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(24.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp).padding(4.dp))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text(
                        stringResource(R.string.nav_discover), 
                        color = Color.White.copy(alpha = 0.8f), 
                        fontSize = 8.sp, 
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            text = item.title,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TvTrendingFilterItem(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label, 
                color = if (isFocused) Color.Black else Color.White, 
                fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
