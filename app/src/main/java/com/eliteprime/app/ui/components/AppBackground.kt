package com.eliteprime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// NOTE: Unused imports for the animated background are removed.
// Restore them when re-enabling the animated background:
//   import androidx.compose.animation.core.*
//   import androidx.compose.ui.geometry.Offset
//   import androidx.compose.ui.graphics.Brush
//   import androidx.compose.ui.graphics.graphicsLayer
//   import com.eliteprime.app.ui.theme.AppColors
//   import com.eliteprime.app.util.rememberIsLowEndDevice
//   import kotlin.math.cos
//   import kotlin.math.sin

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Temporarily commented out animated background to prevent
    // "setRequestedFrameRate frameRate=NaN" logcat flooding on API 35+.
    // The continuous infiniteTransition animations trigger a known Compose
    // frame-pacing issue. Uncomment when the Compose bug is resolved.

    // val isLowEnd = rememberIsLowEndDevice()
    // if (isLowEnd) { ... }
    // val infiniteTransition = rememberInfiniteTransition(...)
    // val phase by infiniteTransition.animateFloat(...)
    // GlowBubble(...) x4

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        content()
    }
}

@Composable
private fun GlowBubble(
    size: Int,
    color: Color,
    coreAlpha: Float,
    duration: Int,
    startX: Float,
    startY: Float,
    rangeX: Float,
    rangeY: Float
) {
    // Temporarily commented out. See AppBackground comment above.
    /*
    val infiniteTransition = rememberInfiniteTransition(label = "bubbleTransition")

    val xOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "x"
    )

    val yOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((duration * 1.3).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "y"
    )

    Box(
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer {
                translationX = startX.dp.toPx() + (sin(xOffset) * rangeX.dp.toPx())
                translationY = startY.dp.toPx() + (cos(yOffset) * rangeY.dp.toPx())
                val scale = 1f + (sin(xOffset) * 0.08f)
                scaleX = scale
                scaleY = scale
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = coreAlpha), Color.Transparent)
                )
            )
    )
    */
}
