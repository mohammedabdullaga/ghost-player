package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.ui.components.VodPosterCard
import com.eliteprime.app.ui.screens.movies.MovieViewModel
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.util.DeviceRenderProfile
import com.eliteprime.app.util.LocalRenderTier
import com.eliteprime.app.util.RenderTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val SIDEBAR_WEIGHT_OPEN = 0.28f
private const val SIDEBAR_WEIGHT_CLOSED = 0f
private const val SIDEBAR_ANIM_MS = 220
private const val RETURN_FOCUS_DELAY_MS = SIDEBAR_ANIM_MS + 80L

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
    val firstMovieRequester = remember { FocusRequester() }

    var isGridFocused by remember { mutableStateOf(false) }
    var isSidebarHidden by remember { mutableStateOf(false) }
    var lastSidebarFocusKey by remember { mutableStateOf<Long?>(MovieViewModel.CAT_ID_RECENTLY_ADDED) }

    // Initial focus on Recently Added
    LaunchedEffect(uiState.categories) {
        if (uiState.categories.isNotEmpty() && !isGridFocused) {
            delay(400)
            try {
                val recentlyAddedCat = uiState.categories.find { it.id == MovieViewModel.CAT_ID_RECENTLY_ADDED }
                if (recentlyAddedCat != null) {
                    categoryFocusRequesters.getOrPut(recentlyAddedCat.id) { FocusRequester() }.requestFocus()
                }
            } catch (e: Exception) {}
        }
    }

    val sidebarWeight by animateFloatAsState(
        targetValue = if (isSidebarHidden) SIDEBAR_WEIGHT_CLOSED else SIDEBAR_WEIGHT_OPEN,
        animationSpec = tween(SIDEBAR_ANIM_MS),
        label = "sidebarWeight"
    )
    val gridWeight = 1f - sidebarWeight

    // "Fast View": Updates grid content but keeps focus on category.
    val selectCategoryOnly: (Category?) -> Unit = { category ->
        if (category != null) {
            viewModel.selectCategory(category)
        }
        isSidebarHidden = false
        isGridFocused = false
        scope.launch { gridState.scrollToItem(0) }
    }

    // "Full Screen": Updates content, hides sidebar, and moves focus to grid.
    val enterGridFullScreen: (Category?) -> Unit = { category ->
        if (category != null) {
            viewModel.selectCategory(category)
        }
        isSidebarHidden = true
        isGridFocused = true
        scope.launch {
            gridState.scrollToItem(0)
            delay(150)
            try { firstMovieRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    val returnFocusToSidebar: () -> Unit = {
        isGridFocused = false
        isSidebarHidden = false
        scope.launch {
            delay(RETURN_FOCUS_DELAY_MS)
            try {
                val key = lastSidebarFocusKey
                val requester = if (key != null) categoryFocusRequesters[key] ?: firstCategoryRequester else firstCategoryRequester
                requester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    BackHandler {
        if (isGridFocused) {
            returnFocusToSidebar()
        } else {
            onBack()
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- 1. DYNAMIC THEATER BACKDROP (Disabled in Lite Mode for instant rendering) ---
        val tier = LocalRenderTier.current
        val isReducedTier = tier == RenderTier.REDUCED
        val isLite = DeviceRenderProfile.isLiteMode()

        val displayBackdrop = focusedMovie?.let { it.backdropUrl ?: it.streamIcon }
            ?: uiState.filteredMovies.firstOrNull()?.let { it.backdropUrl ?: it.streamIcon }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isReducedTier || isLite) {
                AsyncImage(
                    model = displayBackdrop,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f
                )
            } else {
                Crossfade(targetState = displayBackdrop, animationSpec = tween(600), label = "movieBg") { backdrop ->
                    AsyncImage(
                        model = backdrop,
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

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Start
        ) {
            // --- PANE 1: CATEGORIES ---
            Column(
                modifier = Modifier
                    .weight(sidebarWeight + 0.001f)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(top = 24.dp, bottom = 24.dp)
                    .alpha(if (isSidebarHidden) 0f else 1f)
            ) {
                Text(
                    text = "CATEGORIES",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    itemsIndexed(uiState.categories) { index, category ->
                        val requester = categoryFocusRequesters.getOrPut(category.id) { FocusRequester() }
                        
                        // Smart divider between virtual items and provider items
                        if (index > 0) {
                            val prev = uiState.categories[index - 1]
                            if (prev.isVirtual && !category.isVirtual) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                                    color = Color.White.copy(alpha = 0.15f)
                                )
                            }
                        }

                        val icon = when (category.id) {
                            MovieViewModel.CAT_ID_RECENTLY_ADDED -> Icons.Default.FiberNew
                            MovieViewModel.CAT_ID_FAVORITES -> Icons.Default.Star
                            MovieViewModel.CAT_ID_CONTINUE -> Icons.Default.History
                            else -> null
                        }

                        TvCategoryItem(
                            label = category.categoryName.replace("🕒 ", ""),
                            count = uiState.categoryCounts[category.id],
                            isSelected = category.id == uiState.selectedCategory?.id,
                            icon = icon,
                            modifier = Modifier
                                .focusRequester(requester)
                                .onFocusChanged { if (it.isFocused) {
                                    isGridFocused = false
                                    isSidebarHidden = false
                                    lastSidebarFocusKey = category.id
                                }}
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown) {
                                        val isEnterGrid = if (isRtl) it.key == Key.DirectionLeft else it.key == Key.DirectionRight
                                        if (isEnterGrid) {
                                            enterGridFullScreen(category)
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    false
                                },
                            onClick = { selectCategoryOnly(category) }
                        )
                    }
                }
            }

            // --- PANE 2: CONTENT GRID ---
            val columns = if (isSidebarHidden) 6 else 4
            Column(modifier = Modifier.weight(gridWeight + 0.001f).fillMaxHeight().padding(top = 24.dp, bottom = 24.dp, start = if (isSidebarHidden) 24.dp else 0.dp, end = 24.dp)) {
                Text(
                    text = uiState.selectedCategory?.categoryName?.replace("🕒 ", "") ?: "Movies",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp, start = 8.dp)
                )

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    itemsIndexed(uiState.filteredMovies, key = { _, m -> m.id }) { index, movie ->
                        val requester = if (index == 0) firstMovieRequester else movieFocusRequesters.getOrPut(movie.id) { FocusRequester() }
                        val isStartColumn = index % columns == 0
                        val totalMovies = uiState.filteredMovies.size
                        val isLastRow = index >= (totalMovies - (totalMovies % columns).let { if (it == 0) columns else it })

                        VodPosterCard(
                            movie = movie,
                            modifier = Modifier
                                .focusRequester(requester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        focusedMovie = movie
                                        isGridFocused = true
                                        isSidebarHidden = true 
                                    }
                                }
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown) {
                                        if (isStartColumn) {
                                            val isExitGrid = if (isRtl) it.key == Key.DirectionRight else it.key == Key.DirectionLeft
                                            if (isExitGrid) {
                                                returnFocusToSidebar()
                                                return@onPreviewKeyEvent true
                                            }
                                        }
                                        if (isLastRow && it.key == Key.DirectionDown) {
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    false
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
    count: Int?,
    isSelected: Boolean,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    TvSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected && !isFocused) Color.White else Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.White.copy(alpha = 0.1f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(1.5.dp, Color.Red))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .width(3.dp)
                    .background(if (isSelected && !isFocused) Color.Red else Color.Transparent, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected && !isFocused) Color.Black else if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = label,
                color = if (isSelected && !isFocused) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (count != null) {
                Text(
                    text = count.toString(),
                    color = if (isSelected && !isFocused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}
