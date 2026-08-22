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
    val shouldBlur = tier != RenderTier.REDUCED

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTransparent) Color.Transparent else Color(0xFF070B14))
    ) {
        if (!isTransparent) {
            val imageModifier = if (shouldBlur) {
                Modifier.fillMaxSize().blur(50.dp)
            } else {
                Modifier.fillMaxSize()
            }

            Image(
                painter = painterResource(id = R.drawable.app_background),
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
            
            // Commercial Grade Overlay: Opaque Black Layer with Transparency
            // This ensures backdrops on top show clearly without blending with the mesh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (shouldBlur) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.82f))
            )
        }
        content()
    }
}
