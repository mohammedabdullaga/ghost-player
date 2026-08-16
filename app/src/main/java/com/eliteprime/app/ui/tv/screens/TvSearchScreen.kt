package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.search.SearchViewModel
import com.eliteprime.app.ui.tv.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SearchFilter { ALL, MOVIES, SERIES, LIVE_TV }

@Composable
fun TvSearchScreen(
    onBack: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onChannelClick: (LiveStream) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
    val searchFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        delay(300)
        searchFocusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
            // A. SEARCH HEADER & INPUT
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.nav_search).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.width(32.dp))
                
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester),
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.White.copy(alpha = 0.3f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TvColors.Primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // B. FILTER CHIPS
            if (query.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvSearchFilterChip(
                        label = stringResource(R.string.filter_all),
                        icon = Icons.Default.AllInclusive,
                        isSelected = selectedFilter == SearchFilter.ALL,
                        onClick = { selectedFilter = SearchFilter.ALL }
                    )
                    TvSearchFilterChip(
                        label = stringResource(R.string.filter_movies),
                        icon = Icons.Default.Movie,
                        isSelected = selectedFilter == SearchFilter.MOVIES,
                        onClick = { selectedFilter = SearchFilter.MOVIES }
                    )
                    TvSearchFilterChip(
                        label = stringResource(R.string.filter_series),
                        icon = Icons.Default.VideoLibrary,
                        isSelected = selectedFilter == SearchFilter.SERIES,
                        onClick = { selectedFilter = SearchFilter.SERIES }
                    )
                    TvSearchFilterChip(
                        label = "LIVE TV",
                        icon = Icons.Default.LiveTv,
                        isSelected = selectedFilter == SearchFilter.LIVE_TV,
                        onClick = { selectedFilter = SearchFilter.LIVE_TV }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // C. RESULTS GRID/LIST
            TvGlassPane(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, tint = TvColors.TextTertiary, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.search_idle_desc), color = TvColors.TextTertiary, fontSize = 14.sp)
                        }
                    }
                } else if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TvColors.Primary)
                    }
                } else if (uiState.movies.isEmpty() && uiState.series.isEmpty() && uiState.channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.search_no_results), color = TvColors.TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // 1. MOVIES SECTION
                        if ((selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.MOVIES) && uiState.movies.isNotEmpty()) {
                            item { SearchSectionLabel(stringResource(R.string.nav_movies), uiState.movies.size) }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(uiState.movies) { movie ->
                                        TvSearchPosterCard(
                                            title = movie.name,
                                            posterUrl = movie.streamIcon ?: movie.backdropUrl,
                                            onClick = { onMovieClick(movie) }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. SERIES SECTION
                        if ((selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.SERIES) && uiState.series.isNotEmpty()) {
                            item { SearchSectionLabel(stringResource(R.string.nav_series), uiState.series.size) }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(uiState.series) { series ->
                                        TvSearchPosterCard(
                                            title = series.name,
                                            posterUrl = series.cover ?: series.backdropUrl,
                                            onClick = { onSeriesClick(series) }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. LIVE TV SECTION
                        if ((selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.LIVE_TV) && uiState.channels.isNotEmpty()) {
                            item { SearchSectionLabel("LIVE TV", uiState.channels.size) }
                            items(uiState.channels) { channel ->
                                TvSearchRow(channel.name, "LIVE TV") { onChannelClick(channel) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSearchFilterChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = if (isFocused) Color.Black else if (isSelected) TvColors.Primary else Color.White, modifier = Modifier.size(16.dp))
            Text(
                text = label, 
                color = if (isFocused) Color.Black else if (isSelected) TvColors.Primary else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchSectionLabel(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title.uppercase(), color = TvColors.Primary, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.2.sp)
        Text(text = "$count RESULTS", color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun TvSearchPosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(130.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSurfaceCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f/3f).onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = title,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvSearchRow(
    title: String,
    type: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.03f),
            focusedContainerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, color = if (isFocused) Color.Black else Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            TvBadge(text = type, containerColor = if (isFocused) Color.Black.copy(alpha = 0.1f) else TvColors.Primary.copy(alpha = 0.2f), contentColor = if (isFocused) Color.Black else TvColors.Primary)
        }
    }
}
