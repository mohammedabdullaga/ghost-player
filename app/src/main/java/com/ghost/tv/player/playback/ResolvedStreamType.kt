package com.ghost.tv.player.playback

enum class ResolvedStreamType {
    HLS,
    DASH,
    SMOOTH_STREAMING,
    PROGRESSIVE,
    MPEG_TS_LIVE,
    RTSP,
    UNKNOWN;

    val isLive: Boolean
        get() = this == HLS || this == DASH || this == SMOOTH_STREAMING || this == MPEG_TS_LIVE || this == RTSP
}
