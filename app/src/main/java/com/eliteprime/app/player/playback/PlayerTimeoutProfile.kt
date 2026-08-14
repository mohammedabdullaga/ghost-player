package com.eliteprime.app.player.playback

import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.StreamType

enum class PlayerTimeoutProfile(
    val connectTimeoutMs: Long,
    val readTimeoutMs: Long,
    val writeTimeoutMs: Long
) {
    LIVE(
        connectTimeoutMs = 12_000L,
        readTimeoutMs = 20_000L,
        writeTimeoutMs = 20_000L
    ),
    VOD(
        connectTimeoutMs = 15_000L,
        readTimeoutMs = 45_000L,
        writeTimeoutMs = 30_000L
    ),
    PROGRESSIVE(
        connectTimeoutMs = 5_000L,
        readTimeoutMs = 10_000L,
        writeTimeoutMs = 30_000L
    );

    companion object {
        fun resolve(
            streamInfo: StreamInfo,
            resolvedStreamType: ResolvedStreamType
        ): PlayerTimeoutProfile {
            return when {
                resolvedStreamType == ResolvedStreamType.HLS -> LIVE
                resolvedStreamType == ResolvedStreamType.SMOOTH_STREAMING -> LIVE
                resolvedStreamType == ResolvedStreamType.MPEG_TS_LIVE -> LIVE
                resolvedStreamType == ResolvedStreamType.RTSP -> LIVE
                resolvedStreamType == ResolvedStreamType.PROGRESSIVE -> PROGRESSIVE
                streamInfo.streamType == StreamType.PROGRESSIVE -> PROGRESSIVE
                else -> VOD
            }
        }
    }
}
