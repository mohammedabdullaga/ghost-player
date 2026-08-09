package com.eliteprime.app.player

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.VideoFormat
import com.eliteprime.app.player.playback.PlayerErrorClassifier
import com.eliteprime.app.player.timeshift.LiveTimeshiftState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class Media3PlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : PlayerEngine {

    private val TAG = "Media3PlayerEngine"
    private var exoPlayer: ExoPlayer? = null
    private val trackSelector = DefaultTrackSelector(context)
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration = _duration.asStateFlow()

    private val _videoFormat = MutableStateFlow(VideoFormat(0, 0, 0f, 0))
    override val videoFormat = _videoFormat.asStateFlow()

    private val _error = MutableSharedFlow<PlayerError?>(replay = 1)
    override val error = _error.asSharedFlow()

    private val _retryStatus = MutableStateFlow<PlayerRetryStatus?>(null)
    override val retryStatus = _retryStatus.asStateFlow()

    private val _playerStats = MutableStateFlow(PlayerStats())
    override val playerStats = _playerStats.asStateFlow()

    private val _availableAudioTracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val availableAudioTracks = _availableAudioTracks.asStateFlow()

    private val _availableSubtitleTracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val availableSubtitleTracks = _availableSubtitleTracks.asStateFlow()

    private val _availableVideoTracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val availableVideoTracks = _availableVideoTracks.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    override val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted = _isMuted.asStateFlow()

    private val _timeshiftState = MutableStateFlow(LiveTimeshiftState())
    override val timeshiftState = _timeshiftState.asStateFlow()

    private val _renderSurfaceType = MutableStateFlow(PlayerRenderSurfaceType.SURFACE_VIEW)
    override val renderSurfaceType = _renderSurfaceType.asStateFlow()

    private var currentStreamInfo: StreamInfo? = null
    private var retryAttempt = 0

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
    }

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("ElitePrime/1.0")

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(PlayerListener())
                playWhenReady = true
            }
    }

    override fun prepare(streamInfo: StreamInfo) {
        currentStreamInfo = streamInfo
        retryAttempt = 0
        _retryStatus.value = null
        
        val mediaItem = MediaItem.Builder()
            .setUri(streamInfo.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(streamInfo.name).build())
            .build()
        
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
    }

    override fun play() {
        exoPlayer?.play()
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun stop() {
        exoPlayer?.stop()
        _playbackState.value = PlaybackState.IDLE
        _isPlaying.value = false
        positionUpdateJob?.cancel()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
        _isMuted.value = volume == 0f
    }

    override fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        exoPlayer?.volume = if (muted) 0f else 1f
    }

    override fun toggleMute() {
        setMuted(!_isMuted.value)
    }

    override fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    override fun selectAudioTrack(trackId: String) {
        selectTrack(C.TRACK_TYPE_AUDIO, trackId)
    }

    override fun selectVideoTrack(trackId: String) {
        selectTrack(C.TRACK_TYPE_VIDEO, trackId)
    }

    override fun selectSubtitleTrack(trackId: String?) {
        if (trackId == null) {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            selectTrack(C.TRACK_TYPE_TEXT, trackId)
        }
    }

    private fun selectTrack(trackType: Int, trackId: String) {
        val groups = exoPlayer?.currentTracks?.groups ?: return
        for (group in groups) {
            if (group.type == trackType) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.id == trackId) {
                        trackSelector.parameters = trackSelector.buildUponParameters()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            .build()
                        return
                    }
                }
            }
        }
    }

    override fun setSubtitleStyle(style: PlayerSubtitleStyle) {
        // Implementation for subtitle style updates if needed
    }

    override fun release() {
        positionUpdateJob?.cancel()
        scope.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun createRenderView(
        context: Context,
        resizeMode: PlayerSurfaceResizeMode,
        surfaceType: PlayerRenderSurfaceType
    ): View {
        _renderSurfaceType.value = surfaceType
        val view = if (surfaceType == PlayerRenderSurfaceType.TEXTURE_VIEW) {
            TextureView(context)
        } else {
            SurfaceView(context)
        }
        
        bindRenderView(view, resizeMode)
        return view
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        when (renderView) {
            is SurfaceView -> exoPlayer?.setVideoSurfaceView(renderView)
            is TextureView -> exoPlayer?.setVideoTextureView(renderView)
        }
        // Resize mode is usually handled by the layout container (e.g., AspectRatioFrameLayout)
        // but we could set it on a player-provided view if we had one.
    }

    override fun clearRenderBinding() {
        exoPlayer?.clearVideoSurface()
    }

    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = when (state) {
                Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                Player.STATE_READY -> {
                    _duration.value = exoPlayer?.duration?.coerceAtLeast(0) ?: 0
                    updateTrackLists()
                    retryAttempt = 0
                    _retryStatus.value = null
                    PlaybackState.READY
                }
                Player.STATE_ENDED -> PlaybackState.ENDED
                Player.STATE_IDLE -> PlaybackState.IDLE
                else -> PlaybackState.IDLE
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startPositionUpdates()
            } else {
                positionUpdateJob?.cancel()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _videoFormat.value = VideoFormat(videoSize.width, videoSize.height, 0f, 0)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}", error)
            _error.tryEmit(PlayerError.fromException(error))
            handleRetry()
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateTrackLists()
        }
    }

    private fun handleRetry() {
        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
            retryAttempt++
            val delayMs = 1000L * retryAttempt
            _retryStatus.value = PlayerRetryStatus(retryAttempt, MAX_RETRY_ATTEMPTS, delayMs)
            
            scope.launch {
                delay(delayMs)
                Log.i(TAG, "Retrying playback, attempt $retryAttempt")
                exoPlayer?.prepare()
            }
        } else {
            _playbackState.value = PlaybackState.ERROR
        }
    }

    private fun updateTrackLists() {
        val tracks = exoPlayer?.currentTracks ?: return
        
        _availableAudioTracks.value = tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group ->
                (0 until group.length).map { i ->
                    val format = group.getTrackFormat(i)
                    PlayerTrack(
                        id = format.id ?: i.toString(),
                        label = format.label ?: format.language ?: "Audio ${i+1}",
                        language = format.language,
                        isSelected = group.isTrackSelected(i)
                    )
                }
            }

        _availableSubtitleTracks.value = tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group ->
                (0 until group.length).map { i ->
                    val format = group.getTrackFormat(i)
                    PlayerTrack(
                        id = format.id ?: i.toString(),
                        label = format.label ?: format.language ?: "Subtitle ${i+1}",
                        language = format.language,
                        isSelected = group.isTrackSelected(i)
                    )
                }
            }
            
        _availableVideoTracks.value = tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).map { i ->
                    val format = group.getTrackFormat(i)
                    PlayerTrack(
                        id = format.id ?: i.toString(),
                        label = "${format.width}x${format.height}",
                        isSelected = group.isTrackSelected(i),
                        bitrate = format.bitrate,
                        width = format.width,
                        height = format.height
                    )
                }
            }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                _currentPosition.value = exoPlayer?.currentPosition ?: 0
                _playerStats.value = _playerStats.value.copy(
                    bufferedDurationMs = (exoPlayer?.bufferedPosition ?: 0) - (exoPlayer?.currentPosition ?: 0)
                )
                delay(1000)
            }
        }
    }
}
