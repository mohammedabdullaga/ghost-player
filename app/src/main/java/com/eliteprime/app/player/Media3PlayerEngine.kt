package com.eliteprime.app.player

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.VideoFormat
import com.eliteprime.app.player.playback.*
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
    private var boundPlayerView: PlayerView? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var positionUpdateJob: Job? = null
    private var currentBufferPolicy: PlaybackBufferPolicy? = null
    private var mediaSourceFactory: PlayerMediaSourceFactory? = null

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

    private val _prepareStatus = MutableStateFlow<StreamInfo?>(null)
    override val prepareStatus = _prepareStatus.asStateFlow()

    private var currentStreamInfo: StreamInfo? = null
    private var retryAttempt = 0

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
    }

    private fun initializePlayer(bufferPolicy: PlaybackBufferPolicy) {
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        mediaSourceFactory = PlayerMediaSourceFactory(context, dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferPolicy.minBufferMs,
                bufferPolicy.maxBufferMs,
                bufferPolicy.playbackBufferMs,
                bufferPolicy.rebufferMs
            )
            .setTargetBufferBytes(bufferPolicy.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(bufferPolicy.prioritizeTimeOverSizeThresholds)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(PlayerListener())
                playWhenReady = true
                
                // Bind to view immediately if one is waiting
                boundPlayerView?.player = this
            }
        
        currentBufferPolicy = bufferPolicy
    }

    override fun prepare(streamInfo: StreamInfo) {
        val resolvedType = StreamTypeResolver.resolve(streamInfo)
        val bufferPolicy = PlaybackBufferPolicies.forPlayback(resolvedType)

        if (exoPlayer == null || currentBufferPolicy?.label != bufferPolicy.label) {
            initializePlayer(bufferPolicy)
        }

        if (currentStreamInfo?.url == streamInfo.url && exoPlayer?.playbackState != Player.STATE_IDLE) {
            return
        }

        currentStreamInfo = streamInfo
        _prepareStatus.value = streamInfo
        retryAttempt = 0
        _retryStatus.value = null
        
        val isLocal = streamInfo.url.startsWith("file://") || 
                     streamInfo.url.startsWith("content://") || 
                     !streamInfo.url.contains("://")

        val timeoutProfile = PlayerTimeoutProfile.resolve(streamInfo, resolvedType, false)
        val retryPolicy = if (isLocal) {
            DefaultLoadErrorHandlingPolicy()
        } else {
            PlayerRetryPolicy(PlaybackRetryContext(resolvedType, timeoutProfile)) { 
                _playbackState.value == PlaybackState.READY || _playbackState.value == PlaybackState.BUFFERING 
            }
        }

        Log.d(TAG, "prepare: url=${streamInfo.url} resolvedType=$resolvedType isLocal=$isLocal")

        val mediaSource = mediaSourceFactory?.create(streamInfo, resolvedType, retryPolicy) ?: return
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
    }

    override fun play() { exoPlayer?.play() }
    override fun pause() { exoPlayer?.pause() }
    override fun stop() {
        exoPlayer?.stop()
        _playbackState.value = PlaybackState.IDLE
        _isPlaying.value = false
        positionUpdateJob?.cancel()
    }

    override fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    override fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
        _isMuted.value = volume == 0f
    }
    override fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        exoPlayer?.volume = if (muted) 0f else 1f
    }
    override fun toggleMute() { setMuted(!_isMuted.value) }
    override fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    override fun selectAudioTrack(trackId: String) { selectTrack(C.TRACK_TYPE_AUDIO, trackId) }
    override fun selectVideoTrack(trackId: String) { selectTrack(C.TRACK_TYPE_VIDEO, trackId) }
    override fun selectSubtitleTrack(trackId: String?) {
        if (trackId == null) {
            trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
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

    override fun setSubtitleStyle(style: PlayerSubtitleStyle) {}
    override fun renewStreamUrl(streamInfo: StreamInfo) {
        currentStreamInfo = streamInfo
        _prepareStatus.value = streamInfo
        val mediaItem = MediaItem.Builder()
            .setUri(streamInfo.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(streamInfo.name).build())
            .build()
        exoPlayer?.setMediaItem(mediaItem, false)
    }

    override fun release() {
        positionUpdateJob?.cancel()
        scope.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun createRenderView(context: Context, resizeMode: PlayerSurfaceResizeMode, surfaceType: PlayerRenderSurfaceType): View {
        return PlayerView(context).apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
            // Prevent PlayerView from stealing focus from Compose D-pad handlers
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            
            this.resizeMode = when (resizeMode) {
                PlayerSurfaceResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                PlayerSurfaceResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                PlayerSurfaceResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        }
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        val playerView = renderView as? PlayerView ?: return
        val player = exoPlayer ?: return
        
        if (boundPlayerView !== playerView) {
            Log.d(TAG, "bindRenderView: Switching from ${boundPlayerView?.hashCode()} to ${playerView.hashCode()}")
            
            // Use official Media3 switchTargetView for seamless handoff
            PlayerView.switchTargetView(player, boundPlayerView, playerView)
            
            boundPlayerView = playerView
        } else if (playerView.player !== player) {
            playerView.player = player
        }

        val targetResizeMode = when (resizeMode) {
            PlayerSurfaceResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerSurfaceResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerSurfaceResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

        if (playerView.resizeMode != targetResizeMode) {
            playerView.resizeMode = targetResizeMode
            playerView.requestLayout()
        }
    }

    override fun unbindRenderView(renderView: View) {
        val playerView = renderView as? PlayerView ?: return
        if (boundPlayerView === playerView) {
            Log.d(TAG, "unbindRenderView: Clearing current binding ${playerView.hashCode()}")
            playerView.player = null
            boundPlayerView = null
        }
    }

    override fun clearRenderBinding() {
        boundPlayerView?.player = null
        boundPlayerView = null
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
                    val format = exoPlayer?.videoFormat
                    _playerStats.value = _playerStats.value.copy(
                        width = format?.width ?: 0,
                        height = format?.height ?: 0,
                        videoCodec = format?.sampleMimeType ?: "Unknown",
                        videoDecoderName = "Internal Decoder"
                    )
                    PlaybackState.READY
                }
                Player.STATE_ENDED -> PlaybackState.ENDED
                Player.STATE_IDLE -> PlaybackState.IDLE
                else -> PlaybackState.IDLE
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) startPositionUpdates() else positionUpdateJob?.cancel()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _videoFormat.value = VideoFormat(videoSize.width, videoSize.height, 0f, 0)
            _playerStats.value = _playerStats.value.copy(width = videoSize.width, height = videoSize.height)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}", error)
            _error.tryEmit(PlayerError.fromException(error))
            _playerStats.value = _playerStats.value.copy(videoCodec = "ERROR: ${error.errorCodeName}")
            handleRetry()
        }

        override fun onTracksChanged(tracks: Tracks) { updateTrackLists() }
    }

    private fun handleRetry() {
        if (retryAttempt < MAX_RETRY_ATTEMPTS) {
            retryAttempt++
            val delayMs = 1000L * retryAttempt
            _retryStatus.value = PlayerRetryStatus(retryAttempt, MAX_RETRY_ATTEMPTS, delayMs)
            scope.launch {
                delay(delayMs)
                exoPlayer?.prepare()
            }
        } else {
            _playbackState.value = PlaybackState.ERROR
        }
    }

    private fun updateTrackLists() {
        val tracks = exoPlayer?.currentTracks ?: return
        _availableAudioTracks.value = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group ->
            (0 until group.length).map { i ->
                val format = group.getTrackFormat(i)
                PlayerTrack(id = format.id ?: i.toString(), label = format.label ?: format.language ?: "Audio ${i+1}", language = format.language, isSelected = group.isTrackSelected(i))
            }
        }
        _availableSubtitleTracks.value = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { group ->
            (0 until group.length).map { i ->
                val format = group.getTrackFormat(i)
                PlayerTrack(id = format.id ?: i.toString(), label = format.label ?: format.language ?: "Subtitle ${i+1}", language = format.language, isSelected = group.isTrackSelected(i))
            }
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                _currentPosition.value = exoPlayer?.currentPosition ?: 0
                _playerStats.value = _playerStats.value.copy(bufferedDurationMs = (exoPlayer?.bufferedPosition ?: 0) - (exoPlayer?.currentPosition ?: 0))
                delay(1000)
            }
        }
    }
}
