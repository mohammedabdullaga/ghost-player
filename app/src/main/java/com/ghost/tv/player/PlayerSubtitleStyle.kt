package com.ghost.tv.player

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import androidx.media3.ui.CaptionStyleCompat

@OptIn(UnstableApi::class)
data class PlayerSubtitleStyle(
    val textScale: Float = 1.15f,
    val foregroundColorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundColorArgb: Int = 0x00000000,
    val edgeType: Int = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    val bottomPaddingFraction: Float = 0.08f,
    val useEmbeddedStyles: Boolean = true
)
