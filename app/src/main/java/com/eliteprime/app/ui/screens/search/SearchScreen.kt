package com.eliteprime.app.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.intl.Locale as ComposeLocale
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
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.launch

private object SearchSpacing {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
}

private object SearchRadius {
    val sm = 10.dp
    val pill = 28.dp
}

private object SearchPalette {
    val hairline = Color.White.copy(alpha = 0.12f)
    val fieldFill = Color.Black.copy(alpha = 0.65f) // Darker, solid field
    val fieldBorder = Color.White.copy(alpha = 0.15f)
    val chipFill = Color.Black.copy(alpha = 0.7f)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)
    val textTertiary = Color.White.copy(alpha = 0.45f)
}

private enum class SearchResultType { ALL, MOVIES, SERIES, LIVE_TV }

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onMovieClick: (Movie) -> Unit = {},
    onSeriesClick: (Series) -> Unit = {},
    onChannelClick: (LiveStream) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SearchResultType.ALL) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isTv = LocalContext.current.isTelevisionDevice()

    LaunchedEffect(Unit) {
        if (isTv) {
            focusRequester.requestFocus()
        }
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchTopNavBar(onBack = onBack)

            // 1. Search Controls (Fixed at top, but can be hidden/revealed)
            // Hiding logic: We show this based on the scroll position of the list
            val showControls by remember {
                derivedStateOf { listState.firstVisibleItemIndex == 0 }
            }

            AnimatedVisibility(
                visible = showControls,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    SearchInputField(
                        query = query,
                        onQueryChange = {
                            query = it
                            viewModel.onQueryChange(it)
                        },
                        focusRequester = focusRequester,
                        onSearchAction = {
                            keyboardController?.hide()
                            viewModel.onSearchSubmitted()
                        }
                    )

                    if (query.isNotBlank()) {
                        Spacer(modifier = Modifier.height(SearchSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(SearchSpacing.xs)) {
                            SubFilterChip(
                                label = stringResource(R.string.filter_all),
                                icon = Icons.Default.Search,
                                isActive = selectedFilter == SearchResultType.ALL,
                                onClick = { selectedFilter = SearchResultType.ALL }
                            )
                            SubFilterChip(
                                label = stringResource(R.string.filter_movies),
                                icon = Icons.Default.Movie,
                                isActive = selectedFilter == SearchResultType.MOVIES,
                                onClick = { selectedFilter = SearchResultType.MOVIES }
                            )
                            SubFilterChip(
                                label = stringResource(R.string.filter_series),
                                icon = Icons.Default.VideoLibrary,
                                isActive = selectedFilter == SearchResultType.SERIES,
                                onClick = { selectedFilter = SearchResultType.SERIES }
                            )
                            SubFilterChip(
                                label = stringResource(R.string.generic_live),
                                icon = Icons.Default.Tv,
                                isActive = selectedFilter == SearchResultType.LIVE_TV,
                                onClick = { selectedFilter = SearchResultType.LIVE_TV }
                            )
                        }
                    }
                }
            }

            // 2. Results (Scrollable)
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                    verticalArrangement = if (query.isBlank() || uiState.isLoading) Arrangement.Center else Arrangement.Top
                ) {
                    when {
                        query.isBlank() -> item { Box(modifier = Modifier.fillParentMaxHeight(), contentAlignment = Alignment.Center) { IdleState() } }
                        uiState.isLoading -> item { Box(modifier = Modifier.fillParentMaxHeight(), contentAlignment = Alignment.Center) { LoadingState() } }
                        uiState.movies.isEmpty() && uiState.series.isEmpty() && uiState.channels.isEmpty() -> item { Box(modifier = Modifier.fillParentMaxHeight(), contentAlignment = Alignment.Center) { NoResultsState(query) } }
                        else -> {
                            if ((selectedFilter == SearchResultType.ALL || selectedFilter == SearchResultType.MOVIES) && uiState.movies.isNotEmpty()) {
                                item { Box(modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) { ResultsSectionLabel(stringResource(R.string.nav_movies), uiState.movies.size) } }
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(SearchSpacing.md)
                                    ) {
                                        items(uiState.movies) { movie ->
                                            Box(modifier = Modifier.width(136.dp)) {
                                                VodPosterCard(movie = movie, onClick = { onMovieClick(movie) })
                                            }
                                        }
                                    }
                                }
                            }

                            if ((selectedFilter == SearchResultType.ALL || selectedFilter == SearchResultType.SERIES) && uiState.series.isNotEmpty()) {
                                item { Box(modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) { ResultsSectionLabel(stringResource(R.string.nav_series), uiState.series.size) } }
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(SearchSpacing.md)
                                    ) {
                                        items(uiState.series) { series ->
                                            Box(modifier = Modifier.width(136.dp)) {
                                                SeriesPosterCard(series = series, onClick = { onSeriesClick(series) })
                                            }
                                        }
                                    }
                                }
                            }

                            if ((selectedFilter == SearchResultType.ALL || selectedFilter == SearchResultType.LIVE_TV) && uiState.channels.isNotEmpty()) {
                                item { Box(modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) { ResultsSectionLabel(stringResource(R.string.nav_live_tv), uiState.channels.size) } }
                                items(uiState.channels) { channel ->
                                    Box(modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)) {
                                        ChannelRow(channel = channel, isSelected = false, onClick = { onChannelClick(channel) })
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
private fun SearchTopNavBar(onBack: () -> Unit) {
    val isTv = LocalContext.current.isTelevisionDevice()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SearchSpacing.lg, vertical = SearchSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SearchSpacing.sm)
        ) {
            if (isTv) {
                TvSurface(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        focusedContainerColor = Color.White
                    ),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    )
                ) {
                    var isFocused by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxSize().onFocusChanged { isFocused = it.isFocused }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            ArenaLiveLogo(textSize = 20.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SearchPalette.hairline))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSearchAction: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_placeholder), color = SearchPalette.textTertiary) },
        singleLine = true,
        shape = RoundedCornerShape(SearchRadius.pill),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SearchPalette.textTertiary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = null, tint = SearchPalette.textTertiary)
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
            hintLocales = LocaleList(ComposeLocale("ar"), ComposeLocale("en"))
        ),
        keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = SearchPalette.textPrimary,
            unfocusedTextColor = SearchPalette.textPrimary,
            focusedBorderColor = AppColors.ArenaOrange,
            unfocusedBorderColor = SearchPalette.fieldBorder,
            focusedContainerColor = SearchPalette.fieldFill,
            unfocusedContainerColor = SearchPalette.fieldFill,
            cursorColor = AppColors.ArenaOrange
        )
    )
}

@Composable
private fun IdleState() {
    Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Search, contentDescription = null, tint = SearchPalette.textTertiary, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(SearchSpacing.sm))
            Text(stringResource(R.string.search_idle_title), color = SearchPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.search_idle_desc),
                color = SearchPalette.textTertiary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppColors.ArenaOrange, strokeWidth = 2.5.dp)
    }
}

@Composable
private fun NoResultsState(query: String) {
    Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SearchOff, contentDescription = null, tint = SearchPalette.textTertiary, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(SearchSpacing.sm))
            Text(stringResource(R.string.search_no_results), color = SearchPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.search_no_results_query, query), color = SearchPalette.textTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ResultsSectionLabel(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = SearchPalette.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
        Text(text = count.toString(), color = AppColors.ArenaOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

