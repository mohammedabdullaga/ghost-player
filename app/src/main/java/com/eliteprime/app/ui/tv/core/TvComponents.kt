package com.eliteprime.app.ui.tv.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.eliteprime.app.util.DeviceRenderProfile
import androidx.tv.material3.Surface as TvSurface

/**
 * Global state to block background focus (Navbar, Lists) when a popup is open.
 * Provided at the Shell level.
 */
val LocalTvFocusManager = compositionLocalOf { mutableStateOf(false) }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSurfaceCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(
        focusedScale = if (DeviceRenderProfile.isLiteMode()) 1.0f else 1.02f
    ),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = Color.White.copy(alpha = 0.05f),
        focusedContainerColor = Color.White
    ),
    border: ClickableSurfaceBorder = ClickableSurfaceDefaults.border(
        focusedBorder = Border(BorderStroke(2.dp, Color.White))
    ),
    content: @Composable BoxScope.() -> Unit
) {
    TvSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = shape,
        scale = scale,
        colors = colors,
        border = border,
        content = content
    )
}

@Composable
fun TvGlassPane(
    modifier: Modifier = Modifier,
    backgroundColor: Color = TvColors.GlassBase,
    borderColor: Color = TvColors.GlassBorder,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape)
            .clip(shape)
    ) {
        content()
    }
}

@Composable
fun TvBadge(
    text: String,
    containerColor: Color = TvColors.Primary,
    contentColor: Color = Color.Black
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        androidx.compose.material3.Text(
            text = text.uppercase(),
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TvMetadataInfo(
    rating: Double?,
    year: String?,
    genre: String?,
    typeLabel: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (rating != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.Icon(
                    Icons.Default.Star,
                    null,
                    tint = TvColors.StarGold,
                    modifier = Modifier.size(16.dp)
                )
                androidx.compose.material3.Text(
                    text = "%.1f".format(rating),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        
        if (year != null) {
            androidx.compose.material3.Text(
                text = year,
                color = TvColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        if (genre != null) {
            androidx.compose.material3.Text(
                text = genre.uppercase(),
                color = TvColors.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        if (typeLabel != null) {
            TvBadge(text = typeLabel, containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)
        }
    }
}
