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
import androidx.tv.material3.ClickableSurfaceDefaults
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.SeriesPosterCard
import com.eliteprime.app.ui.screens.series.SeriesViewModel
import com.eliteprime.app.ui.tv.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    
    val categoryFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val firstCategoryRequester = remember { FocusRequester() }
    val seriesFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. DYNAMIC THEATER BACKDROP ---
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(targetState = focusedSeries, animationSpec = tween(1000), label = "seriesBg") { series ->
                AsyncImage(
                    model = series?.backdropUrl ?: series?.cover,
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
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 400f
                        )
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)
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
                    itemsIndexed(uiState.filteredSeries, key = { _, s -> s.id }) { index, series ->
                        val requester = seriesFocusRequesters.getOrPut(series.id) { FocusRequester() }
                        val isLastInRow = (index + 1) % 4 == 0 // Updated for 4 columns

                        SeriesPosterCard(
                            series = series,
                            modifier = Modifier
                                    .focusRequester(requester)
                                    .onFocusChanged { if (it.isFocused) focusedSeries = series }
                                    .focusProperties { 
                                        if (isLastInRow) right = FocusRequester.Cancel
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
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .heightIn(min = 40.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.25f) else Color.Transparent,
            focusedContainerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), 
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label, 
                color = if (isFocused) Color.Black else Color.White, 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
