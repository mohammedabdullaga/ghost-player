package com.eliteprime.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.theme.AppColors

@Composable
fun SubFilterChip(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isActive) AppColors.ArenaOrange.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (isActive) AppColors.ArenaOrange else Color.White.copy(alpha = 0.6f))
            Text(
                text = label,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun CategoryTab(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusRequester(focusRequester),
        color = if (isSelected || isFocused) AppColors.ArenaOrange.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(if (isSelected) AppColors.ArenaOrange else Color.Transparent))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = category.categoryName,
                color = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChannelRow(
    channel: LiveStream,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusRequester(focusRequester),
        color = if (isSelected || isFocused) AppColors.ArenaOrange.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
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
}

@Composable
fun VodPosterCard(
    movie: Movie,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusRequester(focusRequester)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .graphicsLayer {
                val scale = if (isFocused) 1.05f else 1f
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused || isPressed) AppColors.ArenaOrange else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            AsyncImage(
                model = movie.streamIcon,
                contentDescription = movie.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (movie.rating > 0) {
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
                        Text("%.1f".format(movie.rating), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = movie.name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun SeriesPosterCard(
    series: Series,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusRequester(focusRequester)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .graphicsLayer {
                val scale = if (isFocused) 1.05f else 1f
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused || isPressed) AppColors.ArenaOrange else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            AsyncImage(
                model = series.cover,
                contentDescription = series.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (series.rating > 0) {
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
                        Text("%.1f".format(series.rating), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = series.name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
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
        color = Color.Black.copy(alpha = 0.35f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, AppColors.ArenaOrange.copy(alpha = 0.14f))
    ) {
        content()
    }
}
