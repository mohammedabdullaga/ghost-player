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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.eliteprime.app.R
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.data.repository.XtreamRepository
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
import java.util.concurrent.atomic.AtomicInteger
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class, ExperimentalApi::class)
class Media3PlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val preferenceRepository: PreferenceRepository,
    private val globalPlayerState: GlobalPlayerState,
    private val xtreamRepository: XtreamRepository
) : PlayerEngine {

    private val TAG = "Media3PlayerEngine"
    private var exoPlayer: ExoPlayer? = null
    private var currentTrackSelector: DefaultTrackSelector? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
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
    
    private var framesRenderedSinceLastStats = AtomicInteger(0)
    private var lastStatsTimestamp = 0L
    
    override val engineType: StateFlow<PlayerType> = MutableStateFlow(PlayerType.EXO).asStateFlow()

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
        val meter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateSupplier { initialBitrate }
            .build()
        this.bandwidthMeter = meter

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
            .setTransferListener(meter)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            .setTransferListener(meter)
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
        // FIX: DO NOT artificially prefer AAC — IPTV streams commonly use MP2, AC3, E-AC3, or Opus.
        // Forcing AAC on TCL/Haier (MediaTek) TVs causes silent audio when the HW AAC decoder
        // rejects the profile or when there is no AAC track at all.
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

            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                // Enable float output for all devices to support FFmpeg PCM float output & wide audio compatibility
                return super.buildAudioSink(context, true, enableAudioTrackPlaybackParams)
            }
        }.setMediaCodecSelector(codecSelector)
         .setEnableDecoderFallback(true)
         .setExtensionRendererMode(if (decoderMode == "SOFTWARE") 
             DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER 
             else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        // FIX: Ensure AudioTrack playback params and clock sync are fully enabled for real-time live TS streams
        renderersFactory.setEnableAudioTrackPlaybackParams(true)

        val playerToRelease = exoPlayer
        exoPlayer = null
        playerToRelease?.let { old ->
            Log.d(TAG, "Synchronously releasing old player instance")
            try { 
                // CRITICAL: Unbind from view before release to prevent native crash
                boundPlayerView?.player = null
                old.stop() 
                old.release() // CRITICAL: Release synchronously to free AudioTrack slots
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing old player", e)
            }
        }

        // Live speed control without artificial slow down
        val speedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(1.0f)
            .setFallbackMaxPlaybackSpeed(1.05f)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(newTrackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(meter)
            .setLivePlaybackSpeedControl(speedControl)
            .setAudioAttributes(audioAttributes, true) // FIX: Properly handle audio focus and device volume
            .build()
            .apply {
                addListener(PlayerListener())
                addAnalyticsListener(AnalyticsListenerImpl())
                playWhenReady = true
                
                setVideoFrameMetadataListener { presentationTimeUs: Long, _: Long, _: Format, _: MediaFormat? ->
                    stallDetector.onVideoFrameRendered(presentationTimeUs / 1000)
                    framesRenderedSinceLastStats.incrementAndGet()
                }
                
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("ara", "ar", "eng", "en")
                    .setPreferredTextLanguages("ara", "ar", "eng", "en")
                    .setViewportSizeToPhysicalDisplaySize(context, true)
                    .build()
                
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
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
        stallDetector.reset(0)
    }

    override fun prepare(streamInfo: StreamInfo) {
        prepareStartTime = System.currentTimeMillis()
        activeStreamInfo = streamInfo
        val plan = buildPlaybackPreparationPlan(streamInfo) { _playbackState.value == PlaybackState.READY }
        activeStreamType = plan.resolvedStreamType
        
        val isLite = preferenceRepository.isLiteModeEnabled.value
        val bufferPolicy = PlaybackBufferPolicies.forPlayback(activeStreamType, _videoFormat.value, streamInfo, isLite)
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

    override fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo: $positionMs")
        stallDetector.reset(positionMs)
        _currentPosition.value = positionMs
        exoPlayer?.seekTo(positionMs)
    }
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

    override fun setAudioDelay(delayMs: Long) {
        // Media3 audio stream offset adjustment requires custom renderers 
        // to apply delays dynamically at the sink level.
    }

    override fun setSubtitleDelay(delayMs: Long) {
        // Media3 subtitle stream offset adjustment is not natively supported 
        // in standard TextRenderer for dynamic runtime changes.
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
        try {
            boundPlayerView?.player = null
            exoPlayer?.stop()
            exoPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing player", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    override fun createRenderView(context: Context, resizeMode: PlayerSurfaceResizeMode, surfaceType: PlayerRenderSurfaceType): View {
        val layout = if (surfaceType == PlayerRenderSurfaceType.TEXTURE_VIEW) R.layout.player_texture else R.layout.player_surface
        val view = LayoutInflater.from(context).inflate(layout, null) as PlayerView
        return view.apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
            isFocusable = false
            this.resizeMode = when (resizeMode) {
                PlayerSurfaceResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                PlayerSurfaceResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                PlayerSurfaceResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                PlayerSurfaceResizeMode.MATCH_PARENT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        }
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        val playerView = renderView as? PlayerView ?: return
        
        // Dynamically apply resize mode according to user selection
        playerView.resizeMode = when (resizeMode) {
            PlayerSurfaceResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerSurfaceResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerSurfaceResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            PlayerSurfaceResizeMode.MATCH_PARENT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

        // Sync decoder-level scaling mode with the view resize mode.
        // Without this, videoScalingMode stays stuck at SCALE_TO_FIT and
        // FILL/ZOOM on the PlayerView has no visible effect.
        // NOTE: FILL must use SCALE_TO_FIT at the decoder so the view handles
        // stretching; using SCALE_TO_FIT_WITH_CROPPING would crop before stretch.
        exoPlayer?.videoScalingMode = when (resizeMode) {
            PlayerSurfaceResizeMode.FIT -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            PlayerSurfaceResizeMode.FILL -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            PlayerSurfaceResizeMode.ZOOM -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            PlayerSurfaceResizeMode.MATCH_PARENT -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }

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
                    val domainF = VideoFormat(
                        f?.width ?: 0, 
                        f?.height ?: 0, 
                        f?.frameRate?.takeIf { it > 0 } ?: 0f, 
                        f?.bitrate?.takeIf { it > 0 } ?: 0, 
                        f?.sampleMimeType
                    )
                    _videoFormat.value = domainF
                    _playerStats.value = _playerStats.value.copy(
                        width = domainF.width, 
                        height = domainF.height, 
                        videoCodec = domainF.codec ?: "",
                        videoBitrate = if (domainF.bitrate > 0) domainF.bitrate else _playerStats.value.videoBitrate
                    )
                    
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
        override fun onTracksChanged(tracks: Tracks) {
            updateTracks()
        }
        override fun onPlayerError(e: PlaybackException) {
            Log.e(TAG, "Error: ${e.message}", e)
            
            val chain = generateSequence<Throwable>(e) { it.cause }.toList()
            val rootCause = chain.lastOrNull()
            
            if (rootCause?.message?.contains("Player stuck", ignoreCase = true) == true) {
                Log.w(TAG, "Media3 detected STUCK player (root cause). Triggering automatic recovery.")
                handleStall()
                return
            }
            
            if (chain.any { it.message?.contains("AudioTrack init failed", ignoreCase = true) == true || 
                          it.message?.contains("Audio sink error", ignoreCase = true) == true ||
                          it.message?.contains("Cannot create AudioTrack", ignoreCase = true) == true }) {
                Log.e(TAG, "CRITICAL: Audio sink initialization failed. Triggering hardware reset recovery.")
                handleStall()
                return
            }

            // FALLBACK HOST RETRY: If the stream source fails for ANY reason
            // (HTTP error, DNS failure, timeout, connection refused, etc.),
            // build fallback URLs from all active hosts and try the next one.
            val errMsgs = chain.mapNotNull { it::class.simpleName + ": " + (it.message ?: "") }
            val hasSourceError = chain.any { cause ->
                cause is HttpDataSource.HttpDataSourceException ||
                cause is UnknownHostException ||
                cause is SocketTimeoutException ||
                cause is ConnectException ||
                cause is SocketException ||
                errMsgs.any { msg ->
                    msg.contains("HttpDataSourceException") ||
                    msg.contains("InvalidResponseCodeException") ||
                    msg.contains("Response code: 5") ||
                    msg.contains("Response code: 4")
                }
            }
            val fallbacks = activeStreamInfo?.fallbackUrls
            Log.w(TAG, "onPlayerError: hasSourceError=$hasSourceError fallbackCount=${fallbacks?.size ?: 0} errors=$errMsgs")
            if (hasSourceError) {
                // Build new fallback list from all active hosts if the current one is empty
                var effectiveFallbacks = fallbacks
                if (effectiveFallbacks.isNullOrEmpty()) {
                    val currentUrl = activeStreamInfo?.url ?: ""
                    val allHostUrls = xtreamRepository.getActiveHostUrls()
                    Log.w(TAG, "onPlayerError: Building fallbacks from ${allHostUrls.size} hosts. Current URL=$currentUrl. Hosts=$allHostUrls")
                    effectiveFallbacks = allHostUrls.mapNotNull { hostUrl ->
                        currentUrl.replace(Regex("https?://[^/]+"), hostUrl.trimEnd('/'))
                            .takeIf { it != currentUrl }
                    }
                    Log.w(TAG, "onPlayerError: Generated ${effectiveFallbacks.size} fallback URLs: $effectiveFallbacks")
                }
                if (!effectiveFallbacks.isNullOrEmpty()) {
                    val nextUrl = effectiveFallbacks.first()
                    Log.w(TAG, "Falling back to next host: $nextUrl (${effectiveFallbacks.size - 1} remaining)")
                    val updatedStream = activeStreamInfo!!.copy(
                        url = nextUrl,
                        fallbackUrls = effectiveFallbacks.drop(1)
                    )
                    activeStreamInfo = updatedStream
                    renewStreamUrl(updatedStream)
                    return
                }
            }

            FirebaseCrashlytics.getInstance().recordException(e)
            _error.tryEmit(PlayerError.fromException(e))
        }
        override fun onRenderedFirstFrame() {
            hasRenderedFirstFrame = true
            playbackStarted = true
            videoStallRecoveryAttempt = 0
        }
    }

    private inner class AnalyticsListenerImpl : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            val current = _videoFormat.value
            Log.d(TAG, "Format changed: ${format.width}x${format.height} @ ${format.frameRate} fps, bitrate=${format.bitrate}")
            _videoFormat.value = VideoFormat(
                width = format.width.takeIf { it > 0 } ?: current.width,
                height = format.height.takeIf { it > 0 } ?: current.height,
                frameRate = format.frameRate.takeIf { it > 0 } ?: current.frameRate,
                bitrate = format.bitrate.takeIf { it > 0 } ?: format.peakBitrate.takeIf { it > 0 } ?: current.bitrate,
                codec = format.sampleMimeType ?: current.codec
            )
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            val codec = format.sampleMimeType ?: format.codecs ?: "Unknown"
            val decoderName = format.sampleMimeType ?: format.codecs ?: "Unknown"
            val sampleRate = format.sampleRate
            val channelCount = format.channelCount
            Log.d(TAG, "Audio format changed: codec=$codec decoder=$decoderName sampleRate=$sampleRate channels=$channelCount")
            _playerStats.value = _playerStats.value.copy(
                audioCodec = codec,
                audioDecoderName = decoderName
            )
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
                val player = exoPlayer
                if (player != null) {
                    val currentPos = player.currentPosition
                    _currentPosition.value = currentPos
                    val buffered = player.bufferedPosition - currentPos
                    
                    // Periodic Format Refresh for LIVE streams
                    val f = player.videoFormat
                    if (f != null) {
                        val currentF = _videoFormat.value
                        if (f.width != currentF.width || f.height != currentF.height || 
                            (f.frameRate > 0 && f.frameRate != currentF.frameRate) || 
                            (f.bitrate > 0 && f.bitrate != currentF.bitrate)) {
                            
                            val domainF = VideoFormat(
                                f.width, f.height, 
                                f.frameRate.takeIf { it > 0 } ?: currentF.frameRate,
                                f.bitrate.takeIf { it > 0 } ?: currentF.bitrate,
                                f.sampleMimeType
                            )
                            _videoFormat.value = domainF
                        }
                    }

                    val currentMeterBitrate = bandwidthMeter?.bitrateEstimate?.toInt() ?: 0
                    val playerBitrate = player.videoFormat?.bitrate ?: 0
                    
                    val now = System.currentTimeMillis()
                    if (lastStatsTimestamp > 0) {
                        val duration = now - lastStatsTimestamp
                        if (duration >= 1000) {
                            val frames = framesRenderedSinceLastStats.getAndSet(0)
                            val fps = (frames * 1000f / duration)
                            Log.d(TAG, "Measured FPS: $fps (Frames in ${duration}ms: $frames)")
                            if (fps > 0) {
                                val currentF = _videoFormat.value
                                _videoFormat.value = currentF.copy(frameRate = fps)
                            }
                            lastStatsTimestamp = now
                        }
                    } else {
                        lastStatsTimestamp = now
                    }

                    _playerStats.value = _playerStats.value.copy(
                        bufferedDurationMs = buffered,
                        videoBitrate = if (currentMeterBitrate > 100_000) currentMeterBitrate else if (playerBitrate > 0) playerBitrate else _playerStats.value.videoBitrate
                    )
                }
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
                    val currentPos = player.currentPosition
                    val stalled = stallDetector.shouldReportStall(
                        playbackState = _playbackState.value,
                        isPlaying = _isPlaying.value,
                        playbackStarted = playbackStarted,
                        currentPositionMs = currentPos,
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
        
        Log.e(TAG, "STALL DETECTED! State=${_playbackState.value} | Pos=${_currentPosition.value} | Buf=${_playerStats.value.bufferedDurationMs} | Attempt=$videoStallRecoveryAttempt")

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
        Log.w(TAG, "Initiating recovery at position: $pos")
        scope.launch {
            isRecoveringFromStall = true
            try {
                initializePlayer(currentBufferPolicy!!)
                view?.let { it.player = exoPlayer; boundPlayerView = it }
                prepare(info)
                if (activeStreamType != ResolvedStreamType.MPEG_TS_LIVE) seekTo(pos)
                exoPlayer?.play()
            } finally {
                isRecoveringFromStall = false
                stallDetector.reset(pos)
            }
        }
    }

    private fun promoteBufferIfNeeded(observed: VideoFormat) {
        val info = activeStreamInfo ?: return
        val isLite = preferenceRepository.isLiteModeEnabled.value
        val next = PlaybackBufferPolicies.forPlayback(activeStreamType, observed, info, isLite)
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
