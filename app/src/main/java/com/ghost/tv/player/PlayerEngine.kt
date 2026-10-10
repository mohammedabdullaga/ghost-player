package com.ghost.tv.player

import android.content.Context
import android.view.View
import androidx.media3.common.text.Cue
import com.ghost.tv.domain.model.*
import com.ghost.tv.player.playback.PlaybackErrorCategory
import com.ghost.tv.player.playback.PlayerErrorClassifier
import com.ghost.tv.player.timeshift.LiveTimeshiftState
import com.ghost.tv.player.timeshift.TimeshiftConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn

const val PLAYER_TRACK_AUTO_ID = "__auto__"

enum class PlayerType {
    VLC, EXO
}

enum class PlayerRenderSurfaceType {
    AUTO, SURFACE_VIEW, TEXTURE_VIEW
}

data class PlayerTrack(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
    val bitrate: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

interface PlayerEngine {
    val engineType: StateFlow<PlayerType>
    val playbackState: StateFlow<PlaybackState>
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    val videoFormat: StateFlow<VideoFormat>
    val error: Flow<PlayerError?>
    val retryStatus: StateFlow<PlayerRetryStatus?>
    val playerStats: StateFlow<PlayerStats>

    val availableAudioTracks: StateFlow<List<PlayerTrack>>
    val availableSubtitleTracks: StateFlow<List<PlayerTrack>>
    val availableVideoTracks: StateFlow<List<PlayerTrack>>
    val playbackSpeed: StateFlow<Float>
    val isMuted: StateFlow<Boolean>
    val timeshiftState: StateFlow<LiveTimeshiftState>
    val renderSurfaceType: StateFlow<PlayerRenderSurfaceType>
    val prepareStatus: StateFlow<StreamInfo?>

    fun prepare(streamInfo: StreamInfo)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun setMuted(muted: Boolean)
    fun toggleMute()
    fun setPlaybackSpeed(speed: Float)
    fun selectAudioTrack(trackId: String)
    fun selectVideoTrack(trackId: String)
    fun selectSubtitleTrack(trackId: String?)
    fun setSubtitleStyle(style: PlayerSubtitleStyle)
    fun setAudioDelay(delayMs: Long) {}
    fun setSubtitleDelay(delayMs: Long) {}
    fun renewStreamUrl(streamInfo: StreamInfo)
    fun release()

    fun createRenderView(
        context: Context,
        resizeMode: PlayerSurfaceResizeMode = PlayerSurfaceResizeMode.FIT,
        surfaceType: PlayerRenderSurfaceType = PlayerRenderSurfaceType.AUTO
    ): View
    
    fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode)
    fun unbindRenderView(renderView: View)
    fun clearRenderBinding()
}

data class PlayerRetryStatus(
    val attempt: Int,
    val maxAttempts: Int,
    val delayMs: Long
)

enum class PlayerSurfaceResizeMode {
    FIT, FILL, ZOOM, MATCH_PARENT
}

enum class PlaybackState {
    IDLE, BUFFERING, READY, ENDED, ERROR
}

data class PlayerStats(
    val videoCodec: String = "Unknown",
    val audioCodec: String = "Unknown",
    val videoDecoderName: String = "Unknown",
    val audioDecoderName: String = "Unknown",
    val videoBitrate: Int = 0,
    val droppedFrames: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val bufferedDurationMs: Long = 0
)

sealed class PlayerError(val message: String) {
    class NetworkError(message: String) : PlayerError(message)
    class SourceError(message: String) : PlayerError(message)
    class DecoderError(message: String) : PlayerError(message)
    class UnknownError(message: String) : PlayerError(message)

    companion object {
        @OptIn(UnstableApi::class)
        fun fromException(e: Throwable): PlayerError {
            val category = PlayerErrorClassifier.classify(e)
            val chain = generateSequence(e) { it.cause }.toList()
            val msg = when (category) {
                PlaybackErrorCategory.NETWORK -> buildNetworkErrorMessage(chain)
                PlaybackErrorCategory.HTTP_AUTH -> "Access denied (HTTP 401/403)."
                PlaybackErrorCategory.HTTP_SERVER -> "Server error."
                PlaybackErrorCategory.DECODER -> "Codec or decoder error."
                else -> e.message ?: "Unknown playback error"
            }
            return when (category) {
                PlaybackErrorCategory.NETWORK,
                PlaybackErrorCategory.HTTP_SERVER,
                PlaybackErrorCategory.HTTP_AUTH -> NetworkError(msg)
                PlaybackErrorCategory.SOURCE_MALFORMED,
                PlaybackErrorCategory.LIVE_WINDOW -> SourceError(msg)
                PlaybackErrorCategory.DECODER -> DecoderError(msg)
                else -> UnknownError(msg)
            }
        }

        private fun buildNetworkErrorMessage(chain: List<Throwable>): String = when {
            chain.any { it is SocketTimeoutException } -> "Connection timed out."
            chain.any { it is UnknownHostException } -> "Server not found."
            chain.any { it is ConnectException } -> "Could not connect to server."
            else -> "Network error."
        }
    }
}
