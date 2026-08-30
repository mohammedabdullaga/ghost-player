package com.eliteprime.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.res.painterResource
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.DeviceRenderProfile
import com.eliteprime.app.util.LocalRenderTier
import com.eliteprime.app.util.RenderTier
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Detail UI Tokens ---
object DetailSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
}

object DetailRadius {
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 28.dp
}

object DetailSurface {
    val backdrop = Color(0xFF070B14)
    val hairline = Color.White.copy(alpha = 0.12f)
    val cardFill = Color.Black.copy(alpha = 0.65f)
    val cardFillElevated = Color.White.copy(alpha = 0.1f)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.7f)
    val textTertiary = Color.White.copy(alpha = 0.45f)
}

val EyebrowStyle = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Black,
    letterSpacing = 1.4.sp
)

@Composable
fun SubFilterChip(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.85f),
                focusedContainerColor = Color.White,
                pressedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                border = Border(BorderStroke(2.dp, if (isActive) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
            )
        ) {
            SubFilterChipContent(label, icon, isActive, isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            color = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (isActive) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.15f))
        ) {
            SubFilterChipContent(label, icon, isActive, false)
        }
    }
}

@Composable
private fun SubFilterChipContent(label: String, icon: ImageVector, active: Boolean, isFocused: Boolean) {
    val contentColor = when {
        isFocused -> Color.Black
        active -> Color.White
        else -> Color.White.copy(alpha = 0.6f)
    }
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            modifier = Modifier.size(14.dp), 
            tint = contentColor
        )
        Text(
            text = label,
            color = contentColor,
            fontWeight = if (active || isFocused) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}

@Composable
fun CategoryTab(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .focusRequester(focusRequester),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.045f),
                focusedContainerColor = Color.White,
                pressedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
            )
        ) {
            CategoryTabContent(category, isSelected, isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.045f),
            shape = RoundedCornerShape(16.dp),
            border = if (isSelected) BorderStroke(2.dp, AppColors.ArenaOrange.copy(alpha = 0.85f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            CategoryTabContent(category, isSelected, false)
        }
    }
}

@Composable
private fun CategoryTabContent(category: Category, isActive: Boolean, isFocused: Boolean) {
    val contentColor = when {
        isFocused -> Color.Black
        isActive -> Color.White
        else -> Color.White.copy(alpha = 0.6f)
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(if (isActive || isFocused) AppColors.ArenaOrange else Color.Transparent)
        )
        Text(
            text = category.categoryName,
            color = contentColor,
            fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun ChannelRow(
    channel: LiveStream,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .focusRequester(focusRequester),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = Color.White,
                pressedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
            )
        ) {
            ChannelRowContent(channel, isSelected, isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) BorderStroke(1.dp, AppColors.ArenaOrange.copy(alpha = 0.85f)) else null
        ) {
            ChannelRowContent(channel, isSelected, false)
        }
    }
}

@Composable
private fun ChannelRowContent(channel: LiveStream, isActive: Boolean, isFocused: Boolean) {
    val contentColor = when {
        isFocused -> Color.Black
        isActive -> Color.White
        else -> Color.White
    }
    val secondaryContentColor = when {
        isFocused -> Color.Black.copy(alpha = 0.7f)
        isActive -> Color.White.copy(alpha = 0.6f)
        else -> Color.White.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.streamIcon != null) {
                SubcomposeAsyncImage(
                    model = channel.streamIcon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
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
            } else {
                Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp), tint = secondaryContentColor)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.live_no_epg_data), color = secondaryContentColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun VodPosterCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val isLite = DeviceRenderProfile.isLiteMode()
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (isLite) 1.0f else 1.04f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border.None
            )
        ) {
            VodPosterCardContent(movie.name, movie.streamIcon, movie.rating.toFloat(), movie.isFavorite, movie.watchProgress, movie.totalDuration, isFocused)
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
                }
        ) {
            VodPosterCardContent(movie.name, movie.streamIcon, movie.rating.toFloat(), movie.isFavorite, movie.watchProgress, movie.totalDuration, false)
        }
    }
}

@Composable
fun SeriesPosterCard(
    series: Series,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val isLite = DeviceRenderProfile.isLiteMode()
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (isLite) 1.0f else 1.04f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border.None
            )
        ) {
            VodPosterCardContent(series.name, series.cover, series.rating.toFloat(), series.isFavorite, 0, 0, isFocused)
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
                }
        ) {
            VodPosterCardContent(series.name, series.cover, series.rating.toFloat(), series.isFavorite, 0, 0, false)
        }
    }
}

@Composable
private fun VodPosterCardContent(
    name: String, 
    posterUrl: String?, 
    rating: Float, 
    isFavorite: Boolean, 
    watchProgress: Long = 0,
    totalDuration: Long = 0,
    isFocused: Boolean
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val isLite = DeviceRenderProfile.isLiteMode()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Poster Image Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(posterUrl)
                    .crossfade(!isLite)
                    .size(if (isTv) Size(300, 450) else Size(200, 300))
                    .precision(Precision.INEXACT)
                    .build(),
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { ArenaLiveThumbnailPlaceholder(Modifier.fillMaxSize()) },
                error = { ArenaLiveThumbnailPlaceholder(Modifier.fillMaxSize()) }
            )

            if (rating > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = AppColors.ArenaOrange, modifier = Modifier.size(10.dp))
                        Text("%.1f".format(rating), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (watchProgress > 0 && totalDuration > 0) {
                val progress = (watchProgress.toFloat() / totalDuration).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = AppColors.ArenaOrange,
                    trackColor = Color.Transparent
                )
            }
        }
        
        // 2. Title Card Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .padding(vertical = 6.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MobileBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.generic_back), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun PaneHeader(title: String) {
    Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        Text(text = title.uppercase(), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
fun PaneSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, AppColors.ArenaOrange.copy(alpha = 0.18f))
    ) {
        content()
    }
}

@Composable
fun StatusPill(
    label: String,
    containerColor: Color,
    contentColor: Color = Color.White,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 4.dp
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            color = contentColor,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun ContinueWatchingRow(
    items: List<PlaybackHistory>,
    onItemClick: (PlaybackHistory) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.history_title),
            style = EyebrowStyle,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { history ->
                ContinueWatchingCard(
                    history = history,
                    onClick = { onItemClick(history) }
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    history: PlaybackHistory,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }
    
    val progress = if (history.durationMs > 0) {
        (history.lastPositionMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
    } else 0f

    val content = @Composable {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = history.backdropUrl ?: history.posterUrl,
                contentDescription = history.name,
                modifier = Modifier.fillMaxSize().alpha(0.8f),
                contentScale = ContentScale.Crop,
                loading = { ArenaLiveThumbnailPlaceholder(Modifier.fillMaxSize()) },
                error = { ArenaLiveThumbnailPlaceholder(Modifier.fillMaxSize()) }
            )
            
            // Bottom Scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = history.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if ((history.type == "series" || history.type == "series_episode") && history.seasonNumber != null) {
                    Text(
                        text = "S${history.seasonNumber} E${history.episodeNumber}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
            
            // Progress Bar at bottom
            if (progress > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = AppColors.ArenaOrange,
                    trackColor = Color.Transparent
                )
            }
        }
    }

    if (isTv) {
        var suppressClick by remember { mutableStateOf(false) }
        TvSurface(
            onClick = {
                if (!suppressClick) {
                    onClick()
                }
                suppressClick = false
            },
            onLongClick = {
                suppressClick = true
                onLongClick()
            },
            modifier = modifier
                .width(220.dp)
                .aspectRatio(16f / 9f)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier
                .width(200.dp)
                .aspectRatio(16f / 9f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                },
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            content()
        }
    }
}

@Composable
fun ArenaLiveThumbnailPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "ArenaLive",
            modifier = Modifier.fillMaxSize(0.45f),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun TvExitDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    TvConfirmDialog(
        title = stringResource(R.string.exit_confirm_title),
        message = stringResource(R.string.exit_confirm_msg),
        confirmText = stringResource(R.string.generic_yes),
        confirmColor = Color(0xFFEF5350),
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun TvConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color = AppColors.ArenaOrange,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // Prevent accidental instant-confirm from the key-up of the long-press that opened this dialog
    var canInteract by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        try { focusRequester.requestFocus() } catch (e: Exception) {}
        delay(300) // Extra grace period so the key-up from the triggering long-press doesn't land on the button
        canInteract = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF161A22),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                tonalElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Header with Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(confirmColor.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = confirmColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = message,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isTv = LocalContext.current.isTelevisionDevice()
                        
                        if (isTv) {
                            var isYesFocused by remember { mutableStateOf(false) }
                            TvSurface(
                                onClick = { if (canInteract) onConfirm() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isYesFocused = it.isFocused },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = confirmColor.copy(alpha = 0.2f),
                                    focusedContainerColor = Color.White
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                                    border = Border(BorderStroke(1.dp, confirmColor.copy(alpha = 0.5f)))
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = confirmText.uppercase(),
                                        color = if (isYesFocused) Color.Black else Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            var isNoFocused by remember { mutableStateOf(false) }
                            TvSurface(
                                onClick = { if (canInteract) onDismiss() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .onFocusChanged { isNoFocused = it.isFocused },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = Color.White
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(android.R.string.cancel).uppercase(),
                                        color = if (isNoFocused) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            // Mobile/Touch Buttons
                            Button(
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = confirmColor
                                )
                            ) {
                                Text(confirmText.uppercase(), fontWeight = FontWeight.Black, color = Color.White)
                            }

                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(stringResource(android.R.string.cancel).uppercase(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
