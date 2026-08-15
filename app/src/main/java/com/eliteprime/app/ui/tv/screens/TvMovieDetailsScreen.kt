package com.eliteprime.app.ui.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.Surface as TvSurface
import androidx.activity.compose.BackHandler
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.details.MovieDetailViewModel
import com.eliteprime.app.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun TvMovieDetailsScreen(
    onBack: () -> Unit,
    onPlay: (Movie) -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val movie by viewModel.movie.collectAsState()
    val playFocusRequester = remember { FocusRequester() }

    BackHandler {
        onBack()
    }

    LaunchedEffect(movie) {
        if (movie != null) {
            delay(300)
            playFocusRequester.requestFocus()
        }
    }

    AppBackground(isTransparent = true) {
        if (movie != null) {
            val m = movie!!
            
            // Backdrop
            AsyncImage(
                model = m.backdropUrl ?: m.streamIcon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )
            
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = 0f,
                            endX = 1200f
                        )
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.6f)
                    .padding(48.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = m.name,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "%.1f".format(m.rating), color = Color.White, fontWeight = FontWeight.Bold)
                    
                    if (!m.releaseDate.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = m.releaseDate!!.take(4), color = Color.White.copy(alpha = 0.6f))
                    }
                    
                    if (!m.genre.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = m.genre!!, color = Color.White.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = m.plot ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(48.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Play Button
                    TvSurface(
                        onClick = { onPlay(m) },
                        modifier = Modifier
                            .focusRequester(playFocusRequester)
                            .width(200.dp)
                            .height(56.dp),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AppColors.ArenaOrange,
                            focusedContainerColor = Color.White
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PlayArrow, 
                                    null, 
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.generic_watch_now),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Favorite Button
                    var isFav by remember { mutableStateOf(m.isFavorite) }
                    TvSurface(
                        onClick = { 
                            viewModel.toggleFavorite() 
                            isFav = !isFav
                        },
                        modifier = Modifier
                            .size(56.dp),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                null,
                                tint = if (isFav) Color.Red else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
