package com.eliteprime.app.player.playback

import com.eliteprime.app.player.PlaybackState

data class PlaybackBufferPolicy(
    val label: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
    val targetBufferBytes: Int = -1,
    val prioritizeTimeOverSizeThresholds: Boolean = true
)

object PlaybackBufferPolicies {
    private const val LIVE_MIN_BUFFER_MS = 8_000
    private const val LIVE_MAX_BUFFER_MS = 30_000
    private const val VOD_MIN_BUFFER_MS = 45_000
    private const val VOD_MAX_BUFFER_MS = 90_000
    private const val PLAYBACK_BUFFER_MS = 1_500
    private const val REBUFFER_MS = 5_000

    private const val MAX_BUFFER_BYTES = 50 * 1024 * 1024 // 50MB max to prevent OOM

    fun forPlayback(resolvedStreamType: ResolvedStreamType): PlaybackBufferPolicy {
        return if (resolvedStreamType.isLive) {
            PlaybackBufferPolicy(
                label = "stable-live",
                minBufferMs = LIVE_MIN_BUFFER_MS,
                maxBufferMs = LIVE_MAX_BUFFER_MS,
                playbackBufferMs = PLAYBACK_BUFFER_MS,
                rebufferMs = REBUFFER_MS,
                targetBufferBytes = MAX_BUFFER_BYTES
            )
        } else {
            PlaybackBufferPolicy(
                label = "stable-vod",
                minBufferMs = VOD_MIN_BUFFER_MS,
                maxBufferMs = VOD_MAX_BUFFER_MS,
                playbackBufferMs = 3_000,
                rebufferMs = 8_000,
                targetBufferBytes = MAX_BUFFER_BYTES
            )
        }
    }
}
