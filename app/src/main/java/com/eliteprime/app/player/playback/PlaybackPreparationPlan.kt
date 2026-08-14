package com.eliteprime.app.player.playback

import com.eliteprime.app.domain.model.StreamInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

@UnstableApi
internal data class PlaybackPreparationPlan(
    val resolvedStreamType: ResolvedStreamType,
    val timeoutProfile: PlayerTimeoutProfile,
    val retryPolicy: LoadErrorHandlingPolicy
)

@UnstableApi
internal fun buildPlaybackPreparationPlan(
    streamInfo: StreamInfo,
    isReady: () -> Boolean
): PlaybackPreparationPlan {
    val resolvedStreamType = StreamTypeResolver.resolve(streamInfo)
    val timeoutProfile = PlayerTimeoutProfile.resolve(streamInfo, resolvedStreamType)
    
    val isLocal = streamInfo.url.startsWith("file://") || 
                 streamInfo.url.startsWith("content://") || 
                 !streamInfo.url.contains("://")
                 
    val retryPolicy = if (isLocal) {
        DefaultLoadErrorHandlingPolicy()
    } else {
        PlayerRetryPolicy(PlaybackRetryContext(resolvedStreamType, timeoutProfile), isReady)
    }

    return PlaybackPreparationPlan(
        resolvedStreamType = resolvedStreamType,
        timeoutProfile = timeoutProfile,
        retryPolicy = retryPolicy
    )
}
