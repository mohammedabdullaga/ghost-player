package com.eliteprime.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.rememberIsLowEndDevice
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isLowEnd = rememberIsLowEndDevice()

    if (isLowEnd) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF070B14))
        ) {
            content()
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "backgroundTransition")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientPhase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.5f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1E293B), Color.Transparent),
                        center = Offset(900f + (250f * phase), -80f + (60f * phase)),
                        radius = 1300f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.14f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(AppColors.ArenaOrange, Color.Transparent),
                        center = Offset(150f * (1f - phase), 1150f - (150f * phase)),
                        radius = 1100f
                    )
                )
        )

        GlowBubble(size = 420, color = Color(0xFF1E293B), coreAlpha = 0.55f, duration = 32000, startX = -60f, startY = 160f, rangeX = 90f, rangeY = 120f)
        GlowBubble(size = 300, color = Color(0xFF312E81), coreAlpha = 0.45f, duration = 26000, startX = 420f, startY = 380f, rangeX = 110f, rangeY = 90f)
        GlowBubble(size = 220, color = AppColors.ArenaOrange, coreAlpha = 0.22f, duration = 18000, startX = 220f, startY = 220f, rangeX = 180f, rangeY = 160f)
        GlowBubble(size = 140, color = AppColors.ArenaOrange, coreAlpha = 0.16f, duration = 22000, startX = 520f, startY = 120f, rangeX = 140f, rangeY = 110f)

        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
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
}
