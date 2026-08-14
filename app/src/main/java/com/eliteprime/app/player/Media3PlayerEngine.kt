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
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.StreamType
import com.eliteprime.app.domain.model.VideoFormat
import com.eliteprime.app.player.playback.*
import com.eliteprime.app.player.timeshift.LiveTimeshiftState
import android.os.Build
import android.os.Handler
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import com.eliteprime.app.player.playback.CapabilityAwareMediaCodecSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Okio
import okio.Source
import android.view.LayoutInflater
import com.eliteprime.app.R
import okio.buffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class Media3PlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val preferenceRepository: PreferenceRepository
) : PlayerEngine {

    private val TAG = "Media3PlayerEngine"
    private var exoPlayer: ExoPlayer? = null
    private var currentTrackSelector: DefaultTrackSelector? = null
    private var boundPlayerView: PlayerView? = null
    // A view that requested binding before the ExoPlayer instance existed yet.
    private var pendingRenderView: PlayerView? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var positionUpdateJob: Job? = null
    private var currentBufferPolicy: PlaybackBufferPolicy? = null
    private var mediaSourceFactory: PlayerMediaSourceFactory? = null
    private val subtitleStyleController = SubtitleStyleController()
    private var prepareStartTime: Long = 0
    private var playbackWatchdogJob: Job? = null
    private var lastRenderedCount: Int = -1
    private var lastRenderedTime: Long = 0
    private var isRecoveringFromStall: Boolean = false
    private var forceNextCodecReset: Boolean = false

    private val playerOkHttpClient = okHttpClient.newBuilder()
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val isTs = url.contains(".ts") || url.contains("ext=ts")
            
            val response = chain.proceed(request)
            if (isTs) {
                val body = response.body
                if (body != null) {
                    Log.d("NetworkWatchdog", "Starting TS stream read: $url")
                    val progressBody = object : ResponseBody() {
                        private var totalBytesRead = 0L
                        private var lastLogTime = 0L

                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun source(): BufferedSource {
                            val bodySource = body.source()
                            val interceptorSource = object : ForwardingSource(bodySource) {
                                override fun read(sink: Buffer, byteCount: Long): Long {
                                    val bytesRead = super.read(sink, byteCount)
                                    if (bytesRead != -1L) {
                                        totalBytesRead += bytesRead
                                        val now = System.currentTimeMillis()
                                        if (now - lastLogTime > 5000) {
                                            Log.d("NetworkWatchdog", "TS Read Progress: ${totalBytesRead / 1024} KB | URL: ${url.take(60)}...")
                                            lastLogTime = now
                                        }
                                    }
                                    return bytesRead
                                }
                            }
                            return (interceptorSource as Source).buffer()
                        }
                    }
                    response.newBuilder().body(progressBody).build()
                } else response
            } else response
        }
        .build()

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
        val httpDataSourceFactory = OkHttpDataSource.Factory(playerOkHttpClient)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        mediaSourceFactory = PlayerMediaSourceFactory(context, dataSourceFactory)

        // Adjust buffer policy based on user preferences, but NEVER downgrade a UHD policy
        val userBufferMs = preferenceRepository.liveBufferSize.value
        val isUhdPolicy = bufferPolicy.label.contains("uhd")
        
        val effectiveBufferPolicy = if (userBufferMs > 0 && !isUhdPolicy && currentStreamInfo?.streamType == StreamType.MPEG_TS) {
            bufferPolicy.copy(
                label = "user-stable-live",
                minBufferMs = userBufferMs,
                maxBufferMs = (userBufferMs * 3).coerceAtLeast(30000),
                rebufferMs = (userBufferMs / 2).coerceAtLeast(3000)
            )
        } else bufferPolicy

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                effectiveBufferPolicy.minBufferMs,
                effectiveBufferPolicy.maxBufferMs,
                effectiveBufferPolicy.playbackBufferMs,
                effectiveBufferPolicy.rebufferMs
            )
            .setTargetBufferBytes(effectiveBufferPolicy.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(effectiveBufferPolicy.prioritizeTimeOverSizeThresholds)
            .build()

        val decoderMode = preferenceRepository.decoderMode.value
        val codecSelector = CapabilityAwareMediaCodecSelector(context, decoderMode)

        // CREATE FRESH TRACK SELECTOR FOR NEW PLAYER INSTANCE
        val newTrackSelector = DefaultTrackSelector(context)
        currentTrackSelector = newTrackSelector

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: Handler,
                eventListener: VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                // Add our custom capability-aware renderer first
                out.add(object : MediaCodecVideoRenderer(
                    context,
                    mediaCodecSelector,
                    allowedVideoJoiningTimeMs,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    100 // INCREASE DROPPED FRAME TOLERANCE FOR 4K
                ) {
                    override fun getDecoderInfos(
                        selector: MediaCodecSelector,
                        format: Format,
                        requiresSecure: Boolean
                    ): List<MediaCodecInfo> {
                        return (selector as CapabilityAwareMediaCodecSelector).getRankedDecoderInfos(
                            format.sampleMimeType!!,
                            requiresSecure,
                            false,
                            format
                        )
                    }

                    override fun canReuseCodec(
                        codecInfo: MediaCodecInfo,
                        oldFormat: Format,
                        newFormat: Format
                    ): DecoderReuseEvaluation {
                        var evaluation = super.canReuseCodec(codecInfo, oldFormat, newFormat)
                        
                        val isAmlogic = Build.HARDWARE.lowercase().contains("amlogic") || Build.BOARD.lowercase().contains("amlogic")
                        val isHighRes = newFormat.width >= 1920 || newFormat.height >= 1080
                        
                        // AGGRESSIVE: NO REUSE for Amlogic 4K/HD to prevent artifacts (missing pixels)
                        if (isAmlogic && isHighRes && evaluation.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                            Log.w("CodecSelector", "Forcing NO_REUSE for Amlogic 4K/HD stability")
                            evaluation = DecoderReuseEvaluation(
                                codecInfo.name,
                                oldFormat,
                                newFormat,
                                DecoderReuseEvaluation.REUSE_RESULT_NO,
                                evaluation.discardReasons or DecoderReuseEvaluation.DISCARD_REASON_VIDEO_RESOLUTION_CHANGED
                            )
                        }
                        
                        return evaluation
                    }
                })
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setMediaCodecSelector(codecSelector)
            .setEnableDecoderFallback(true) // ALLOW MEDIA3 TO TRY NEXT DECODER IF PRIMARY FAILS
            
        // Setup Extension Mode
        if (decoderMode == "SOFTWARE") {
            renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        } else if (decoderMode == "HARDWARE") {
            renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        // OFF-LOAD RELEASE: Releasing a potentially stuck hardware decoder on the Main thread 
        // is the primary cause of ANRs during stall recovery. However, ExoPlayer REQUIRES 
        // release on the application thread. We call stop() SYNCHRONOUSLY to signal to the
        // hardware that resources can be reclaimed, then release in background.
        val playerToRelease = exoPlayer
        exoPlayer = null
        playerToRelease?.let { old ->
            try {
                old.stop() // Immediate stop to free hardware
            } catch (e: Exception) {
                Log.e("Media3Engine", "Player stop failed", e)
            }
            scope.launch(Dispatchers.Main) {
                try {
                    old.release() // Background release for the slow part
                } catch (e: Exception) {
                    Log.e("Media3Engine", "Player background release failed", e)
                }
            }
        }

        val livePlaybackSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(1.0f)
            .setFallbackMaxPlaybackSpeed(1.0f)
            .build()

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(newTrackSelector)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(livePlaybackSpeedControl)
            .build()
            .apply {
                addListener(PlayerListener())
                playWhenReady = true
            }

        // RE-BIND PERSISTENT VIEW: If we have an existing view bound, or a pending one, 
        // attach the new player instance to it immediately.
        val viewToBind = boundPlayerView ?: pendingRenderView
        viewToBind?.let { view ->
            Log.d(TAG, "initializePlayer: Re-attaching new player instance to view ${view.hashCode()}")
            view.player = exoPlayer
            boundPlayerView = view
            pendingRenderView = null
        }

        currentBufferPolicy = effectiveBufferPolicy
    }

    override fun prepare(streamInfo: StreamInfo) {
        prepareStartTime = System.currentTimeMillis()
        val resolvedType = StreamTypeResolver.resolve(streamInfo)
        val bufferPolicy = PlaybackBufferPolicies.forPlayback(resolvedType, _videoFormat.value, streamInfo)

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

        Log.d(TAG, "prepare: url=${streamInfo.url} resolvedType=$resolvedType isLocal=$isLocal streamInfo.mimeType=${streamInfo.streamType}")

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
        val selector = currentTrackSelector ?: return
        if (trackId == null) {
            selector.parameters = selector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            selector.parameters = selector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
            selectTrack(C.TRACK_TYPE_TEXT, trackId)
        }
    }

    private fun selectTrack(trackType: Int, trackId: String) {
        val player = exoPlayer ?: return
        val selector = currentTrackSelector ?: return
        val groups = player.currentTracks.groups
        for (group in groups) {
            if (group.type == trackType) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.id == trackId) {
                        selector.parameters = selector.buildUponParameters()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            .build()
                        return
                    }
                }
            }
        }
    }

    override fun setSubtitleStyle(style: PlayerSubtitleStyle) {
        subtitleStyleController.updateStyle(style)
        boundPlayerView?.let { subtitleStyleController.apply(it) }
    }

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
        playbackWatchdogJob?.cancel()
        positionUpdateJob?.cancel()
        scope.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun createRenderView(context: Context, resizeMode: PlayerSurfaceResizeMode, surfaceType: PlayerRenderSurfaceType): View {
        val layoutId = if (surfaceType == PlayerRenderSurfaceType.TEXTURE_VIEW) {
            R.layout.player_texture
        } else {
            R.layout.player_surface
        }
        
        val view = LayoutInflater.from(context).inflate(layoutId, null) as PlayerView
        return view.apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
            
            if (surfaceType == PlayerRenderSurfaceType.TEXTURE_VIEW) {
                Log.d(TAG, "Created PlayerView with TextureView for better transform sync")
            }

            // Prevent PlayerView from stealing focus from Compose D-pad handlers
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            
            // FIX: Set to FILL and let Compose handle aspect ratio via the pre-size modifier
            // This prevents PlayerView's internal layout pass from triggering a second surface cycle.
            this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

            // Diagnostic: Track size/layout changes to detect double-reconfig
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                    Log.v("PlayerLayout", "PlayerView size changed: ${right-left}x${bottom-top} (was ${oldRight-oldLeft}x${oldBottom-oldTop}) | view=${this.hashCode()}")
                }
            }
        }
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        val playerView = renderView as? PlayerView ?: return
        val player = exoPlayer
        if (player == null) {
            // Player isn't ready yet (e.g. still fetching EPG/resolving the stream).
            // Remember this view and bind it once the player is created.
            pendingRenderView = playerView
            return
        }

        val targetResizeMode = when (resizeMode) {
            PlayerSurfaceResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerSurfaceResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerSurfaceResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

        Log.d("PlayerBinding", "bindRenderView: view=${playerView.hashCode()} | targetResizeMode=$targetResizeMode | currentResizeMode=${playerView.resizeMode}")

        // 1. Resize mode is now handled by AspectRatioFrameLayout.RESIZE_MODE_FILL 
        // and Compose modifier. No need to update it here and risk a layout pass.
        
        if (boundPlayerView !== playerView) {
            val isHandoff = boundPlayerView != null
            Log.d(TAG, "bindRenderView: ${if (isHandoff) "HANDOFF" else "COLD ATTACH"} Switching from ${boundPlayerView?.hashCode()} to ${playerView.hashCode()}")
            
            // PRE-EMPTIVE RESET: If Amlogic HEVC, force a codec reset on the next opportunity
            val isAmlogic = Build.HARDWARE.lowercase().contains("amlogic") || Build.BOARD.lowercase().contains("amlogic")
            val isHevc = currentStreamInfo?.url?.let { it.contains("hevc", true) || it.contains("h265", true) } ?: true
            if (isAmlogic && isHevc && isHandoff) {
                Log.w(TAG, "  - Pre-emptively flagging codec reset for Amlogic HEVC handoff")
                forceNextCodecReset = true
            }

            Log.d("PlayerBinding", "  - StackTrace for context: ${Log.getStackTraceString(Throwable()).take(500)}...")
            
            // Use official Media3 switchTargetView for seamless handoff
            PlayerView.switchTargetView(player, boundPlayerView, playerView)
            
            boundPlayerView = playerView
        } else if (playerView.player !== player) {
            Log.d("PlayerBinding", "  - Re-binding player to existing view")
            playerView.player = player
        }

        subtitleStyleController.apply(playerView)
    }

    override fun unbindRenderView(renderView: View) {
        val playerView = renderView as? PlayerView ?: return
        if (pendingRenderView === playerView) {
            pendingRenderView = null
        }
        if (boundPlayerView === playerView) {
            Log.d(TAG, "unbindRenderView: Clearing current binding ${playerView.hashCode()}")
            playerView.player = null
            boundPlayerView = null
        }
    }

    override fun clearRenderBinding() {
        boundPlayerView?.player = null
        boundPlayerView = null
        pendingRenderView = null
    }

    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val elapsed = if (prepareStartTime > 0) System.currentTimeMillis() - prepareStartTime else 0
            val stateName = when (state) {
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                Player.STATE_IDLE -> "IDLE"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "onPlaybackStateChanged: $stateName | Elapsed since prepare: ${elapsed}ms")

            _playbackState.value = when (state) {
                Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                Player.STATE_READY -> {
                    _duration.value = exoPlayer?.duration?.coerceAtLeast(0) ?: 0
                    updateTrackLists()
                    retryAttempt = 0
                    _retryStatus.value = null
                    
                    val format = exoPlayer?.videoFormat
                    Log.d(TAG, "READY details: Format: $format")
                    
                    val domainFormat = VideoFormat(
                        width = format?.width ?: 0,
                        height = format?.height ?: 0,
                        frameRate = format?.frameRate ?: 0f,
                        bitrate = format?.bitrate ?: 0,
                        codec = format?.sampleMimeType
                    )
                    
                    _videoFormat.value = domainFormat
                    _playerStats.value = _playerStats.value.copy(
                        width = domainFormat.width,
                        height = domainFormat.height,
                        videoCodec = domainFormat.codec ?: "Unknown",
                        videoDecoderName = "Internal Decoder"
                    )

                    // 4K OPTIMIZATION: Check for promotion on READY too (bitrate discovery)
                    promoteBufferIfNeeded(domainFormat)

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
                startPlaybackWatchdog()
            } else {
                positionUpdateJob?.cancel()
                playbackWatchdogJob?.cancel()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val aspect = if (videoSize.height > 0) videoSize.width.toFloat() / videoSize.height * videoSize.pixelWidthHeightRatio else 0f
            Log.d("PlayerBinding", "onVideoSizeChanged: ${videoSize.width}x${videoSize.height} | aspect=$aspect | pixelRatio=${videoSize.pixelWidthHeightRatio}")
            
            // PRESERVE BITRATE AND CODEC IF KNOWN
            val current = _videoFormat.value
            val newFormat = VideoFormat(
                width = videoSize.width,
                height = videoSize.height,
                frameRate = current.frameRate,
                bitrate = current.bitrate,
                codec = current.codec
            )
            _videoFormat.value = newFormat
            _playerStats.value = _playerStats.value.copy(width = videoSize.width, height = videoSize.height)

            // 4K OPTIMIZATION: Check if we need to promote the buffer based on newly discovered resolution
            promoteBufferIfNeeded(newFormat)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}", error)
            _error.tryEmit(PlayerError.fromException(error))
            _playerStats.value = _playerStats.value.copy(videoCodec = "ERROR: ${error.errorCodeName}")
            handleRetry()
        }

        override fun onTracksChanged(tracks: Tracks) { 
            Log.d(TAG, "onTracksChanged: ${tracks.groups.size} groups")
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        Log.d(TAG, "  Video Track $i: ${group.getTrackFormat(i)}")
                    }
                }
            }
            updateTrackLists() 
        }

        override fun onRenderedFirstFrame() {
            Log.d(TAG, "onRenderedFirstFrame: First frame has been rendered to the surface")
        }
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

    private fun startPlaybackWatchdog() {
        playbackWatchdogJob?.cancel()
        playbackWatchdogJob = scope.launch {
            lastRenderedCount = -1
            lastRenderedTime = System.currentTimeMillis()
            var isWaitingForFirstFrame = true
            
            while (isActive) {
                val player = exoPlayer
                if (player != null && !isRecoveringFromStall) {
                    val renderedCount = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
                    
                    if (renderedCount > 0) isWaitingForFirstFrame = false

                    if (player.isPlaying && _playbackState.value == PlaybackState.READY) {
                         if (renderedCount == lastRenderedCount) {
                             val stallDuration = System.currentTimeMillis() - lastRenderedTime
                             
                             // UHD is more sensitive to decoder hangs. 
                             // We also give more time for the very first frame.
                             val limit = if (isWaitingForFirstFrame) {
                                 10000L 
                             } else if (_videoFormat.value.width >= 3840) {
                                 2500L 
                             } else {
                                 4000L
                             }
                             
                             if (stallDuration > limit) {
                                 Log.e("DecoderStallRecovery", "STALL DETECTED! Rendered stuck at $renderedCount (FirstFrame=$isWaitingForFirstFrame). Resetting.")
                                 triggerStallRecovery()
                             }
                         } else {
                             lastRenderedCount = renderedCount
                             lastRenderedTime = System.currentTimeMillis()
                         }
                    } else {
                        lastRenderedTime = System.currentTimeMillis()
                    }
                }
                delay(1500)
            }
        }
    }

    private fun triggerStallRecovery() {
        val streamInfo = currentStreamInfo ?: return
        val bufferPolicy = currentBufferPolicy ?: return
        val position = exoPlayer?.currentPosition ?: 0L
        val lastView = boundPlayerView
        
        scope.launch {
            isRecoveringFromStall = true
            try {
                Log.i("DecoderStallRecovery", "Restarting player for stream: ${streamInfo.name}")
                Log.d("DecoderStallRecovery", "  - Reusing view identity: ${lastView?.hashCode()}")
                
                // initializePlayer will release old player and create new one
                initializePlayer(bufferPolicy)
                
                // CRITICAL: Explicitly re-bind the existing view to the new player instance
                lastView?.let { view ->
                    val player = exoPlayer
                    if (player != null) {
                        Log.d("DecoderStallRecovery", "  - Re-attaching new player to view ${view.hashCode()}")
                        view.player = player
                        boundPlayerView = view
                    }
                }

                // Re-prepare the new player
                prepare(streamInfo)
                if (streamInfo.streamType != StreamType.MPEG_TS) {
                    exoPlayer?.seekTo(position)
                }
                exoPlayer?.play()
                Log.i("DecoderStallRecovery", "Restart complete successfully.")
            } catch (e: Exception) {
                Log.e("DecoderStallRecovery", "FATAL RECOVERY FAILURE: Hard reset failed to rebuild player", e)
            } finally {
                isRecoveringFromStall = false
                lastRenderedTime = System.currentTimeMillis()
            }
        }
    }

    private fun promoteBufferIfNeeded(observedFormat: VideoFormat) {
        val streamInfo = currentStreamInfo ?: return
        val resolvedType = StreamTypeResolver.resolve(streamInfo)
        val nextPolicy = PlaybackBufferPolicies.forPlayback(resolvedType, observedFormat, streamInfo)

        if (currentBufferPolicy?.label != nextPolicy.label) {
            Log.i(TAG, "PROMOTING BUFFER: ${currentBufferPolicy?.label} -> ${nextPolicy.label} (Found ${observedFormat.width}x${observedFormat.height})")
            
            // Re-apply policy to existing load control isn't directly possible in ExoPlayer 
            // without a full re-preparation.
            val position = exoPlayer?.currentPosition ?: 0L
            val isPlaying = exoPlayer?.isPlaying == true
            
            // We use initializePlayer which will recreate the exoPlayer instance with the NEW buffer policy.
            initializePlayer(nextPolicy)
            prepare(streamInfo)
            
            if (resolvedType != ResolvedStreamType.MPEG_TS_LIVE) {
                exoPlayer?.seekTo(position)
            }
            if (isPlaying) exoPlayer?.play()
        }
    }
}
