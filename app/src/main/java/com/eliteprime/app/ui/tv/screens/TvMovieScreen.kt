package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.VodPosterCard
import com.eliteprime.app.ui.screens.movies.MovieViewModel
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.util.LocalRenderTier
import com.eliteprime.app.util.RenderTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Composable
fun TvMovieScreen(
    onBack: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    viewModel: MovieViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    val gridState = rememberLazyGridState()
    var focusedMovie by remember { mutableStateOf<Movie?>(null) }
    
    // Pagination trigger
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
            .map { visibleItems ->
                val lastVisibleItem = visibleItems.lastOrNull() ?: return@map false
                lastVisibleItem.index >= uiState.filteredMovies.size - 8
            }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                viewModel.loadMore()
            }
    }
    
    val categoryFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val firstCategoryRequester = remember { FocusRequester() }
    val movieFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }

    BackHandler { onBack() }

    val tier = LocalRenderTier.current
    val isReducedTier = tier == RenderTier.REDUCED

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. DYNAMIC THEATER BACKDROP ---
        Box(modifier = Modifier.fillMaxSize()) {
            if (isReducedTier) {
                AsyncImage(
                    model = focusedMovie?.backdropUrl ?: focusedMovie?.streamIcon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f
                )
            } else {
                Crossfade(targetState = focusedMovie, animationSpec = tween(600), label = "movieBg") { movie ->
                    AsyncImage(
                        model = movie?.backdropUrl ?: movie?.streamIcon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        remember {
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 400f
                            )
                        }
                    )
            )
        }

        // --- 2. CLEAN DUAL-PANE LAYOUT ---
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp) // Reduced from 24.dp
        ) {
            // --- PANE 1: CATEGORIES (Matched to Live TV Width: 30%) ---
            TvGlassPane(modifier = Modifier.weight(0.30f).fillMaxHeight()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(), 
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.categories) { index, category ->
                        val requester = if (index == 0) firstCategoryRequester else categoryFocusRequesters.getOrPut(category.id) { FocusRequester() }
                        
                        TvCategoryItem(
                            label = category.categoryName,
                            isSelected = category.id == uiState.selectedCategory?.id,
                            modifier = Modifier.focusRequester(requester),
                            onClick = { 
                                viewModel.selectCategory(category)
                                scope.launch { gridState.scrollToItem(0) }
                            }
                        )
                    }
                }
            }

            // --- PANE 2: CONTENT GRID (70%) ---
            TvGlassPane(modifier = Modifier.weight(0.70f).fillMaxHeight()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(4), // Changed to 4 columns
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(uiState.filteredMovies, key = { _, m -> m.id }) { index, movie ->
                        val requester = movieFocusRequesters.getOrPut(movie.id) { FocusRequester() }
                        val isLastInRow = (index + 1) % 4 == 0 // Updated for 4 columns
                        
                        VodPosterCard(
                            movie = movie,
                            modifier = Modifier
                                .focusRequester(requester)
                                .onFocusChanged { if (it.isFocused) focusedMovie = movie }
                                .focusProperties { 
                                    if (isLastInRow) right = FocusRequester.Cancel
                                },
                            onClick = { onMovieClick(movie) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvCategoryItem(
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
            .heightIn(min = 44.dp)
            .onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.045f),
            focusedContainerColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
            border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(if (isSelected || isFocused) AppColors.ArenaOrange else Color.Transparent)
            )
            Text(
                text = label,
                color = if (isFocused) Color.Black else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
