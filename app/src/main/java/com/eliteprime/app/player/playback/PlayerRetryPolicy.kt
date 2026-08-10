package com.eliteprime.app.player.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val LIVE_TRANSIENT_RETRY_ATTEMPTS = 10
private const val LIVE_HLS_MALFORMED_RETRY_ATTEMPTS_AFTER_START = 12

data class PlaybackRetryContext(
    val resolvedStreamType: ResolvedStreamType,
    val timeoutProfile: PlayerTimeoutProfile
) {
    val isLive: Boolean
        get() = resolvedStreamType == ResolvedStreamType.HLS ||
            resolvedStreamType == ResolvedStreamType.SMOOTH_STREAMING ||
            resolvedStreamType == ResolvedStreamType.MPEG_TS_LIVE ||
            resolvedStreamType == ResolvedStreamType.RTSP
}

@UnstableApi
class PlayerRetryPolicy(
    private val streamContext: PlaybackRetryContext,
    private val playbackStarted: () -> Boolean
) : LoadErrorHandlingPolicy {

    fun shouldRetry(
        error: Throwable,
        playbackStarted: Boolean,
        attempt: Int
    ): Boolean {
        val category = PlayerErrorClassifier.classify(error)
        if (playbackStarted && category in setOf(PlaybackErrorCategory.DECODER)) {
            return false
        }
        val maxAttempts = maxAttemptsFor(error, streamContext, playbackStarted)
        return attempt <= maxAttempts && maxAttempts > 0
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        return when {
            streamContext.isLive && dataType == C.DATA_TYPE_MANIFEST -> LIVE_TRANSIENT_RETRY_ATTEMPTS
            streamContext.isLive && dataType == C.DATA_TYPE_MEDIA -> LIVE_TRANSIENT_RETRY_ATTEMPTS
            dataType == C.DATA_TYPE_MANIFEST -> 3
            dataType == C.DATA_TYPE_MEDIA -> 2
            else -> 1
        }
    }

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val attempt = loadErrorInfo.errorCount
        val error = loadErrorInfo.exception
        val started = playbackStarted()
        
        if (!shouldRetry(error, started, attempt)) {
            return C.TIME_UNSET
        }
        
        return when (attempt) {
            1 -> 1_000L
            2 -> 2_500L
            else -> 5_000L
        }
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): LoadErrorHandlingPolicy.FallbackSelection? = null

    override fun onLoadTaskConcluded(loadTaskId: Long) = Unit

    private fun maxAttemptsFor(
        error: Throwable,
        streamContext: PlaybackRetryContext,
        playbackStarted: Boolean
    ): Int {
        return when (PlayerErrorClassifier.classify(error)) {
            PlaybackErrorCategory.DECODER,
            PlaybackErrorCategory.HTTP_AUTH -> 0

            PlaybackErrorCategory.LIVE_WINDOW -> 1
            PlaybackErrorCategory.HTTP_SERVER -> {
                if (streamContext.isLive) LIVE_TRANSIENT_RETRY_ATTEMPTS else 2
            }
            PlaybackErrorCategory.NETWORK -> when {
                error.hasCause<UnknownHostException>() -> 1
                error.hasCause<SocketTimeoutException>() || error.hasCause<ConnectException>() ->
                    if (streamContext.isLive) LIVE_TRANSIENT_RETRY_ATTEMPTS else 2
                !playbackStarted -> if (streamContext.isLive) LIVE_TRANSIENT_RETRY_ATTEMPTS else 2
                else -> 1
            }

            PlaybackErrorCategory.SOURCE_MALFORMED -> when {
                streamContext.resolvedStreamType == ResolvedStreamType.HLS && playbackStarted ->
                    LIVE_HLS_MALFORMED_RETRY_ATTEMPTS_AFTER_START
                else -> 1
            }
            else -> if (streamContext.isLive) 3 else 1
        }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        return generateSequence(this) { it.cause }.any { it is T }
    }
}
