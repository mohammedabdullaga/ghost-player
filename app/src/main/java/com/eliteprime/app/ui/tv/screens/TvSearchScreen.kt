package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.ArenaLiveThumbnailPlaceholder
import com.eliteprime.app.ui.screens.search.SearchResultFilter
import com.eliteprime.app.ui.screens.search.SearchViewModel
import com.eliteprime.app.ui.tv.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


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
    val selectedFilter = uiState.activeFilter
    val searchFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Focus restoration states
    val itemFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    var lastFocusedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    // Initial focus on search bar, ONLY if we don't have a restoration target
    LaunchedEffect(Unit) {
        if (lastFocusedId == null) {
            delay(300)
            searchFocusRequester.requestFocus()
        }
    }

    // Restore focus when returning to the screen
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (lastFocusedId != null) {
            scope.launch {
                // More intense retry loop to catch the item when it's ready
                repeat(10) {
                    delay(100)
                    try {
                        itemFocusRequesters[lastFocusedId]?.requestFocus()
                        return@launch
                    } catch (_: Exception) {}
                }
                // Final fallback if item never appeared
                searchFocusRequester.requestFocus()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 8.dp) // MINIMAL TOP PADDING
        ) {
            // A. COMPACT SEARCH HEADER
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(55.dp) // Increased height by 15%
            ) {
                Text(
                    text = stringResource(R.string.nav_search).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.width(24.dp))
                
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester),
                    textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
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
                    keyboardOptions = KeyboardOptions(
                        hintLocales = LocaleList(Locale("ar"), Locale("en"))
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp)) // Reduced padding below searchbar

            // B. COMPACT FILTER CHIPS
            if (query.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TvSearchFilterChip(
                        label = stringResource(R.string.filter_all),
                        icon = Icons.Default.AllInclusive,
                        isSelected = selectedFilter == SearchResultFilter.ALL,
                        onClick = { viewModel.setFilter(SearchResultFilter.ALL) }
                    )
                    TvSearchFilterChip(
                        label = stringResource(R.string.filter_movies),
                        icon = Icons.Default.Movie,
                        isSelected = selectedFilter == SearchResultFilter.MOVIES,
                        onClick = { viewModel.setFilter(SearchResultFilter.MOVIES) }
                    )
                    TvSearchFilterChip(
                        label = stringResource(R.string.filter_series),
                        icon = Icons.Default.VideoLibrary,
                        isSelected = selectedFilter == SearchResultFilter.SERIES,
                        onClick = { viewModel.setFilter(SearchResultFilter.SERIES) }
                    )
                    TvSearchFilterChip(
                        label = stringResource(R.string.generic_live) + " " + stringResource(R.string.generic_channels),
                        icon = Icons.Default.LiveTv,
                        isSelected = selectedFilter == SearchResultFilter.LIVE_TV,
                        onClick = { viewModel.setFilter(SearchResultFilter.LIVE_TV) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp)) // Reduced padding below filters
            }

            // C. CENTERED RESULTS GRID
            TvGlassPane(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, tint = TvColors.TextTertiary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.search_idle_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(stringResource(R.string.search_idle_desc), color = TvColors.TextTertiary, fontSize = 13.sp)
                        }
                    }
                } else if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TvColors.Primary, modifier = Modifier.size(32.dp))
                    }
                } else if (uiState.movies.isEmpty() && uiState.series.isEmpty() && uiState.channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.search_no_results), color = TvColors.TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(40.dp), // Separate sections clearly
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. MOVIES SECTION
                        if ((selectedFilter == SearchResultFilter.ALL || selectedFilter == SearchResultFilter.MOVIES) && uiState.movies.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    SearchSectionLabelV3(stringResource(R.string.nav_movies), uiState.movies.size)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp)
                                    ) {
                                        items(uiState.movies) { movie ->
                                            val requester = itemFocusRequesters.getOrPut(movie.id) { FocusRequester() }
                                            TvSearchPosterCardV3(
                                                title = movie.name,
                                                posterUrl = movie.streamIcon ?: movie.backdropUrl,
                                                modifier = Modifier
                                                    .focusRequester(requester)
                                                    .onFocusChanged { if (it.isFocused) lastFocusedId = movie.id },
                                                onClick = { onMovieClick(movie) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. SERIES SECTION
                        if ((selectedFilter == SearchResultFilter.ALL || selectedFilter == SearchResultFilter.SERIES) && uiState.series.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    SearchSectionLabelV3(stringResource(R.string.nav_series), uiState.series.size)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp)
                                    ) {
                                        items(uiState.series) { series ->
                                            val requester = itemFocusRequesters.getOrPut(series.id) { FocusRequester() }
                                            TvSearchPosterCardV3(
                                                title = series.name,
                                                posterUrl = series.cover ?: series.backdropUrl,
                                                modifier = Modifier
                                                    .focusRequester(requester)
                                                    .onFocusChanged { if (it.isFocused) lastFocusedId = series.id },
                                                onClick = { onSeriesClick(series) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. LIVE TV SECTION (Now Card Grid just like Movies/Series)
                        if ((selectedFilter == SearchResultFilter.ALL || selectedFilter == SearchResultFilter.LIVE_TV) && uiState.channels.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    SearchSectionLabelV3(stringResource(R.string.generic_live) + " " + stringResource(R.string.generic_channels), uiState.channels.size)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp)
                                    ) {
                                        items(uiState.channels) { channel ->
                                            val requester = itemFocusRequesters.getOrPut(channel.id) { FocusRequester() }
                                            TvSearchChannelCardV3(
                                                channel = channel,
                                                modifier = Modifier
                                                    .focusRequester(requester)
                                                    .onFocusChanged { if (it.isFocused) lastFocusedId = channel.id },
                                                onClick = { onChannelClick(channel) }
                                            )
                                        }
                                    }
                                }
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = if (isFocused) Color.Black else if (isSelected) TvColors.Primary else Color.White, modifier = Modifier.size(14.dp))
            Text(
                text = label.uppercase(), 
                color = if (isFocused) Color.Black else if (isSelected) TvColors.Primary else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchSectionLabelV3(title: String, count: Int) {
    Row(
        modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title.uppercase(), color = TvColors.Primary, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.2.sp)
        Text(text = "$count " + stringResource(R.string.generic_results), color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

@Composable
private fun TvSearchPosterCardV3(
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.width(134.dp), 
        // INCREASED SPACING TO ACCOUNT FOR FOCUS SCALE
        verticalArrangement = Arrangement.spacedBy(if (isFocused) 14.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TvSurfaceCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f/3f).onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            SubcomposeAsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { ArenaLiveThumbnailPlaceholder(Modifier.fillMaxSize()) },
                error = { ArenaLiveThumbnailPlaceholder(Modifier.fillMaxSize()) }
            )
        }
        Text(
            text = title,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TvSearchChannelCardV3(
    channel: LiveStream,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.width(120.dp), 
        // INCREASED SPACING TO ACCOUNT FOR FOCUS SCALE
        verticalArrangement = Arrangement.spacedBy(if (isFocused) 14.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TvSurfaceCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f/3f).onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = channel.streamIcon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.fillMaxSize(0.6f), contentScale = ContentScale.Fit)
                        }
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.fillMaxSize(0.6f), contentScale = ContentScale.Fit)
                        }
                    }
                )
            }
        }
        Text(
            text = channel.name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
