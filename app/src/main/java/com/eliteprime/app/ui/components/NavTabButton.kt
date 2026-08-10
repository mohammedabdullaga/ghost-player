package com.eliteprime.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice

@Composable
fun NavTabButton(
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
                .height(42.dp)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.15f) else Color.Transparent,
                focusedContainerColor = AppColors.ArenaOrange.copy(alpha = 0.3f)
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            NavTabButtonContent(label, icon, isActive || isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.height(40.dp),
            color = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.12f) else Color.Transparent,
            shape = RoundedCornerShape(12.dp)
        ) {
            NavTabButtonContent(label, icon, isActive)
        }
    }
}

@Composable
private fun NavTabButtonContent(label: String, icon: ImageVector, active: Boolean) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}
