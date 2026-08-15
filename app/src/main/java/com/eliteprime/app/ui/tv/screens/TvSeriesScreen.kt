package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.SeriesPosterCard
import com.eliteprime.app.ui.screens.series.SeriesViewModel
import com.eliteprime.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object TvSeriesPalette {
    val paneFill = Color.Black.copy(alpha = 0.65f)
    val paneBorder = Color.White.copy(alpha = 0.12f)
    val hairline = Color.White.copy(alpha = 0.1f)
    val textSecondary = Color.White.copy(alpha = 0.6f)
    val selectionOrange = Color(0xFFFF8C00)
}

@Composable
fun TvSeriesScreen(
    onBack: () -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    val gridState = rememberLazyGridState()
    var hoveredCategory by remember { mutableStateOf<Category?>(null) }
    
    val categoryFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val firstCategoryRequester = remember { FocusRequester() }
    val seriesFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }

    BackHandler { onBack() }

    // Initial Focus
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            delay(200)
            val targetId = uiState.selectedCategory?.id ?: -1L
            categoryFocusRequesters[targetId]?.requestFocus() ?: firstCategoryRequester.requestFocus()
        }
    }

    AppBackground(isTransparent = true) {
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- PANE 1: Categories ---
            TvSeriesPaneSurface(modifier = Modifier.weight(0.28f)) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        Text(text = stringResource(R.string.generic_groups).uppercase(), color = TvSeriesPalette.textSecondary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TvSeriesPalette.hairline))

                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                        itemsIndexed(uiState.categories) { index, category ->
                            val requester = if (index == 0) firstCategoryRequester else categoryFocusRequesters.getOrPut(category.id) { FocusRequester() }
                            
                            TvSeriesCategoryItem(
                                category = category,
                                isSelected = category.id == uiState.selectedCategory?.id,
                                modifier = Modifier.focusRequester(requester),
                                onFocused = { hoveredCategory = category },
                                onClick = { 
                                    viewModel.selectCategory(category)
                                    scope.launch { gridState.scrollToItem(0) }
                                }
                            )
                        }
                    }
                }
            }

            // --- PANE 2: Content Grid ---
            TvSeriesPaneSurface(modifier = Modifier.weight(0.72f)) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        Text(text = (uiState.selectedCategory?.categoryName ?: hoveredCategory?.categoryName ?: "").uppercase(), color = TvSeriesPalette.textSecondary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TvSeriesPalette.hairline))
                    
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        itemsIndexed(uiState.filteredSeries, key = { _, s -> s.id }) { _, series ->
                            val requester = seriesFocusRequesters.getOrPut(series.id) { FocusRequester() }
                            SeriesPosterCard(
                                series = series,
                                modifier = Modifier.focusRequester(requester),
                                onClick = { onSeriesClick(series) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSeriesCategoryItem(
    category: Category,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) onFocused() 
            },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvSeriesPalette.selectionOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.45f),
            focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(3.dp).height(18.dp).background(if (isSelected || isFocused) TvSeriesPalette.selectionOrange else Color.Transparent, CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = category.categoryName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvSeriesPaneSurface(
    modifier: Modifier = Modifier, 
    content: @Composable () -> Unit
) {
    TvSurface(
        modifier = modifier.fillMaxHeight(),
        onClick = {},
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvSeriesPalette.paneFill,
            focusedContainerColor = TvSeriesPalette.paneFill
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, TvSeriesPalette.paneBorder)),
            focusedBorder = Border(BorderStroke(1.dp, TvSeriesPalette.paneBorder))
        )
    ) {
        content()
    }
}
