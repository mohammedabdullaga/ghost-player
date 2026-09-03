package com.eliteprime.app.ui.screens.series

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
import androidx.compose.ui.unit.sp
import com.eliteprime.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.player.PlayerViewMode
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.util.FocusRestoration
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
fun SeriesScreen(
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lastFocusedCategoryId by viewModel.lastFocusedCategoryId.collectAsState()
    val lastFocusedItemId by viewModel.lastFocusedItemId.collectAsState()
    val globalMode by viewModel.globalPlayerState.mode.collectAsState()
    val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }

    val seriesGridState = rememberLazyGridState()
    val context = LocalContext.current
    val isTv = context.isTelevisionDevice()
    val seriesFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val scope = rememberCoroutineScope()

    // Restore focus on resume — uses ViewModel-backed state via shared utility
    FocusRestoration(
        targetCategoryId = lastFocusedCategoryId,
        targetItemId = lastFocusedItemId,
        selectedCategory = uiState.selectedCategory,
        onSelectCategory = { catId ->
            uiState.categories.find { it.id == catId }?.let { viewModel.selectCategoryForRestore(it) }
        },
        categoryListState = null,
        gridState = seriesGridState,
        categoryFocusRequesters = emptyMap<Long, FocusRequester>(),
        itemFocusRequesters = seriesFocusRequesters,
        findItemIndex = { id -> uiState.filteredSeries.indexOfFirst { it.id == id } },
        trigger = globalMode != PlayerViewMode.FULLSCREEN
    )



    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }

    // Favorite add/remove confirm dialog
    var seriesToToggleFav by remember { mutableStateOf<Series?>(null) }
    if (seriesToToggleFav != null) {
        val target = seriesToToggleFav!!
        val isFav = target.isFavorite
        AlertDialog(
            onDismissRequest = { seriesToToggleFav = null },
            title = { Text(if (isFav) stringResource(R.string.fav_remove) else stringResource(R.string.fav_add), fontWeight = FontWeight.Bold) },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleFavorite(target)
                    seriesToToggleFav = null
                }) {
                    Text(
                        if (isFav) stringResource(R.string.generic_remove) else stringResource(R.string.fav_add),
                        color = if (isFav) Color(0xFFEF5350) else AppColors.ArenaOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { seriesToToggleFav = null }) {
                    Text(stringResource(R.string.generic_no), color = Color.White.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF12151B),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f)
        )
    }

    // Continue Watching remove confirm dialog
    var seriesToRemove by remember { mutableStateOf<Series?>(null) }
    if (seriesToRemove != null) {
        val target = seriesToRemove!!
        AlertDialog(
            onDismissRequest = { seriesToRemove = null },
            title = { Text(stringResource(R.string.history_remove_title), fontWeight = FontWeight.Bold) },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFromHistory(target)
                    seriesToRemove = null
                }) {
                    Text(stringResource(R.string.generic_remove), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { seriesToRemove = null }) {
                    Text(stringResource(R.string.generic_no), color = Color.White.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF12151B),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f)
        )
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            VodTopNavBar(
                currentTime = currentTime.value,
                title = stringResource(R.string.nav_series).uppercase(),
                onBack = onBack,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToLiveTv = onNavigateToLiveTv,
                onNavigateToMovies = onNavigateToMovies
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
                                    onClick = { viewModel.selectCategory(category); viewModel.recordFocusedCategory(category.id); scope.launch { seriesGridState.scrollToItem(0) } }
                                )
                            }
                        }
                    }
                }

                // --- Pane 2: Content Grid ---
                VodPaneSurface(modifier = Modifier.weight(0.7f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        VodPaneHeader(title = uiState.selectedCategory?.categoryName ?: stringResource(R.string.nav_series).uppercase())
                        LazyVerticalGrid(
                            state = seriesGridState,
                            columns = GridCells.Fixed(5),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(VodSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(VodSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(uiState.filteredSeries.distinctBy { it.id }, key = { index, s -> "${s.id}_$index" }) { index, series ->
                                if (index >= uiState.filteredSeries.size - 12) {
                                    SideEffect {
                                        viewModel.loadMore()
                                    }
                                }
                                val focusRequester = seriesFocusRequesters.getOrPut(series.id) { FocusRequester() }
                                SeriesPosterCard(
                                    series = series,
                                    modifier = Modifier
                                        .focusRequester(focusRequester)
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                viewModel.recordFocusedItem(series.id)
                                            }
                                        },
                                    onClick = { onSeriesClick(series) },
                                    onLongClick = {
                                        when (uiState.selectedCategory?.id) {
                                            SeriesViewModel.CAT_ID_CONTINUE -> seriesToRemove = series
                                            else -> seriesToToggleFav = series
                                        }
                                    }
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
    onNavigateToMovies: () -> Unit
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
                NavTabButton(label = stringResource(R.string.nav_movies), isActive = false, onClick = onNavigateToMovies)
                NavTabButton(label = stringResource(R.string.nav_series), isActive = true, onClick = {})
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
            modifier = Modifier.size(40.dp).background(if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.35f), CircleShape)
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


