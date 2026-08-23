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

            val resId = if (isLite) R.drawable.lite_mode_background else R.drawable.app_background

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resId)
                    .size(if (shouldBlur) Size(480, 270) else Size.ORIGINAL) // Downsample heavily if we're blurring
                    .build(),
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
            
            // Commercial Grade Overlay: Opaque Black Layer with Transparency
            // This ensures backdrops on top show clearly without blending with the mesh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isLite) Color.Black.copy(alpha = 0.2f) // Light dimming for Lite mode
                        else if (shouldBlur) Color.Black.copy(alpha = 0.6f) 
                        else Color.Black.copy(alpha = 0.82f)
                    )
            )
        }
        content()
    }
}
