package com.eliteprime.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.eliteprime.app.R
import com.eliteprime.app.util.LocalRenderTier
import com.eliteprime.app.util.RenderTier

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
            .background(if (isTransparent) Color.Transparent else Color(0xFF070B14))
    ) {
        if (!isTransparent) {
            val imageModifier = if (shouldBlur) {
                Modifier.fillMaxSize().blur(20.dp)
            } else {
                Modifier.fillMaxSize()
            }

            val resId = R.drawable.new_tv_background

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resId)
                    .size(if (shouldBlur) Size(480, 270) else Size.ORIGINAL) // Downsample heavily if we're blurring
                    .build(),
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
            
            // Glassmorphic Transparent Background Layer
            // Light dimming layer so the background artwork/mesh shines through
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
        }
        content()
    }
}
