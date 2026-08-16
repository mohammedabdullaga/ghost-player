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

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    isTransparent: Boolean = false,
    blurRadius: Int = 50, // Increased blur for better separation
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTransparent) Color.Transparent else Color(0xFF070B14))
    ) {
        if (!isTransparent) {
            Image(
                painter = painterResource(id = R.drawable.app_background),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius.dp),
                contentScale = ContentScale.Crop
            )
            
            // Commercial Grade Overlay: Opaque Black Layer with Transparency
            // This ensures backdrops on top show clearly without blending with the mesh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }
        content()
    }
}
