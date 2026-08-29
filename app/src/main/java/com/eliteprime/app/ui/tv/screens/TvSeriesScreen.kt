package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.SeriesPosterCard
import com.eliteprime.app.ui.screens.series.SeriesViewModel
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.util.DeviceRenderProfile
import com.eliteprime.app.util.LocalRenderTier
import com.eliteprime.app.util.RenderTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val SIDEBAR_WEIGHT_OPEN = 0.34f
private const val SIDEBAR_WEIGHT_CLOSED = 0f
private const val SIDEBAR_ANIM_MS = 220
private const val RETURN_FOCUS_DELAY_MS = SIDEBAR_ANIM_MS + 80L

@Composable
fun TvSeriesScreen(
    onBack: () -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val gridState = rememberLazyGridState()
    var focusedSeries by remember { mutableStateOf<Series?>(null) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
            .map { visibleItems ->
                val lastVisibleItem = visibleItems.lastOrNull() ?: return@map false
                lastVisibleItem.index >= uiState.filteredSeries.size - 8
            }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMore() }
    }

    val categoryFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val firstCategoryRequester = remember { FocusRequester() }
    val seriesFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val firstSeriesRequester = remember { FocusRequester() }
    val sidebarListState = rememberLazyListState()

    // ViewModel-backed state: survives navigation back from details
    val isSidebarHidden by viewModel.isSidebarHidden.collectAsState()
    val isGridFocused by viewModel.isGridFocused.collectAsState()
    var lastSidebarFocusKey by remember { mutableStateOf<Long?>(SeriesViewModel.CAT_ID_RECENTLY_ADDED) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(searchText) { delay(300); viewModel.setSearchQuery(searchText) }

    var hasInitiallyFocused by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.categories) {
        if (uiState.categories.isNotEmpty() && !hasInitiallyFocused) {
            delay(400)
            try {
                val recentlyAddedCat = uiState.categories.find { it.id == SeriesViewModel.CAT_ID_RECENTLY_ADDED }
                val targetRequester = if (recentlyAddedCat != null) {
                    categoryFocusRequesters.getOrPut(recentlyAddedCat.id) { FocusRequester() }
                } else firstCategoryRequester
                targetRequester.requestFocus()
                hasInitiallyFocused = true
            } catch (_: Exception) {}
        }
    }

    val sidebarWeight by animateFloatAsState(
        targetValue = if (isSidebarHidden) SIDEBAR_WEIGHT_CLOSED else SIDEBAR_WEIGHT_OPEN,
        animationSpec = tween(SIDEBAR_ANIM_MS), label = "sidebarWeight"
    )
    val gridWeight = 1f - sidebarWeight

    val selectCategoryOnly: (Category?) -> Unit = { category ->
        if (category != null) viewModel.selectCategory(category)
        viewModel.showSidebar()
        scope.launch { gridState.scrollToItem(0) }
    }

    val enterGridFullScreen: (Category?) -> Unit = { category ->
        if (category != null) viewModel.selectCategory(category)
        viewModel.enterFullScreen()
        scope.launch {
            gridState.scrollToItem(0)
            delay(SIDEBAR_ANIM_MS + 50L)
            try { firstSeriesRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val returnFocusToSidebar: () -> Unit = {
        viewModel.showSidebar()
        scope.launch {
            val key = lastSidebarFocusKey
            val index = uiState.categories.indexOfFirst { it.id == key }
            if (index >= 0) {
                try { sidebarListState.scrollToItem(index) } catch (_: Exception) {}
            }
            delay(RETURN_FOCUS_DELAY_MS)
            val requester = categoryFocusRequesters[key] ?: firstCategoryRequester
            try { requester.requestFocus() }
            catch (_: Exception) { delay(100); try { requester.requestFocus() } catch (_: Exception) {} }
        }
    }

    BackHandler { if (isSidebarHidden) returnFocusToSidebar() else onBack() }

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val tier = LocalRenderTier.current
        val isReducedTier = tier == RenderTier.REDUCED
        val isLite = DeviceRenderProfile.isLiteMode()
        val currentBackdropSeries = focusedSeries ?: uiState.filteredSeries.firstOrNull()

        Box(modifier = Modifier.fillMaxSize()) {
            if (isReducedTier || isLite) {
                AsyncImage(model = currentBackdropSeries?.backdropUrl ?: currentBackdropSeries?.cover,
                    contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alpha = 0.45f)
            } else {
                Crossfade(targetState = currentBackdropSeries, animationSpec = tween(600), label = "seriesBg") { series ->
                    AsyncImage(model = series?.backdropUrl ?: series?.cover,
                        contentDescription = null, modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop, alpha = 0.45f)
                }
            }
            Box(modifier = Modifier.fillMaxSize().background(remember {
                Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 400f)
            }))
        }

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Start) {
            // PANE 1: CATEGORIES + SEARCH
            Column(
                modifier = Modifier.weight(sidebarWeight + 0.001f).fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(top = 24.dp, bottom = 24.dp)
                    .alpha(if (isSidebarHidden) 0f else 1f)
            ) {
                // Search bar
                val searchRequester = remember { FocusRequester() }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, bottom = 16.dp)
                        .height(40.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .focusRequester(searchRequester)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when {
                                event.key == Key.Backspace -> { if (searchText.isNotEmpty()) searchText = searchText.dropLast(1); true }
                                event.utf16CodePoint in 32..126 -> { searchText += event.utf16CodePoint.toChar(); true }
                                else -> false
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = searchText.ifEmpty { "Search series..." },
                            color = if (searchText.isEmpty()) Color.White.copy(alpha = 0.35f) else Color.White,
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.Search, contentDescription = "Search",
                            tint = AppColors.ArenaOrange, modifier = Modifier.size(18.dp))
                    }
                }

                LazyColumn(state = sidebarListState, modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    itemsIndexed(uiState.categories) { index, category ->
                        val requester = categoryFocusRequesters.getOrPut(category.id) { FocusRequester() }

                        LaunchedEffect(category.id) { viewModel.loadCategoryCount(category) }
                        
                        if (index > 0) {
                            val prev = uiState.categories[index - 1]
                            if (prev.isVirtual && !category.isVirtual) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                                    color = Color.White.copy(alpha = 0.15f))
                            }
                        }

                        val icon = when (category.id) {
                            SeriesViewModel.CAT_ID_RECENTLY_ADDED -> Icons.Default.FiberNew
                            SeriesViewModel.CAT_ID_FAVORITES -> Icons.Default.Star
                            SeriesViewModel.CAT_ID_CONTINUE -> Icons.Default.History
                            else -> null
                        }

                        TvCategoryItem(
                            label = category.categoryName.replace("\uD83D\uDD52 ", ""),
                            count = uiState.categoryCounts[category.id],
                            isSelected = category.id == uiState.selectedCategory?.id,
                            icon = icon,
                            modifier = Modifier
                                .focusRequester(requester)
                                .onFocusChanged { if (it.isFocused && !isGridFocused) { lastSidebarFocusKey = category.id } }
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown) {
                                        val isEnterGrid = if (isRtl) it.key == Key.DirectionLeft else it.key == Key.DirectionRight
                                        if (isEnterGrid) { enterGridFullScreen(null); return@onPreviewKeyEvent true }
                                    }
                                    false
                                },
                            onClick = { selectCategoryOnly(category) }
                        )
                    }
                }
            }

            // PANE 2: CONTENT GRID
            val columns = if (isSidebarHidden) 6 else 4
            Column(modifier = Modifier.weight(gridWeight + 0.001f).fillMaxHeight()
                .padding(top = 24.dp, bottom = 24.dp, start = if (isSidebarHidden) 24.dp else 12.dp, end = 24.dp)
            ) {
                LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.filteredSeries, key = { _, s -> s.id }) { index, series ->
                        val requester = if (index == 0) firstSeriesRequester else seriesFocusRequesters.getOrPut(series.id) { FocusRequester() }
                        val isStartColumn = index % columns == 0
                        val totalSeries = uiState.filteredSeries.size
                        val isLastRow = index >= (totalSeries - (totalSeries % columns).let { if (it == 0) columns else it })

                        SeriesPosterCard(series = series,
                            modifier = Modifier
                                .focusRequester(requester)
                                .onFocusChanged {
                                    if (it.isFocused) { focusedSeries = series; viewModel.enterFullScreen() }
                                }
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown) {
                                        if (isStartColumn) {
                                            val isExitGrid = if (isRtl) it.key == Key.DirectionRight else it.key == Key.DirectionLeft
                                            if (isExitGrid) { returnFocusToSidebar(); return@onPreviewKeyEvent true }
                                        }
                                        if (isLastRow && it.key == Key.DirectionDown) return@onPreviewKeyEvent true
                                    }
                                    false
                                },
                            onClick = { onSeriesClick(series) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvCategoryItem(label: String, count: Int?, isSelected: Boolean,
    icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp).onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.White.copy(alpha = 0.1f)),
        border = ClickableSurfaceDefaults.border(
            border = Border.None, focusedBorder = Border(BorderStroke(1.5.dp, AppColors.ArenaOrange)))
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.fillMaxHeight(0.6f).width(3.dp)
                .background(if (isSelected) AppColors.ArenaOrange else Color.Transparent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))
            if (icon != null) {
                Icon(icon, contentDescription = null,
                    tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(label, color = Color.White, fontSize = 15.sp,
                fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (count != null) {
                Text(count.toString(), color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}