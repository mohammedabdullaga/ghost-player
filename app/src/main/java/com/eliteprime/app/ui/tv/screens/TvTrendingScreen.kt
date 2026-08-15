package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.VodPosterCard
import com.eliteprime.app.ui.components.SeriesPosterCard
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.dashboard.TrendingItem
import kotlinx.coroutines.delay

@Composable
fun TvTrendingScreen(
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var focusedItem by remember { mutableStateOf<TrendingItem<*>?>(null) }
    val firstItemRequester = remember { FocusRequester() }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        delay(500)
        try { firstItemRequester.requestFocus() } catch (e: Exception) {}
    }

    AppBackground(isTransparent = true) {
        // Immersive Backdrop
        Crossfade(targetState = focusedItem, animationSpec = tween(1000), label = "trendingBackdrop") { item ->
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item?.backdropUrl ?: item?.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 400f)))
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
            Text(
                text = stringResource(R.string.home_trending_title),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val allTrending = uiState.trendingMovies + uiState.trendingSeries
                
                items(allTrending.distinctBy { it.tmdbId }) { item ->
                    val isFirst = allTrending.firstOrNull()?.tmdbId == item.tmdbId
                    val modifier = if (isFirst) Modifier.focusRequester(firstItemRequester) else Modifier
                    
                    val cardModifier = modifier.onFocusChanged { if (it.isFocused) focusedItem = item }

                    if (item.localMatch is Movie) {
                        VodPosterCard(
                            movie = item.localMatch,
                            modifier = cardModifier,
                            onClick = { onMovieClick(item.localMatch) }
                        )
                    } else if (item.localMatch is Series) {
                        SeriesPosterCard(
                            series = item.localMatch,
                            modifier = cardModifier,
                            onClick = { onSeriesClick(item.localMatch) }
                        )
                    }
                }
            }
        }
    }
}
