package com.eliteprime.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice

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
                containerColor = if (isActive) AppColors.FocusBrown else Color.Black.copy(alpha = 0.85f),
                focusedContainerColor = AppColors.FocusBrown,
                pressedContainerColor = AppColors.FocusBrown
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            SubFilterChipContent(label, icon, isActive || isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            color = if (isActive) AppColors.ArenaOrange else Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (isActive) AppColors.ArenaOrange else Color.White.copy(alpha = 0.15f))
        ) {
            SubFilterChipContent(label, icon, isActive)
        }
    }
}

@Composable
private fun SubFilterChipContent(label: String, icon: ImageVector, active: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            modifier = Modifier.size(14.dp), 
            tint = if (active) Color.White else Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.FocusBrown.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f),
                pressedContainerColor = AppColors.FocusBrown
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            CategoryTabContent(category, isSelected || isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            color = if (isSelected) AppColors.FocusBrown.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp)
        ) {
            CategoryTabContent(category, isSelected)
        }
    }
}

@Composable
private fun CategoryTabContent(category: Category, isActive: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(if (isActive) AppColors.ArenaOrange else Color.Transparent))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = category.categoryName,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                containerColor = if (isSelected) AppColors.FocusBrown.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            ChannelRowContent(channel, isSelected || isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = if (isSelected) AppColors.FocusBrown.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp)
        ) {
            ChannelRowContent(channel, isSelected)
        }
    }
}

@Composable
private fun ChannelRowContent(channel: LiveStream, isActive: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.streamIcon != null) {
                AsyncImage(model = channel.streamIcon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.4f))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("No EPG Data", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.7f)
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White))
            )
        ) {
            VodPosterCardContent(movie.name, movie.streamIcon, movie.rating.toFloat(), movie.isFavorite, isFocused)
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
                }
        ) {
            VodPosterCardContent(movie.name, movie.streamIcon, movie.rating.toFloat(), movie.isFavorite, false)
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
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.7f)
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White))
            )
        ) {
            VodPosterCardContent(series.name, series.cover, series.rating.toFloat(), series.isFavorite, isFocused)
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
                }
        ) {
            VodPosterCardContent(series.name, series.cover, series.rating.toFloat(), series.isFavorite, false)
        }
    }
}

@Composable
private fun VodPosterCardContent(name: String, posterUrl: String?, rating: Float, isFavorite: Boolean, isFocused: Boolean) {
    Column(
        modifier = Modifier
            .background(
                if (isFocused) AppColors.FocusBrown.copy(alpha = 0.7f) 
                else Color.White.copy(alpha = 0.05f), 
                RoundedCornerShape(18.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (rating > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = AppColors.ArenaOrange, modifier = Modifier.size(10.dp))
                        Text("%.1f".format(rating), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isFavorite) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart),
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(topEnd = 8.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = AppColors.ArenaOrange, modifier = Modifier.padding(6.dp).size(12.dp))
                }
            }
        }
        
        // Caption strip - matching old app look
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(if (isFocused) AppColors.FocusBrown.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.cleanVodName(),
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun String.cleanVodName(): String {
    val endYearRegex = Regex("\\s*[(\\[\\-]?\\d{4}[)\\]]?\\s*$")
    val startYearRegex = Regex("^\\s*[(\\[]?\\d{4}[)\\]]?\\s*[-]?\\s*")
    return this.replace(endYearRegex, "").replace(startYearRegex, "").trim()
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
            Text("Back", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
