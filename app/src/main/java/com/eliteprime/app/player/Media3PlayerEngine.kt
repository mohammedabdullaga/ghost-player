package com.eliteprime.app.player

import android.content.Context
import android.graphics.Color
import android.media.MediaFormat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.os.Build
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.eliteprime.app.R
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.StreamType
import com.eliteprime.app.domain.model.VideoFormat
import com.eliteprime.app.player.playback.*
import com.eliteprime.app.player.timeshift.LiveTimeshiftState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class, ExperimentalApi::class)
class Media3PlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val preferenceRepository: PreferenceRepository,
    private val globalPlayerState: GlobalPlayerState
) : PlayerEngine {

    private val TAG = "Media3PlayerEngine"
    private var exoPlayer: ExoPlayer? = null
    private var currentTrackSelector: DefaultTrackSelector? = null
    private var boundPlayerView: PlayerView? = null
    private var pendingRenderView: PlayerView? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var positionUpdateJob: Job? = null
    private var currentBufferPolicy: PlaybackBufferPolicy? = null
    private var mediaSourceFactory: PlayerMediaSourceFactory? = null
    private val subtitleStyleController = SubtitleStyleController()
    private var prepareStartTime: Long = 0
    private val stallDetector = VideoStallDetector()
    private var playbackWatchdogJob: Job? = null
    private var isRecoveringFromStall: Boolean = false
    private var forceNextCodecReset: Boolean = false
    private var hasRenderedFirstFrame: Boolean = false
    private var playbackStarted: Boolean = false
    private var videoStallRecoveryAttempt: Int = 0
    
    // Engine State for Recovery
    private var activeStreamType: ResolvedStreamType = ResolvedStreamType.UNKNOWN
    private var activeStreamInfo: StreamInfo? = null

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

    init {
        startWatchdog()
    }

    private fun initializePlayer(bufferPolicy: PlaybackBufferPolicy) {
        val streamInfo = activeStreamInfo ?: return
        val timeoutProfile = PlayerTimeoutProfile.resolve(streamInfo, activeStreamType)
        
        // MEDIA3 1.11.0: PROVIDE HIGHER INITIAL BITRATE FOR UHD TO AVOID STARTUP FLICKER
        val initialBitrate = if (bufferPolicy.label.contains("uhd")) 15_000_000L else 2_000_000L
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateSupplier { initialBitrate }
            .build()

        val perStreamClient = okHttpClient.newBuilder()
            .connectTimeout(timeoutProfile.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutProfile.readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutProfile.writeTimeoutMs, TimeUnit.MILLISECONDS)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val isTs = url.contains(".ts") || url.contains("ext=ts")
                val response = chain.proceed(request)
                if (isTs && response.body != null) {
                    val body = response.body!!
                    val progressBody = object : ResponseBody() {
                        private var totalRead = 0L
                        private var lastLog = 0L
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun source(): BufferedSource = (object : ForwardingSource(body.source()) {
                            override fun read(sink: Buffer, byteCount: Long): Long {
                                val read = super.read(sink, byteCount)
                                if (read != -1L) {
                                    totalRead += read
                                    val now = System.currentTimeMillis()
                                    if (now - lastLog > 5000) {
                                        Log.d("NetworkWatchdog", "TS Progress: ${totalRead / 1024} KB")
                                        lastLog = now
                                    }
                                }
                                return read
                            }
                        } as Source).buffer()
                    }
                    response.newBuilder().body(progressBody).build()
                } else response
            }
            .build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(perStreamClient)
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
            .setBackBuffer(0, false) // STABILITY FIX: DISABLE BACK BUFFER FOR 4K RAM
            .build()

        val decoderMode = preferenceRepository.decoderMode.value
        val codecSelector = CapabilityAwareMediaCodecSelector(context, decoderMode)
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
                out.add(object : MediaCodecVideoRenderer(
                    context, mediaCodecSelector, allowedVideoJoiningTimeMs,
                    enableDecoderFallback, eventHandler, eventListener, 50
                ) {
                    override fun canReuseCodec(
                        codecInfo: MediaCodecInfo, oldFormat: Format, newFormat: Format, isTunnelingEnabled: Boolean
                    ): DecoderReuseEvaluation {
                        var eval = super.canReuseCodec(codecInfo, oldFormat, newFormat, isTunnelingEnabled)
                        val isAmlogic = Build.HARDWARE.contains("amlogic", true) || Build.BOARD.contains("amlogic", true)
                        
                        // AGGRESSIVE: Reset for any UHD involving change
                        val isUhd = newFormat.width >= 1920 || newFormat.height >= 1080
                        
                        if (isAmlogic && (isUhd || forceNextCodecReset) && eval.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                            Log.w(TAG, "Amlogic Stability: Forcing clean decoder")
                            forceNextCodecReset = false
                            eval = DecoderReuseEvaluation(
                                codecInfo.name, oldFormat, newFormat,
                                DecoderReuseEvaluation.REUSE_RESULT_NO,
                                eval.discardReasons or DecoderReuseEvaluation.DISCARD_REASON_VIDEO_RESOLUTION_CHANGED
                            )
                        }
                        return eval
                    }
                })
            }
        }.setMediaCodecSelector(codecSelector)
         .setEnableDecoderFallback(true)
         .setExtensionRendererMode(if (decoderMode == "SOFTWARE") 
             DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER 
             else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        // Disable audio track params for Xiaomi sync issues
        renderersFactory.setEnableAudioTrackPlaybackParams(false)

        val playerToRelease = exoPlayer
        exoPlayer = null
        playerToRelease?.let { old ->
            try { old.stop() } catch (e: Exception) {}
            scope.launch { try { old.release() } catch (e: Exception) {} }
        }

        val speedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(0.95f)
            .setFallbackMaxPlaybackSpeed(1.05f)
            .build()

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(newTrackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setLivePlaybackSpeedControl(speedControl)
            .enablePerStreamMediaProgression(true)
            .build()
            .apply {
                addListener(PlayerListener())
                playWhenReady = true
                setVideoFrameMetadataListener { presentationTimeUs: Long, _: Long, _: Format, _: MediaFormat? ->
                    stallDetector.onVideoFrameRendered(presentationTimeUs / 1000)
                }
                
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setViewportSizeToPhysicalDisplaySize(context, true)
                    .build()
                
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }

        val view = boundPlayerView ?: pendingRenderView
        view?.let { 
            it.player = exoPlayer
            boundPlayerView = it
            pendingRenderView = null
        }
        currentBufferPolicy = bufferPolicy
        hasRenderedFirstFrame = false
        playbackStarted = false
        videoStallRecoveryAttempt = 0
        stallDetector.reset()
    }

    override fun prepare(streamInfo: StreamInfo) {
        prepareStartTime = System.currentTimeMillis()
        activeStreamInfo = streamInfo
        val plan = buildPlaybackPreparationPlan(streamInfo) { _playbackState.value == PlaybackState.READY }
        activeStreamType = plan.resolvedStreamType
        
        val bufferPolicy = PlaybackBufferPolicies.forPlayback(activeStreamType, _videoFormat.value, streamInfo)
        if (exoPlayer == null || currentBufferPolicy?.label != bufferPolicy.label) {
            initializePlayer(bufferPolicy)
        }

        _prepareStatus.value = streamInfo
        Log.i(TAG, "Prepare: ${streamInfo.url} | Resolved: $activeStreamType | Policy: ${bufferPolicy.label}")

        val mediaSource = mediaSourceFactory?.create(streamInfo, activeStreamType, plan.retryPolicy) ?: return
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
    }

    override fun play() { exoPlayer?.play() }
    override fun pause() { exoPlayer?.pause() }
    override fun stop() {
        exoPlayer?.stop()
        _playbackState.value = PlaybackState.IDLE
        _isPlaying.value = false
        playbackStarted = false
        positionUpdateJob?.cancel()
    }

    override fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    override fun setVolume(volume: Float) { exoPlayer?.volume = volume }
    override fun setMuted(muted: Boolean) { exoPlayer?.volume = if (muted) 0f else 1f }
    override fun toggleMute() { setMuted(exoPlayer?.volume == 0f) }
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

    private fun selectTrack(type: Int, id: String) {
        val player = exoPlayer ?: return
        val selector = currentTrackSelector ?: return
        for (group in player.currentTracks.groups) {
            if (group.type == type) {
                for (i in 0 until group.length) {
                    if (group.getTrackFormat(i).id == id) {
                        selector.parameters = selector.buildUponParameters()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
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
        activeStreamInfo = streamInfo
        _prepareStatus.value = streamInfo
        val item = MediaItem.Builder().setUri(streamInfo.url).build()
        exoPlayer?.setMediaItem(item, false)
    }

    override fun release() {
        playbackWatchdogJob?.cancel()
        positionUpdateJob?.cancel()
        scope.cancel()
        exoPlayer?.release()
    }

    override fun createRenderView(context: Context, resizeMode: PlayerSurfaceResizeMode, surfaceType: PlayerRenderSurfaceType): View {
        val layout = if (surfaceType == PlayerRenderSurfaceType.TEXTURE_VIEW) R.layout.player_texture else R.layout.player_surface
        val view = LayoutInflater.from(context).inflate(layout, null) as PlayerView
        return view.apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
            isFocusable = false
            this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        val playerView = renderView as? PlayerView ?: return
        if (exoPlayer == null) {
            pendingRenderView = playerView
            return
        }
        if (boundPlayerView !== playerView) {
            val isAmlogic = Build.HARDWARE.contains("amlogic", true)
            if (isAmlogic && activeStreamInfo?.url?.contains("hevc", true) == true) {
                forceNextCodecReset = true
            }
            PlayerView.switchTargetView(exoPlayer!!, boundPlayerView, playerView)
            boundPlayerView = playerView
        }
        subtitleStyleController.apply(playerView)
    }

    override fun unbindRenderView(renderView: View) {
        if (pendingRenderView === renderView) pendingRenderView = null
        if (boundPlayerView === renderView) {
            (renderView as PlayerView).player = null
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
            _playbackState.value = when (state) {
                Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                Player.STATE_READY -> {
                    _duration.value = exoPlayer?.duration?.coerceAtLeast(0) ?: 0
                    updateTracks()
                    val f = exoPlayer?.videoFormat
                    val domainF = VideoFormat(f?.width ?: 0, f?.height ?: 0, f?.frameRate ?: 0f, f?.bitrate ?: 0, f?.sampleMimeType)
                    _videoFormat.value = domainF
                    _playerStats.value = _playerStats.value.copy(width = domainF.width, height = domainF.height, videoCodec = domainF.codec ?: "")
                    
                    globalPlayerState.updateSurfaceTypeForResolution(domainF.width, domainF.height)
                    promoteBufferIfNeeded(domainF)
                    
                    if (_isPlaying.value) {
                        playbackStarted = true
                        videoStallRecoveryAttempt = 0
                    }
                    
                    PlaybackState.READY
                }
                Player.STATE_ENDED -> PlaybackState.ENDED
                else -> PlaybackState.IDLE
            }
        }
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) {
                startPositionUpdates()
                if (_playbackState.value == PlaybackState.READY) {
                    playbackStarted = true
                    videoStallRecoveryAttempt = 0
                }
            } else {
                positionUpdateJob?.cancel()
            }
        }
        override fun onVideoSizeChanged(size: VideoSize) {
            val current = _videoFormat.value
            val newF = VideoFormat(size.width, size.height, current.frameRate, current.bitrate, current.codec)
            _videoFormat.value = newF
            promoteBufferIfNeeded(newF)
        }
        override fun onPlayerError(e: PlaybackException) {
            Log.e(TAG, "Error: ${e.message}", e)
            _error.tryEmit(PlayerError.fromException(e))
        }
        override fun onRenderedFirstFrame() {
            hasRenderedFirstFrame = true
            playbackStarted = true
            videoStallRecoveryAttempt = 0
        }
    }

    private fun updateTracks() {
        val tracks = exoPlayer?.currentTracks ?: return
        _availableAudioTracks.value = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g ->
            (0 until g.length).map { i ->
                val f = g.getTrackFormat(i)
                PlayerTrack(f.id ?: i.toString(), f.label ?: f.language ?: "Audio ${i+1}", f.language, g.isTrackSelected(i))
            }
        }
        _availableSubtitleTracks.value = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { g ->
            (0 until g.length).map { i ->
                val f = g.getTrackFormat(i)
                PlayerTrack(f.id ?: i.toString(), f.label ?: f.language ?: "Subtitle ${i+1}", f.language, g.isTrackSelected(i))
            }
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                _currentPosition.value = exoPlayer?.currentPosition ?: 0
                _playerStats.value = _playerStats.value.copy(bufferedDurationMs = (exoPlayer?.bufferedPosition ?: 0) - _currentPosition.value)
                delay(1000)
            }
        }
    }

    private fun startWatchdog() {
        playbackWatchdogJob?.cancel()
        playbackWatchdogJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && !isRecoveringFromStall) {
                    val stalled = stallDetector.shouldReportStall(
                        playbackState = _playbackState.value,
                        isPlaying = _isPlaying.value,
                        playbackStarted = playbackStarted,
                        currentPositionMs = _currentPosition.value,
                        bufferedDurationMs = _playerStats.value.bufferedDurationMs,
                        playWhenReady = player.playWhenReady,
                        recoverBufferingStalls = activeStreamType.isLive,
                        recoverReadyStalls = shouldRecoverReadyStalls(activeStreamType),
                        recoverPositionAdvancingReadyStalls = shouldRecoverPositionAdvancingReadyStalls(activeStreamType),
                        recoverFrameSilentReadyStalls = shouldRecoverFrameSilentReadyStalls(activeStreamType)
                    )
                    
                    if (shouldFallbackTextureView(player)) {
                        Log.w(TAG, "TextureView fallback triggered. Switching to SURFACE_VIEW.")
                        globalPlayerState.setSurfaceType(PlayerRenderSurfaceType.SURFACE_VIEW)
                    }

                    if (stalled) {
                        Log.e(TAG, "STALL! Triggering recovery.")
                        handleStall()
                    }
                }
                delay(2000)
            }
        }
    }
    
    private fun shouldFallbackTextureView(player: ExoPlayer): Boolean {
        if (_renderSurfaceType.value != PlayerRenderSurfaceType.TEXTURE_VIEW) return false
        if (hasRenderedFirstFrame) return false
        val elapsed = System.currentTimeMillis() - prepareStartTime
        if (elapsed < 9000L) return false
        
        val state = _playbackState.value
        if (state == PlaybackState.READY || (state == PlaybackState.BUFFERING && _playerStats.value.bufferedDurationMs > 4000L)) {
            return true
        }
        return false
    }

    private fun handleStall() {
        val info = activeStreamInfo ?: return
        videoStallRecoveryAttempt++
        
        if (shouldReconnectLiveStall(_playbackState.value, activeStreamType, videoStallRecoveryAttempt)) {
            Log.w(TAG, "Reconnecting live stall, attempt $videoStallRecoveryAttempt")
            prepare(info)
            return
        }
        
        Log.e(TAG, "Hard hardware reset for stall, attempt $videoStallRecoveryAttempt")
        recover()
    }

    private fun recover() {
        val info = activeStreamInfo ?: return
        val pos = exoPlayer?.currentPosition ?: 0L
        val view = boundPlayerView
        scope.launch {
            isRecoveringFromStall = true
            try {
                initializePlayer(currentBufferPolicy!!)
                view?.let { it.player = exoPlayer; boundPlayerView = it }
                prepare(info)
                if (activeStreamType != ResolvedStreamType.MPEG_TS_LIVE) exoPlayer?.seekTo(pos)
                exoPlayer?.play()
            } finally {
                isRecoveringFromStall = false
                stallDetector.reset()
            }
        }
    }

    private fun promoteBufferIfNeeded(observed: VideoFormat) {
        val info = activeStreamInfo ?: return
        val next = PlaybackBufferPolicies.forPlayback(activeStreamType, observed, info)
        if (currentBufferPolicy?.label != next.label) {
            Log.i(TAG, "PROMOTING: ${currentBufferPolicy?.label} -> ${next.label} (${next.qualityReason})")
            val pos = exoPlayer?.currentPosition ?: 0L
            val playing = exoPlayer?.isPlaying == true
            
            // For 4K streams, we try to AVOID player recreation if already playing 
            // unless the buffer gap is dangerous.
            if (observed.width >= 3840 && playing) {
                Log.w(TAG, "Skipping mid-stream recreation for 4K to prevent freeze")
                currentBufferPolicy = next // SILENT PROMOTE (won't take effect until next prepare)
                return
            }
            
            initializePlayer(next)
            prepare(info)
            if (activeStreamType != ResolvedStreamType.MPEG_TS_LIVE) exoPlayer?.seekTo(pos)
            if (playing) exoPlayer?.play()
        }
    }
}
