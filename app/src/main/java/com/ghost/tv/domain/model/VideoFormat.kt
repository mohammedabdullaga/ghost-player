package com.ghost.tv.domain.model

data class VideoFormat(
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val bitrate: Int,
    val codec: String? = null
)
