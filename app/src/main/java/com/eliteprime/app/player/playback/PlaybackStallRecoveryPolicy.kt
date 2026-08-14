package com.eliteprime.app.player.playback

import com.eliteprime.app.player.PlaybackState

internal fun shouldRecoverReadyStalls(resolvedStreamType: ResolvedStreamType): Boolean =
    true

internal fun shouldRecoverPositionAdvancingReadyStalls(resolvedStreamType: ResolvedStreamType): Boolean =
    !resolvedStreamType.isLive

internal fun shouldRecoverFrameSilentReadyStalls(resolvedStreamType: ResolvedStreamType): Boolean =
    resolvedStreamType.isLive

internal fun shouldReconnectLiveStall(
    playbackState: PlaybackState,
    resolvedStreamType: ResolvedStreamType,
    recoveryAttempt: Int
): Boolean =
    recoveryAttempt <= 1 &&
        (
            playbackState == PlaybackState.BUFFERING && resolvedStreamType.isLive ||
                playbackState == PlaybackState.READY && resolvedStreamType.isLive
        )
