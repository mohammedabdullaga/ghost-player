package com.eliteprime.app.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.MobileBackButton
import com.eliteprime.app.ui.theme.AppColors

@Composable
fun MovieDetailsScreen(
    movie: Movie,
    onBack: () -> Unit,
    onPlay: (Movie) -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cinematic Backdrop
            AsyncImage(
                model = movie.backdropUrl ?: movie.streamIcon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF070B14), Color(0xFF070B14).copy(alpha = 0.8f), Color.Transparent)
                    )
                )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Poster & Back
                Box(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(24.dp))
                ) {
                    AsyncImage(
                        model = movie.streamIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    MobileBackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
                }

                // Right Panel: Info
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(text = "MOVIE", color = AppColors.ArenaOrange, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text(text = movie.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (movie.rating > 0) {
                            Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("%.1f".format(movie.rating), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        movie.genre?.let {
                            Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }

                    if (!movie.plot.isNullOrBlank()) {
                        Text(
                            text = movie.plot!!,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }

                    Button(
                        onClick = { onPlay(movie) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.ArenaOrange),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("PLAY NOW", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    
                    if (!movie.cast.isNullOrBlank()) {
                        MetadataItem(label = "CAST", value = movie.cast!!)
                    }
                    if (!movie.director.isNullOrBlank()) {
                        MetadataItem(label = "DIRECTOR", value = movie.director!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataItem(label: String, value: String) {
    Column {
        Text(text = label, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp)
        Text(text = value, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
    }
}
