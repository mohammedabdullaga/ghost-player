package com.eliteprime.app.ui.screens.details

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.domain.model.DownloadItem
import com.eliteprime.app.domain.model.DownloadStatus
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.formatPositionMs
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay

@Composable
fun MovieDetailsScreen(
    onBack: () -> Unit,
    onPlay: (Movie) -> Unit,
    onPlayLocal: (Long, String, String) -> Unit = { _, _, _ -> },
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val movieState by viewModel.movie.collectAsState()
    val movie = movieState
    val relatedMovies by viewModel.relatedMovies.collectAsState()
    val downloadItem by viewModel.downloadItem.collectAsState()
    val isTv = LocalContext.current.isTelevisionDevice()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(movie) {
        if (isTv && movie != null) {
            delay(100)
            playButtonFocusRequester.requestFocus()
        }
    }

    if (movie == null) {
        Box(modifier = Modifier.fillMaxSize().background(DetailSurface.backdrop), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.ArenaOrange, strokeWidth = 2.5.dp)
        }
        return
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cinematic Backdrop
            AsyncImage(
                model = movie.backdropUrl ?: movie.streamIcon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.45f
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(DetailSurface.backdrop, DetailSurface.backdrop.copy(alpha = 0.75f), DetailSurface.backdrop.copy(alpha = 0.95f))
                    )
                )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Poster & Back
                PosterPanel(
                    posterUrl = movie.streamIcon ?: movie.backdropUrl, 
                    onBack = onBack, 
                    modifier = Modifier.weight(0.38f),
                    isTv = isTv
                )

                // Right Panel: Info
                LazyColumn(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .padding(start = DetailSpacing.md, end = DetailSpacing.xl, top = DetailSpacing.xl, bottom = DetailSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DetailSpacing.md)
                ) {
                    item {
                        InfoCard {
                            // 1. Genre (Eyebrow)
                            Text(
                                text = movie.genre?.uppercase() ?: stringResource(R.string.generic_movie), 
                                style = EyebrowStyle, 
                                color = AppColors.ArenaOrange
                            )
                            
                            // 2. Title + Favorite
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = movie.name,
                                    color = DetailSurface.textPrimary,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 30.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                DetailFavButton(
                                    isFavorite = movie.isFavorite,
                                    onClick = { viewModel.toggleFavorite() },
                                    isTv = isTv
                                )
                            }

                            // 3. Rating Row
                            TagRow(genre = movie.genre, rating = movie.rating)
                            
                            // 4. Description (Plot)
                            if (!movie.plot.isNullOrBlank()) {
                                Text(
                                    text = movie.plot!!,
                                    color = DetailSurface.textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 5. Action Row (Play + Download)
                            PrimaryActionRow(
                                onPlay = { onPlay(movie) },
                                onPlayLocal = onPlayLocal,
                                onDownload = { viewModel.downloadMovie() },
                                downloadItem = downloadItem,
                                movieId = movie.id,
                                movieName = movie.name,
                                watchProgress = movie.watchProgress,
                                isTv = isTv,
                                playFocusRequester = playButtonFocusRequester
                            )

                            // 6. Metadata Grid (Technical Details)
                            MetadataGrid(
                                listOfNotNull(
                                    movie.duration?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.metadata_duration) to it },
                                    movie.director?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.metadata_director) to it },
                                    movie.genre?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.metadata_genre) to it },
                                    movie.cast?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.metadata_cast) to it }
                                )
                            )
                        }
                    }

                    if (relatedMovies.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.detail_more_like_this),
                                color = DetailSurface.textPrimary,
                                style = EyebrowStyle
                            )
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(DetailSpacing.sm),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(relatedMovies) { related ->
                                    RelatedPosterCard(
                                        imageUrl = related.streamIcon ?: related.backdropUrl,
                                        title = related.name,
                                        onClick = { onPlay(related) }
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
private fun PosterPanel(posterUrl: String?, onBack: () -> Unit, modifier: Modifier = Modifier, isTv: Boolean) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(DetailSpacing.md)
            .clip(RoundedCornerShape(DetailRadius.xl))
            .border(BorderStroke(1.dp, DetailSurface.hairline), RoundedCornerShape(DetailRadius.xl))
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.85f)
                )
            )
        )
        
        if (isTv) {
            TvSurface(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(DetailSpacing.md).size(40.dp),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
                ),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            MobileBackButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(DetailSpacing.md))
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = DetailSurface.cardFill,
        shape = RoundedCornerShape(DetailRadius.lg),
        border = BorderStroke(1.dp, DetailSurface.hairline)
    ) {
        Column(
            modifier = Modifier.padding(DetailSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DetailSpacing.md),
            content = content
        )
    }
}

@Composable
private fun DetailFavButton(isFavorite: Boolean, onClick: () -> Unit, isTv: Boolean) {
    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) AppColors.ArenaOrange else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .background(DetailSurface.cardFillElevated, CircleShape)
                .border(BorderStroke(1.dp, DetailSurface.hairline), CircleShape)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) AppColors.ArenaOrange else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TagRow(genre: String?, rating: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DetailSpacing.xs)) {
        genre?.takeIf { it.isNotBlank() }?.let {
            Surface(
                color = DetailSurface.cardFillElevated,
                shape = RoundedCornerShape(DetailRadius.sm),
                border = BorderStroke(1.dp, DetailSurface.hairline)
            ) {
                Text(
                    text = it,
                    color = DetailSurface.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = DetailSpacing.sm, vertical = DetailSpacing.xxs)
                )
            }
        }
        if (rating > 0) {
            Surface(
                color = DetailSurface.cardFillElevated,
                shape = RoundedCornerShape(DetailRadius.sm),
                border = BorderStroke(1.dp, DetailSurface.hairline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = DetailSpacing.sm, vertical = DetailSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "%.1f".format(rating), color = DetailSurface.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionRow(
    onPlay: () -> Unit,
    onPlayLocal: (Long, String, String) -> Unit,
    onDownload: () -> Unit,
    downloadItem: DownloadItem?,
    movieId: Long,
    movieName: String,
    watchProgress: Long,
    isTv: Boolean,
    playFocusRequester: FocusRequester
) {
    val isDownloaded = downloadItem?.status == DownloadStatus.COMPLETED
    val localPath = downloadItem?.outputUri ?: downloadItem?.outputDisplayPath

    Row(horizontalArrangement = Arrangement.spacedBy(DetailSpacing.md), modifier = Modifier.fillMaxWidth()) {
        if (isTv) {
            TvSurface(
                onClick = { if (isDownloaded && localPath != null) onPlayLocal(movieId, localPath, movieName) else onPlay() },
                modifier = Modifier.weight(1f).height(52.dp).focusRequester(playFocusRequester),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isDownloaded) Color(0xFF4CAF50) else AppColors.ArenaOrange,
                    focusedContainerColor = Color.White
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(DetailRadius.md))
            ) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(24.dp), tint = Color.Unspecified)
                    Spacer(Modifier.width(DetailSpacing.xs))
                    Text(
                        text = when {
                            isDownloaded -> stringResource(R.string.player_play_downloaded)
                            watchProgress > 10000 -> stringResource(R.string.player_resume_from, formatPositionMs(watchProgress))
                            else -> stringResource(R.string.player_play_now)
                        },
                        fontWeight = FontWeight.Black, 
                        fontSize = 14.sp, 
                        color = Color.Unspecified
                    )
                }
            }

            if (!isDownloaded) {
                TvSurface(
                    onClick = onDownload,
                    modifier = Modifier.size(52.dp),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(DetailRadius.md)),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when(downloadItem?.status) {
                                DownloadStatus.DOWNLOADING -> Icons.Default.HourglassEmpty
                                DownloadStatus.PENDING -> Icons.Default.Schedule
                                else -> Icons.Default.Download
                            }, 
                            null, tint = Color.Unspecified, modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                // Show green tick
                Surface(
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(DetailRadius.md),
                    modifier = Modifier.size(52.dp),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                    }
                }
            }
        } else {
            Button(
                onClick = { if (isDownloaded && localPath != null) onPlayLocal(movieId, localPath, movieName) else onPlay() },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isDownloaded) Color(0xFF4CAF50) else AppColors.ArenaOrange),
                shape = RoundedCornerShape(DetailRadius.md)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DetailSpacing.xs))
                Text(
                    text = when {
                        isDownloaded -> "PLAY DOWNLOADED"
                        watchProgress > 10000 -> "RESUME FROM ${formatPositionMs(watchProgress)}"
                        else -> "PLAY NOW"
                    },
                    fontWeight = FontWeight.Black, 
                    fontSize = 13.sp
                )
            }
            
            if (!isDownloaded) {
                OutlinedIconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(48.dp),
                    border = BorderStroke(1.dp, DetailSurface.hairline),
                    colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = DetailSurface.cardFillElevated)
                ) {
                    Icon(
                        imageVector = when(downloadItem?.status) {
                            DownloadStatus.DOWNLOADING -> Icons.Default.HourglassEmpty
                            DownloadStatus.PENDING -> Icons.Default.Schedule
                            else -> Icons.Default.Download
                        }, 
                        null, tint = DetailSurface.textPrimary, modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataGrid(items: List<Pair<String, String>>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(DetailSpacing.xs)) {
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DetailSurface.hairline))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                rowItems.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(text = label, color = DetailSurface.textTertiary, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.6.sp)
                        Text(text = value, color = DetailSurface.textPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RelatedPosterCard(imageUrl: String?, title: String, onClick: () -> Unit) {
    val isTv = LocalContext.current.isTelevisionDevice()
    
    Column(
        modifier = Modifier.width(110.dp),
        verticalArrangement = Arrangement.spacedBy(DetailSpacing.xxs)
    ) {
        if (isTv) {
            TvSurface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(DetailRadius.md)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.2f),
                    focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                )
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                shape = RoundedCornerShape(DetailRadius.md),
                color = DetailSurface.cardFill,
                border = BorderStroke(1.dp, DetailSurface.hairline)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = title,
            color = DetailSurface.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
