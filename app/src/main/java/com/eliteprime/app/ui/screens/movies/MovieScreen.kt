package com.eliteprime.app.ui.screens.movies

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteprime.app.R
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private object VodSpacing {
    val lg = 20.dp
    val xs = 8.dp
    val md = 8.dp
}

private object VodPalette {
    val paneFill = Color.Black.copy(alpha = 0.65f)
    val paneBorder = AppColors.ArenaOrange.copy(alpha = 0.18f)
    val hairline = Color.White.copy(alpha = 0.1f)
    val textSecondary = Color.White.copy(alpha = 0.6f)
}

@Composable
fun MovieScreen(
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    viewModel: MovieViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }

    val movieGridState = rememberLazyGridState()
    val context = LocalContext.current
    val isTv = context.isTelevisionDevice()
    val movieFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    var lastFocusedMovieId by rememberSaveable { mutableStateOf<Long?>(null) }
    val topBarHomeFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Restore focus on initial load AND when returning from other screens
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (isTv && uiState.filteredMovies.isNotEmpty()) {
            val targetId = lastFocusedMovieId ?: uiState.filteredMovies.first().id
            val index = uiState.filteredMovies.indexOfFirst { it.id == targetId }
            
            if (index >= 0) {
                scope.launch {
                    repeat(10) {
                        try {
                            if (!movieGridState.layoutInfo.visibleItemsInfo.any { it.index == index }) {
                                movieGridState.scrollToItem(index)
                            }
                            delay(50)
                            movieFocusRequesters[targetId]?.requestFocus()
                            return@launch
                        } catch (e: Exception) {}
                        delay(100)
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.filteredMovies) {
        if (isTv && uiState.filteredMovies.isNotEmpty()) {
            val targetId = lastFocusedMovieId ?: uiState.filteredMovies.first().id
            val index = uiState.filteredMovies.indexOfFirst { it.id == targetId }
            
            if (index >= 0) {
                // Ensure the item is scrolled into view before requesting focus
                if (!movieGridState.layoutInfo.visibleItemsInfo.any { it.index == index }) {
                    movieGridState.scrollToItem(index)
                }
                // Use a minimal delay to allow the grid to attach the newly scrolled item
                delay(50)
                movieFocusRequesters[targetId]?.requestFocus()
            }
        }
    }

    // Reset scroll when category changes
    LaunchedEffect(uiState.selectedCategory) {
        movieGridState.scrollToItem(0)
        // Reset last focused when category changes to ensure we focus the first item of new category
        lastFocusedMovieId = null 
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Nav Bar (Optional on TV if Sidebar is used, but kept for Mobile/Tablet)
            VodTopNavBar(
                currentTime = currentTime.value,
                title = stringResource(R.string.nav_movies).uppercase(),
                onBack = onBack,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToLiveTv = onNavigateToLiveTv,
                onNavigateToSeries = onNavigateToSeries
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = VodSpacing.lg, vertical = VodSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(VodSpacing.lg)
            ) {
                // --- Pane 1: Categories ---
                VodPaneSurface(modifier = Modifier.weight(0.3f)) {
                    Column {
                        VodPaneHeader(title = stringResource(R.string.generic_groups))
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            items(uiState.categories) { category ->
                                LaunchedEffect(category.id) { viewModel.loadCategoryCount(category) }
                                CategoryTab(
                                    category = category,
                                    count = uiState.categoryCounts[category.id],
                                    isSelected = category.id == uiState.selectedCategory?.id,
                                    onClick = { viewModel.selectCategory(category) }
                                )
                            }
                        }
                    }
                }

                // --- Pane 2: Content Grid ---
                VodPaneSurface(modifier = Modifier.weight(0.7f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        VodPaneHeader(title = uiState.selectedCategory?.categoryName ?: stringResource(R.string.nav_movies).uppercase())
                        LazyVerticalGrid(
                            state = movieGridState,
                            columns = GridCells.Fixed(5),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(VodSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(VodSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(uiState.filteredMovies, key = { _, m -> m.id }) { index, movie ->
                                if (index >= uiState.filteredMovies.size - 12) {
                                    SideEffect {
                                        viewModel.loadMore()
                                    }
                                }
                                val focusRequester = movieFocusRequesters.getOrPut(movie.id) { FocusRequester() }
                                VodPosterCard(
                                    movie = movie,
                                    modifier = Modifier
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { 
                                            if (it.isFocused) {
                                                lastFocusedMovieId = movie.id 
                                            }
                                        },
                                    onClick = { onMovieClick(movie) },
                                    onLongClick = { viewModel.toggleFavorite(movie) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodTopNavBar(
    currentTime: String,
    title: String,
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToSeries: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArenaLiveLogo(textSize = 20.sp)

            Spacer(modifier = Modifier.width(32.dp))

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTabButton(label = stringResource(R.string.nav_home), isActive = false, onClick = onBack)
                NavTabButton(label = stringResource(R.string.nav_live_tv), isActive = false, onClick = onNavigateToLiveTv)
                NavTabButton(label = stringResource(R.string.nav_movies), isActive = true, onClick = {})
                NavTabButton(label = stringResource(R.string.nav_series), isActive = false, onClick = onNavigateToSeries)
            }

            VodRoundIconButton(
                icon = Icons.Default.Search, 
                contentDescription = stringResource(R.string.nav_search), 
                onClick = onNavigateToSearch
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(text = currentTime, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(VodPalette.hairline))
    }
}

@Composable
private fun VodPaneSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = VodPalette.paneFill,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, VodPalette.paneBorder)
    ) {
        content()
    }
}

@Composable
private fun VodPaneHeader(title: String) {
    Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        Text(text = title.uppercase(), color = VodPalette.textSecondary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(VodPalette.hairline))
}

@Composable
private fun VodRoundIconButton(
    icon: ImageVector, 
    contentDescription: String?, 
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(2.dp, if (isActive) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent)),
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    icon, 
                    contentDescription = contentDescription, 
                    tint = if (isFocused) Color.Black else if (isActive) Color.White else Color.White.copy(alpha = 0.8f), 
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp).background(if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.35f),
                CircleShape
            )
        ) {
            Icon(
                icon, 
                contentDescription = contentDescription, 
                tint = if (isActive) AppColors.ArenaOrange else Color.White, 
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


