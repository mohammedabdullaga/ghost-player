package com.eliteprime.app.ui.screens.trending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.EyebrowStyle
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingScreen(
    onBack: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTv = LocalContext.current.isTelevisionDevice()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("TRENDING THIS WEEK", fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.2f),
                        titleContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                contentPadding = PaddingValues(vertical = 24.dp)
            ) {
                // 1. Trending Movies Section
                if (uiState.trendingMovies.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "TRENDING MOVIES",
                                style = EyebrowStyle,
                                color = AppColors.ArenaOrange,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.trendingMovies) { movie ->
                                    TrendingBackdropCard(
                                        title = movie.name,
                                        imageUrl = movie.backdropUrl ?: movie.streamIcon,
                                        rating = movie.rating,
                                        onClick = { onMovieClick(movie) },
                                        isTv = isTv
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Trending Series Section
                if (uiState.trendingSeries.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "TRENDING SERIES",
                                style = EyebrowStyle,
                                color = AppColors.ArenaOrange,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.trendingSeries) { series ->
                                    TrendingBackdropCard(
                                        title = series.name,
                                        imageUrl = series.backdropUrl ?: series.cover,
                                        rating = series.rating,
                                        onClick = { onSeriesClick(series) },
                                        isTv = isTv
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.trendingMovies.isEmpty() && uiState.trendingSeries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(color = AppColors.ArenaOrange)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "No trending content matched your library",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Matches are based on your synchronized content",
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 13.sp
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

@Composable
private fun TrendingBackdropCard(
    title: String,
    imageUrl: String?,
    rating: Double,
    onClick: () -> Unit,
    isTv: Boolean
) {
    val content = @Composable {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Bottom Scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(10.dp))
                        Text("%.1f".format(rating), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier
                .width(280.dp)
                .aspectRatio(16f / 9f),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
        ) {
            content()
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .width(240.dp)
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            content()
        }
    }
}
