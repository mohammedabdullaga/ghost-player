package com.ghost.tv.ui.tv.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.ghost.tv.ui.theme.AppColors

object TvColors {
    val Primary = Color(0xFFA855F7) // Electric Purple
    val Background = Color(0xFF090514) // Deep Dark Purple-Black
    val Surface = Color(0xFF160D2B)
    
    // Glassmorphism Palette
    val GlassBase = Color(0xFF110A24).copy(alpha = 0.65f)
    val GlassBorder = Color(0xFFA855F7).copy(alpha = 0.20f)
    val GlassSelection = Color(0xFFA855F7).copy(alpha = 0.25f)
    
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFE2D9F3)
    val TextTertiary = Color(0xFFA394B8)
    
    val StarGold = Color(0xFFFBBF24)
}

val TvColorScheme: ColorScheme = darkColorScheme(
    primary = TvColors.Primary,
    background = TvColors.Background,
    surface = TvColors.Surface,
    onPrimary = Color.White,
    onBackground = TvColors.TextPrimary,
    onSurface = TvColors.TextPrimary
)

@Composable
fun GhostTvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        content = content
    )
}

@Composable
fun ElitPrimeTvTheme(
    content: @Composable () -> Unit
) {
    GhostTvTheme(content = content)
}
