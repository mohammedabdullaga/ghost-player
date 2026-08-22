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
    val targetBufferBytes: Int,
    val prioritizeTimeOverSizeThresholds: Boolean,
    val qualityReason: String = "baseline"
)

object PlaybackBufferPolicies {
    private const val DEFAULT_TARGET_BUFFER_BYTES = -1
    private const val MPEG_TS_LIVE_TARGET_BUFFER_BYTES = 16 * 1024 * 1024 // 16MB for TS to prevent RAM pressure
    private const val LARGE_LIVE_TARGET_BUFFER_BYTES = 32 * 1024 * 1024

    private const val LIVE_MIN_BUFFER_MS = 6_000 // Fast startup
    private const val LIVE_MAX_BUFFER_MS = 25_000
    private const val VOD_MIN_BUFFER_MS = 15_000 // Fast startup on VOD
    private const val VOD_MAX_BUFFER_MS = 45_000
    private const val PLAYBACK_BUFFER_MS = 1_500 // Start rendering quickly
    private const val REBUFFER_MS = 3_000
    private const val VOD_PLAYBACK_BUFFER_MS = 2_000 // Start VOD within 2s instead of 5s
    private const val VOD_REBUFFER_MS = 5_000

    private const val LARGE_LIVE_MIN_BUFFER_MS = 15_000
    private const val LARGE_LIVE_MAX_BUFFER_MS = 40_000
    private const val LARGE_LIVE_PLAYBACK_BUFFER_MS = 2_500
    private const val LARGE_LIVE_REBUFFER_MS = 6_000

    private const val UHD_MIN_WIDTH = 3800
    private const val UHD_MIN_HEIGHT = 2100
    private const val HIGH_BITRATE_THRESHOLD_BPS = 12_000_000 

    fun forPlayback(
        resolvedStreamType: ResolvedStreamType,
        observedVideoFormat: VideoFormat? = null,
        streamInfo: StreamInfo? = null
    ): PlaybackBufferPolicy {
        val qualityReason = highQualityReason(streamInfo, observedVideoFormat)
        
        return when {
            resolvedStreamType.isLive && qualityReason != null -> largeLivePolicy("auto-uhd-live", qualityReason)
            resolvedStreamType.isLive -> baselineLivePolicy(resolvedStreamType)
            else -> baselineVodPolicy()
        }
    }

    private fun baselineLivePolicy(resolvedStreamType: ResolvedStreamType): PlaybackBufferPolicy {
        val isMpegTs = resolvedStreamType == ResolvedStreamType.MPEG_TS_LIVE
        return PlaybackBufferPolicy(
            label = if (isMpegTs) "mpeg-ts-live" else "stable-live",
            minBufferMs = if (isMpegTs) 8_000 else LIVE_MIN_BUFFER_MS,
            maxBufferMs = if (isMpegTs) 30_000 else LIVE_MAX_BUFFER_MS,
            playbackBufferMs = PLAYBACK_BUFFER_MS,
            rebufferMs = REBUFFER_MS,
            targetBufferBytes = if (isMpegTs) MPEG_TS_LIVE_TARGET_BUFFER_BYTES else DEFAULT_TARGET_BUFFER_BYTES,
            prioritizeTimeOverSizeThresholds = true
        )
    }

    private fun largeLivePolicy(label: String, qualityReason: String): PlaybackBufferPolicy = PlaybackBufferPolicy(
        label = label,
        minBufferMs = LARGE_LIVE_MIN_BUFFER_MS,
        maxBufferMs = LARGE_LIVE_MAX_BUFFER_MS,
        playbackBufferMs = LARGE_LIVE_PLAYBACK_BUFFER_MS,
        rebufferMs = LARGE_LIVE_REBUFFER_MS,
        targetBufferBytes = LARGE_LIVE_TARGET_BUFFER_BYTES,
        prioritizeTimeOverSizeThresholds = true,
        qualityReason = qualityReason
    )

    private fun baselineVodPolicy(): PlaybackBufferPolicy = PlaybackBufferPolicy(
        label = "stable-vod",
        minBufferMs = VOD_MIN_BUFFER_MS,
        maxBufferMs = VOD_MAX_BUFFER_MS,
        playbackBufferMs = VOD_PLAYBACK_BUFFER_MS,
        rebufferMs = VOD_REBUFFER_MS,
        targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES,
        prioritizeTimeOverSizeThresholds = true
    )

    fun highQualityReason(streamInfo: StreamInfo?, observedVideoFormat: VideoFormat?): String? {
        observedVideoFormat?.let { format ->
            if (format.width >= UHD_MIN_WIDTH || format.height >= UHD_MIN_HEIGHT) return "observed-uhd"
            if (format.bitrate >= HIGH_BITRATE_THRESHOLD_BPS) return "observed-high-bitrate"
        }
        val searchable = listOfNotNull(
            streamInfo?.name,
            streamInfo?.url
        ).joinToString(" ").lowercase(Locale.ROOT)
        
        return when {
            "2160p" in searchable -> "metadata-2160p"
            "uhd" in searchable -> "metadata-uhd"
            "4k" in searchable -> "metadata-4k"
            else -> null
        }
    }
}
