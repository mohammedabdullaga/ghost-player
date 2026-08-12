package com.eliteprime.app.ui.screens.settings

import android.content.Context
import com.eliteprime.app.R

data class SubtitleScaleOption(
    val scale: Float,
    val labelResId: Int
)

data class SubtitleColorOption(
    val colorArgb: Int,
    val labelResId: Int
)

fun subtitleSizeOptions(): List<SubtitleScaleOption> = listOf(
    SubtitleScaleOption(0.85f, R.string.settings_subtitle_size_small),
    SubtitleScaleOption(1.0f, R.string.settings_subtitle_size_default),
    SubtitleScaleOption(1.15f, R.string.settings_subtitle_size_large),
    SubtitleScaleOption(1.3f, R.string.settings_subtitle_size_extra_large)
)

fun subtitleTextColorOptions(): List<SubtitleColorOption> = listOf(
    SubtitleColorOption(0xFFFFFFFF.toInt(), R.string.settings_subtitle_color_white),
    SubtitleColorOption(0xFFFFEB3B.toInt(), R.string.settings_subtitle_color_yellow),
    SubtitleColorOption(0xFF80DEEA.toInt(), R.string.settings_subtitle_color_cyan),
    SubtitleColorOption(0xFFA5D6A7.toInt(), R.string.settings_subtitle_color_green)
)

fun subtitleBackgroundColorOptions(): List<SubtitleColorOption> = listOf(
    SubtitleColorOption(0x00000000, R.string.settings_subtitle_background_transparent),
    SubtitleColorOption(0x80000000.toInt(), R.string.settings_subtitle_background_dim),
    SubtitleColorOption(0xCC000000.toInt(), R.string.settings_subtitle_background_black),
    SubtitleColorOption(0xCC102A43.toInt(), R.string.settings_subtitle_background_blue)
)
