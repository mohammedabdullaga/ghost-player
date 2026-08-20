package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.details.MovieDetailViewModel
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.util.formatPositionMs
import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.delay

@Composable
fun TvMovieDetailsScreen(
    onBack: () -> Unit,
    onPlay: (Movie) -> Unit,
    onMovieClick: (Movie) -> Unit = {},
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val movie by viewModel.movie.collectAsState()
    val relatedMovies by viewModel.relatedMovies.collectAsState()
    val playFocusRequester = remember { FocusRequester() }

    BackHandler { onBack() }

    LaunchedEffect(movie) {
        if (movie != null) {
            delay(300)
            try { playFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    AppBackground(isTransparent = true) {
        if (movie == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TvColors.Primary)
            }
        } else {
            val m = movie!!
            
            // Backdrop
            AsyncImage(
                model = m.backdropUrl ?: m.streamIcon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.45f
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        remember {
                            Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Black.copy(alpha = 0.8f), Color.Transparent),
                                endX = 1400f
                            )
                        }
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                // LEFT PANEL: POSTER + ACTIONS
                Column(
                    modifier = Modifier.weight(0.30f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TvGlassPane(
                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        AsyncImage(
                            model = m.streamIcon ?: m.backdropUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isPlayFocused by remember { mutableStateOf(false) }
                        TvSurfaceCard(
                            onClick = { onPlay(m) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .focusRequester(playFocusRequester)
                                .onFocusChanged { isPlayFocused = it.isFocused },
                            colors = ClickableSurfaceDefaults.colors(containerColor = AppColors.ArenaOrange.copy(alpha = 0.25f), focusedContainerColor = Color.White),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, Color.White))
                            )
                        ) {
                            // Explicit Centering
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically, 
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp), tint = if (isPlayFocused) Color.Black else Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (m.watchProgress > 10000) stringResource(R.string.generic_resume).uppercase() else stringResource(R.string.generic_watch_now).uppercase(),
                                    color = if (isPlayFocused) Color.Black else Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        var isFavFocused by remember { mutableStateOf(false) }
                        TvSurfaceCard(
                            onClick = { viewModel.toggleFavorite() },
                            modifier = Modifier.size(48.dp).onFocusChanged { isFavFocused = it.isFocused },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), focusedContainerColor = Color.White)
                        ) {
                            // Explicit Centering
                            Icon(
                                imageVector = if (m.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).align(Alignment.Center),
                                tint = if (isFavFocused) Color.Black else if (m.isFavorite) Color.Red else Color.White
                            )
                        }
                    }
                }

                // RIGHT PANEL: INFO + RELATED
                Column(
                    modifier = Modifier.weight(0.70f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    TvGlassPane(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        backgroundColor = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = m.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                lineHeight = 30.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                m.genre?.takeIf { it.isNotBlank() }?.let {
                                    Text(text = it.uppercase(), color = TvColors.Primary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                                }

                                if (m.rating > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Star, null, tint = TvColors.StarGold, modifier = Modifier.size(14.dp))
                                        Text(text = "%.1f".format(m.rating), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                Text(text = m.releaseDate?.take(4) ?: "N/A", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (!m.duration.isNullOrBlank()) {
                                    Text(text = m.duration!!, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                TvBadge(text = "4K HDR", containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)
                            }
                            
                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = m.plot ?: stringResource(R.string.detail_no_related_series),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = 20.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            Spacer(Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                TechnicalItemCompact(label = stringResource(R.string.metadata_director), value = m.director ?: stringResource(R.string.generic_unknown))
                                if (!m.cast.isNullOrBlank()) {
                                    TechnicalItemCompact(label = stringResource(R.string.metadata_cast), value = m.cast!!)
                                }
                            }
                        }
                    }

                    if (relatedMovies.isNotEmpty()) {
                        Column {
                            Text(
                                text = stringResource(R.string.detail_more_like_this).uppercase(),
                                color = Color.White.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                items(relatedMovies) { related ->
                                    TvRelatedMovieCardCompact(related) { onMovieClick(related) }
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
private fun TechnicalItemCompact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.width(80.dp), color = TvColors.TextTertiary, fontWeight = FontWeight.Black, fontSize = 8.sp)
        Text(text = value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvRelatedMovieCardCompact(movie: Movie, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(100.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TvSurfaceCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f/3f).onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = movie.streamIcon ?: movie.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().align(Alignment.Center)
            )
        }
        Text(
            text = movie.name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
