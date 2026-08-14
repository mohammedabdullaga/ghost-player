package com.eliteprime.app.player.playback

import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.VideoFormat
import java.util.Locale

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

    // LARGE/UHD Buffer settings
    private const val LARGE_LIVE_MIN_BUFFER_MS = 25_000
    private const val LARGE_LIVE_MAX_BUFFER_MS = 75_000
    private const val LARGE_LIVE_PLAYBACK_BUFFER_MS = 5_000
    private const val LARGE_LIVE_REBUFFER_MS = 15_000

    private const val MAX_BUFFER_BYTES = 64 * 1024 * 1024 // 64MB - Balanced for 4K without OOM risk

    fun forPlayback(
        resolvedStreamType: ResolvedStreamType,
        observedFormat: VideoFormat? = null,
        streamInfo: StreamInfo? = null
    ): PlaybackBufferPolicy {
        val isUhd = isHighQualityStream(observedFormat, streamInfo)
        
        return if (resolvedStreamType.isLive) {
            if (isUhd) {
                PlaybackBufferPolicy(
                    label = "large-live-uhd",
                    minBufferMs = LARGE_LIVE_MIN_BUFFER_MS,
                    maxBufferMs = LARGE_LIVE_MAX_BUFFER_MS,
                    playbackBufferMs = LARGE_LIVE_PLAYBACK_BUFFER_MS,
                    rebufferMs = LARGE_LIVE_REBUFFER_MS,
                    targetBufferBytes = MAX_BUFFER_BYTES
                )
            } else {
                PlaybackBufferPolicy(
                    label = "stable-live",
                    minBufferMs = LIVE_MIN_BUFFER_MS,
                    maxBufferMs = LIVE_MAX_BUFFER_MS,
                    playbackBufferMs = PLAYBACK_BUFFER_MS,
                    rebufferMs = REBUFFER_MS,
                    targetBufferBytes = MAX_BUFFER_BYTES
                )
            }
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

    fun isHighQualityStream(format: VideoFormat?, info: StreamInfo?): Boolean {
        if (format != null) {
            // 4K resolution check (allowing for slight variations)
            if (format.width >= 3800 || format.height >= 2100) return true
            // High bitrate check (e.g. > 15Mbps)
            if (format.bitrate > 15_000_000) return true
        }
        
        if (info != null) {
            val title = info.name.lowercase(Locale.ROOT)
            if (title.contains("4k") || title.contains("uhd") || title.contains("2160")) return true
        }
        
        return false
    }
}
