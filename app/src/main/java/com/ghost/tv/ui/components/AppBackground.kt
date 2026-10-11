package com.ghost.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.ghost.tv.R
import com.ghost.tv.util.LocalRenderTier
import com.ghost.tv.util.RenderTier

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    isTransparent: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val tier = LocalRenderTier.current
    val isLite = tier == RenderTier.LITE
    val shouldBlur = tier != RenderTier.REDUCED && !isLite

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTransparent) Color.Transparent else Color(0xFF090514))
    ) {
        if (!isTransparent) {
            val imageModifier = if (shouldBlur) {
                Modifier.fillMaxSize().blur(20.dp)
            } else {
                Modifier.fillMaxSize()
            }

            val resId = R.drawable.ghost_app_background

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resId)
                    .size(if (shouldBlur) Size(480, 270) else Size.ORIGINAL)
                    .build(),
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
            
            // Glassmorphic Dark Purple Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090514).copy(alpha = 0.45f))
            )
        }
        content()
    }
}
