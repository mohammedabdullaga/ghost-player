package com.eliteprime.app.ui.tv.screens

import android.app.Activity
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.player.PlayerViewMode
import com.eliteprime.app.ui.components.VodPosterCard
import com.eliteprime.app.ui.components.TvConfirmDialog
import com.eliteprime.app.ui.screens.movies.MovieViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.ui.util.FocusRestoration
import com.eliteprime.app.util.DeviceRenderProfile
import com.eliteprime.app.util.LocalRenderTier
import com.eliteprime.app.util.RenderTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val SIDEBAR_WEIGHT_OPEN = 0.34f
private const val SIDEBAR_WEIGHT_CLOSED = 0f
private const val SIDEBAR_ANIM_MS = 300
private const val RETURN_FOCUS_DELAY_MS = 350L

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
    
    val categoryFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val movieFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val firstCategoryRequester = remember { FocusRequester() }
    val firstMovieRequester = remember { FocusRequester() }
    val sidebarListState = rememberLazyListState()

    // ViewModel-backed state: survives navigation back from details
    val isSidebarHidden by viewModel.isSidebarHidden.collectAsState()
    val isGridFocused by viewModel.isGridFocused.collectAsState()
    val lastFocusedCategoryId by viewModel.lastFocusedCategoryId.collectAsState()
    val lastFocusedItemId by viewModel.lastFocusedItemId.collectAsState()
    val globalMode by viewModel.globalPlayerState.mode.collectAsState()
    var lastSidebarFocusKey by remember { mutableStateOf(lastFocusedCategoryId ?: MovieViewModel.CAT_ID_RECENTLY_ADDED) }
    var searchText by remember { mutableStateOf(viewModel.searchQuery.value) }
    var movieToRemove by remember { mutableStateOf<Movie?>(null) }

    LaunchedEffect(searchText) { delay(300); viewModel.setSearchQuery(searchText) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filterNotNull()
            .filter { it >= uiState.filteredMovies.size - 8 }
            .collect { viewModel.loadMore() }
    }

    var hasInitiallyFocused by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.categories) {
        if (uiState.categories.isNotEmpty() && !hasInitiallyFocused) {
            delay(400)
            try {
                // If a grid item was previously focused (e.g. returning from details screen),
                // do NOT force-focus the sidebar category.
                if (lastFocusedItemId == null) {
                    val targetCatId = lastFocusedCategoryId
                        ?: uiState.categories.find { it.id == MovieViewModel.CAT_ID_RECENTLY_ADDED }?.id
                        ?: uiState.categories.firstOrNull()?.id
                    val targetRequester = if (targetCatId != null) {
                        categoryFocusRequesters.getOrPut(targetCatId) { FocusRequester() }
                    } else firstCategoryRequester
                    targetRequester.requestFocus()
                }
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
        if (searchText.isNotEmpty()) { searchText = "" }
        if (category != null) viewModel.selectCategory(category)
        viewModel.showSidebar()
    }

    val enterGridFullScreen: (Category?) -> Unit = { category ->
        if (searchText.isNotEmpty()) { searchText = "" }
        if (category != null) viewModel.selectCategory(category)
        viewModel.enterFullScreen()
        scope.launch {
            delay(SIDEBAR_ANIM_MS + 50L)
            try { firstMovieRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val returnFocusToSidebar: () -> Unit = {
        // Use ViewModel-persisted focus, fall back to local cache, then default
        val key = lastFocusedCategoryId ?: lastSidebarFocusKey ?: MovieViewModel.CAT_ID_RECENTLY_ADDED
        viewModel.showSidebar()
        scope.launch {
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

    // Restore focus on resume using ViewModel-backed state via shared utility
    FocusRestoration(
        targetCategoryId = lastFocusedCategoryId,
        targetItemId = lastFocusedItemId,
        selectedCategory = uiState.selectedCategory,
        onSelectCategory = { catId ->
            uiState.categories.find { it.id == catId }?.let { cat ->
                viewModel.selectCategoryForRestore(cat)
                viewModel.showSidebar()
            }
        },
        categoryListState = sidebarListState,
        gridState = gridState,
        categoryFocusRequesters = categoryFocusRequesters,
        itemFocusRequesters = movieFocusRequesters,
        findItemIndex = { id -> uiState.filteredMovies.indexOfFirst { it.id == id } },
        trigger = globalMode != PlayerViewMode.FULLSCREEN
    )

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    if (movieToRemove != null) {
        val target = movieToRemove!!
        TvConfirmDialog(
            title = stringResource(R.string.history_remove_title),
            message = stringResource(R.string.history_remove_msg, target.name),
            confirmText = stringResource(R.string.generic_remove),
            confirmColor = Color(0xFFEF5350),
            icon = Icons.Default.Delete,
            onConfirm = {
                viewModel.removeFromHistory(target)
                movieToRemove = null
            },
            onDismiss = { movieToRemove = null }
        )
    }

    // Toggle favorite confirm dialog
    var movieToToggleFav by remember { mutableStateOf<Movie?>(null) }
    if (movieToToggleFav != null) {
        val target = movieToToggleFav!!
        val isFav = target.isFavorite
        TvConfirmDialog(
            title = if (isFav) stringResource(R.string.fav_remove) else stringResource(R.string.fav_add),
            message = target.name,
            confirmText = if (isFav) stringResource(R.string.generic_remove) else stringResource(R.string.fav_add),
            confirmColor = if (isFav) Color(0xFFEF5350) else AppColors.ArenaOrange,
            icon = Icons.Default.Star,
            onConfirm = {
                viewModel.toggleFavorite(target)
                movieToToggleFav = null
            },
            onDismiss = { movieToToggleFav = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val tier = LocalRenderTier.current
        val isReducedTier = tier == RenderTier.REDUCED
        val isLite = DeviceRenderProfile.isLiteMode()
        val currentBackdropMovie = focusedMovie ?: uiState.filteredMovies.firstOrNull()

        Box(modifier = Modifier.fillMaxSize()) {
            if (isReducedTier || isLite) {
                AsyncImage(model = currentBackdropMovie?.backdropUrl ?: currentBackdropMovie?.streamIcon,
                    contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alpha = 0.45f)
            } else {
                Crossfade(targetState = currentBackdropMovie, animationSpec = tween(600), label = "movieBg") { movie ->
                    AsyncImage(model = movie?.backdropUrl ?: movie?.streamIcon,
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
                    .padding(top = 16.dp, bottom = 16.dp)
                    .alpha(if (isSidebarHidden) 0f else 1f)
            ) {
                // Search bar — keyboard only opens on OK (login-screen pattern)
                var isSearchFocused by remember { mutableStateOf(false) }
                var isSearchEditing by remember { mutableStateOf(false) }
                val keyboardController = LocalSoftwareKeyboardController.current

                LaunchedEffect(isSearchEditing) {
                    if (isSearchEditing) { delay(100); keyboardController?.show() }
                }

                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 20.dp, bottom = 8.dp)
                        .height(40.dp)
                        .onFocusChanged {
                            isSearchFocused = it.isFocused
                            if (!it.isFocused) isSearchEditing = false
                        }
                        .onKeyEvent { keyEvent ->
                            if (isSearchFocused && !isSearchEditing) {
                                if (keyEvent.type == KeyEventType.KeyUp &&
                                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                                    isSearchEditing = true
                                    return@onKeyEvent true
                                }
                            }
                            false
                        }
                        .border(
                            1.dp,
                            if (isSearchFocused) AppColors.ArenaOrange else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        ),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchText.isEmpty()) {
                                    Text(
                                        text = "Search movies...",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 10.sp
                                    )
                                }
                                innerTextField()
                            }
                            Icon(
                                Icons.Default.Search,
                                null,
                                tint = AppColors.ArenaOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    readOnly = !isSearchEditing,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        hintLocales = LocaleList(Locale("ar"), Locale("en"))
                    )
                )

                LazyColumn(state = sidebarListState, modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    itemsIndexed(uiState.categories) { index, category ->
                        val requester = categoryFocusRequesters.getOrPut(category.id) { FocusRequester() }

                        LaunchedEffect(category.id) { viewModel.loadCategoryCount(category) }

                        val icon = when (category.id) {
                            MovieViewModel.CAT_ID_RECENTLY_ADDED -> Icons.Default.FiberNew
                            MovieViewModel.CAT_ID_FAVORITES -> Icons.Default.Star
                            MovieViewModel.CAT_ID_CONTINUE -> Icons.Default.History
                            else -> null
                        }

                        TvCategoryItem(
                            label = category.categoryName.replace("\uD83D\uDD52 ", "").replace("\u2605 ", ""),
                            count = uiState.categoryCounts[category.id],
                            isSelected = category.id == uiState.selectedCategory?.id,
                            icon = icon,
                            modifier = Modifier
                                .focusRequester(requester)
                                .onFocusChanged {
                                    if (it.isFocused && !isGridFocused && lastFocusedItemId == null) {
                                        lastSidebarFocusKey = category.id
                                        viewModel.recordFocusedCategory(category.id)
                                        if (searchText.isEmpty()) {
                                            viewModel.selectCategory(category)
                                        }
                                    }
                                }
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown) {
                                        val isEnterGrid = if (isRtl) it.key == Key.DirectionLeft else it.key == Key.DirectionRight
                                        if (isEnterGrid) {
                                            if (searchText.isNotEmpty()) { searchText = "" }
                                            enterGridFullScreen(category)
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    false
                                },
                            onClick = {
                                if (searchText.isNotEmpty()) { searchText = "" }
                                viewModel.selectCategory(category)
                                viewModel.recordFocusedCategory(category.id)
                                lastSidebarFocusKey = category.id
                                viewModel.showSidebar()
                                scope.launch { gridState.scrollToItem(0) }
                            }
                        )
                    }
                }
            }

            // PANE 2: CONTENT GRID
            val columns = if (isSidebarHidden) 6 else 4
            Column(modifier = Modifier.weight(gridWeight + 0.001f).fillMaxHeight()
                .padding(top = 24.dp, bottom = 24.dp, start = if (isSidebarHidden) 16.dp else 12.dp, end = 16.dp)
            ) {
                LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.filteredMovies.distinctBy { it.id }, key = { _, m -> m.id }) { index, movie ->
                        val requester = movieFocusRequesters.getOrPut(movie.id) {
                            if (index == 0) firstMovieRequester else FocusRequester()
                        }
                        val isStartColumn = index % columns == 0
                        val totalMovies = uiState.filteredMovies.size
                        val isLastRow = index >= (totalMovies - (totalMovies % columns).let { if (it == 0) columns else it })

                        VodPosterCard(movie = movie,
                            modifier = Modifier
                                .focusRequester(requester)
                                .onFocusChanged {
                                    if (it.isFocused) { focusedMovie = movie; viewModel.enterFullScreen(); viewModel.recordFocusedItem(movie.id) }
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
                            onClick = { onMovieClick(movie) },
                            onLongClick = {
                                when (uiState.selectedCategory?.id) {
                                    MovieViewModel.CAT_ID_CONTINUE -> movieToRemove = movie
                                    else -> movieToToggleFav = movie
                                }
                            }
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
        modifier = modifier.fillMaxWidth().height(40.dp).onFocusChanged { isFocused = it.isFocused },
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
