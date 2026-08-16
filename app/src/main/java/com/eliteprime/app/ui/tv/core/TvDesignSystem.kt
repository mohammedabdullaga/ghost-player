package com.eliteprime.app.ui.tv.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.eliteprime.app.ui.theme.AppColors

object TvColors {
    val Primary = AppColors.ArenaOrange
    val Background = Color(0xFF070B14)
    val Surface = Color(0xFF12151B)
    
    // Glassmorphism Palette
    val GlassBase = Color.Black.copy(alpha = 0.45f)
    val GlassBorder = Color.White.copy(alpha = 0.12f)
    val GlassSelection = Color.White.copy(alpha = 0.1f)
    
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.6f)
    val TextTertiary = Color.White.copy(alpha = 0.4f)
    
    val StarGold = Color(0xFFFFD700)
}

val TvColorScheme: ColorScheme = darkColorScheme(
    primary = TvColors.Primary,
    background = TvColors.Background,
    surface = TvColors.Surface,
    onPrimary = Color.Black,
    onBackground = TvColors.TextPrimary,
    onSurface = TvColors.TextPrimary
)

@Composable
fun ElitPrimeTvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        content = content
    )
}
