package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.search.SearchViewModel
import kotlinx.coroutines.delay

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
    
    val searchFocusRequester = remember { FocusRequester() }

    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        delay(300)
        searchFocusRequester.requestFocus()
    }

    AppBackground(isTransparent = true) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            // Search Input
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester),
                placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF8C00),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Results
            if (query.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_idle_desc), color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Channels Section
                    if (uiState.channels.isNotEmpty()) {
                        item { SearchSectionHeader(stringResource(R.string.generic_channels)) }
                        items(uiState.channels) { channel ->
                            TvSearchResultRow(channel.name, "Live TV") { onChannelClick(channel) }
                        }
                    }

                    // Movies Section
                    if (uiState.movies.isNotEmpty()) {
                        item { SearchSectionHeader(stringResource(R.string.nav_movies)) }
                        items(uiState.movies) { movie ->
                            TvSearchResultRow(movie.name, "Movie") { onMovieClick(movie) }
                        }
                    }

                    // Series Section
                    if (uiState.series.isNotEmpty()) {
                        item { SearchSectionHeader(stringResource(R.string.nav_series)) }
                        items(uiState.series) { series ->
                            TvSearchResultRow(series.name, "Series") { onSeriesClick(series) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFFFF8C00),
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun TvSearchResultRow(
    title: String,
    type: String,
    onClick: () -> Unit
) {
    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(text = type, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
