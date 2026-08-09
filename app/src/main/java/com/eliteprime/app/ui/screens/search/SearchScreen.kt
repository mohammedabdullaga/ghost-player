package com.eliteprime.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors

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
    val hairline = Color.White.copy(alpha = 0.08f)
    val fieldFill = Color.White.copy(alpha = 0.06f)
    val fieldBorder = Color.White.copy(alpha = 0.1f)
    val chipFill = Color.White.copy(alpha = 0.05f)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)
    val textTertiary = Color.White.copy(alpha = 0.38f)
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchTopNavBar(onBack = onBack)

            Column(modifier = Modifier.padding(horizontal = SearchSpacing.lg, vertical = SearchSpacing.md)) {
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
                            label = "ALL",
                            icon = Icons.Default.Search,
                            isActive = selectedFilter == SearchResultType.ALL,
                            onClick = { selectedFilter = SearchResultType.ALL }
                        )
                        SubFilterChip(
                            label = "MOVIES",
                            icon = Icons.Default.Movie,
                            isActive = selectedFilter == SearchResultType.MOVIES,
                            onClick = { selectedFilter = SearchResultType.MOVIES }
                        )
                        SubFilterChip(
                            label = "SERIES",
                            icon = Icons.Default.VideoLibrary,
                            isActive = selectedFilter == SearchResultType.SERIES,
                            onClick = { selectedFilter = SearchResultType.SERIES }
                        )
                        SubFilterChip(
                            label = "LIVE TV",
                            icon = Icons.Default.Tv,
                            isActive = selectedFilter == SearchResultType.LIVE_TV,
                            onClick = { selectedFilter = SearchResultType.LIVE_TV }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = SearchSpacing.lg)) {
                when {
                    query.isBlank() -> IdleState()
                    uiState.isLoading -> LoadingState()
                    uiState.movies.isEmpty() && uiState.series.isEmpty() && uiState.channels.isEmpty() -> NoResultsState(query)
                    else -> ResultsList(
                        selectedFilter = selectedFilter,
                        movieResults = uiState.movies,
                        seriesResults = uiState.series,
                        channelResults = uiState.channels,
                        onMovieClick = onMovieClick,
                        onSeriesClick = onSeriesClick,
                        onChannelClick = onChannelClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTopNavBar(onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SearchSpacing.lg, vertical = SearchSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SearchSpacing.sm)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp).background(SearchPalette.chipFill, CircleShape)
            ) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = SearchPalette.textPrimary, modifier = Modifier.size(20.dp))
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
        placeholder = { Text("Search your library", color = SearchPalette.textTertiary) },
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
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Search, contentDescription = null, tint = SearchPalette.textTertiary, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(SearchSpacing.sm))
            Text("Search your library", color = SearchPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Find movies, series, and live channels by title",
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppColors.ArenaOrange, strokeWidth = 2.5.dp)
    }
}

@Composable
private fun NoResultsState(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SearchOff, contentDescription = null, tint = SearchPalette.textTertiary, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(SearchSpacing.sm))
            Text("No results found", color = SearchPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Nothing matched \u201c$query\u201d", color = SearchPalette.textTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ResultsList(
    selectedFilter: SearchResultType,
    movieResults: List<Movie>,
    seriesResults: List<Series>,
    channelResults: List<LiveStream>,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onChannelClick: (LiveStream) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = SearchSpacing.sm, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(SearchSpacing.lg)
    ) {
        if ((selectedFilter == SearchResultType.ALL || selectedFilter == SearchResultType.MOVIES) && movieResults.isNotEmpty()) {
            item { ResultsSectionLabel("Movies", movieResults.size) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SearchSpacing.sm)) {
                    items(movieResults) { movie ->
                        Box(modifier = Modifier.width(130.dp)) {
                            VodPosterCard(movie = movie, onClick = { onMovieClick(movie) })
                        }
                    }
                }
            }
        }

        if ((selectedFilter == SearchResultType.ALL || selectedFilter == SearchResultType.SERIES) && seriesResults.isNotEmpty()) {
            item { ResultsSectionLabel("Series", seriesResults.size) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SearchSpacing.sm)) {
                    items(seriesResults) { series ->
                        Box(modifier = Modifier.width(130.dp)) {
                            SeriesPosterCard(series = series, onClick = { onSeriesClick(series) })
                        }
                    }
                }
            }
        }

        if ((selectedFilter == SearchResultType.ALL || selectedFilter == SearchResultType.LIVE_TV) && channelResults.isNotEmpty()) {
            item { ResultsSectionLabel("Live TV", channelResults.size) }
            items(channelResults) { channel ->
                ChannelRow(
                    channel = channel,
                    isSelected = false,
                    onClick = { onChannelClick(channel) }
                )
            }
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
