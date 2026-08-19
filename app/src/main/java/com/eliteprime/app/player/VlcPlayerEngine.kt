package com.eliteprime.app.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import com.eliteprime.app.R
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.VideoFormat
import com.eliteprime.app.player.timeshift.LiveTimeshiftState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VlcPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerEngine {

    @Volatile private var libVLC: LibVLC? = null
    @Volatile private var mediaPlayer: MediaPlayer? = null
    @Volatile private var videoLayout: VLCVideoLayout? = null
    
    @Volatile private var pendingUrl: String? = null
    @Volatile private var isSurfaceAttached = false
    @Volatile private var attachedLayout: VLCVideoLayout? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val TAG = "VlcPlayerEngine"

    override val engineType: StateFlow<PlayerType> = MutableStateFlow(PlayerType.VLC).asStateFlow()

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
    override val error = _error

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

    override fun prepare(streamInfo: StreamInfo) {
        Log.d(TAG, "prepare: ${streamInfo.url}")
        _prepareStatus.value = streamInfo
        _playbackState.value = PlaybackState.BUFFERING
        
        pendingUrl = streamInfo.url
        ensureLibInitialized()

        val layout = videoLayout
        if (layout != null) {
            // Re-attach if the layout changed (surface swap) or was torn down
            // while a previous playback was hidden.
            if (attachedLayout !== layout) {
                if (isSurfaceAttached) detachVlcViews()
                attachVlcViews(layout)
            } else if (isSurfaceAttached) {
                loadPendingUrl()
            }
        } else {
            Log.d(TAG, "prepare: Postponing loadfile until Surface is attached")
        }
    }

    private fun ensureLibInitialized() {
        if (libVLC == null) {
            Log.d(TAG, "Initializing LibVLC Context")
            val args = arrayListOf(
                // FORCE VLC NATIVE MEDIACODEC HARDWARE MODULE OVER AVCODEC/FFMPEG
                "--codec=mediacodec_ndk,mediacodec_jni,all",
                "--network-caching=3000",
                "--live-caching=3000",
                "--file-caching=3000",
                "--clock-jitter=0",
                "--clock-synchro=0",
                "--drop-late-frames",
                "--skip-frames",
                "--avcodec-skiploopfilter=4",
                "--avcodec-threads=4",
                "--no-stats",
                "--no-osd"
            )
            libVLC = LibVLC(context, args)
            mediaPlayer = MediaPlayer(libVLC)
            mediaPlayer?.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0)
            
            mediaPlayer?.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Buffering -> {
                        if (event.buffering >= 100f) {
                             _playbackState.value = PlaybackState.READY
                        } else {
                             _playbackState.value = PlaybackState.BUFFERING
                        }
                    }
                    MediaPlayer.Event.Playing -> {
                        _playbackState.value = PlaybackState.READY
                        _isPlaying.value = true
                        _duration.value = mediaPlayer?.length ?: 0L
                        updateTracks()
                        updateVideoFormat()
                    }
                    MediaPlayer.Event.Paused -> _isPlaying.value = false
                    MediaPlayer.Event.Stopped -> {
                        _playbackState.value = PlaybackState.IDLE
                        _isPlaying.value = false
                    }
                    MediaPlayer.Event.EndReached -> _playbackState.value = PlaybackState.ENDED
                    MediaPlayer.Event.EncounteredError -> {
                        Log.e(TAG, "VLC Native Error encountered")
                        _playbackState.value = PlaybackState.ERROR
                        _error.tryEmit(PlayerError.UnknownError(context.getString(R.string.player_error_title)))
                    }
                    MediaPlayer.Event.PositionChanged -> {
                        _currentPosition.value = (event.positionChanged * (mediaPlayer?.length ?: 0)).toLong()
                    }
                    MediaPlayer.Event.LengthChanged -> {
                        _duration.value = event.lengthChanged
                    }
                }
            }
        }
    }

    private fun loadPendingUrl() {
        val url = pendingUrl ?: return
        Log.i(TAG, "Executing load for: $url")
        
        val media = Media(libVLC, Uri.parse(url))
        // Direct zero-copy MediaCodec hardware decoding on SurfaceView
        media.setHWDecoderEnabled(true, false)
        // Tune stream flags per media instance
        media.addOption(":clock-synchro=0")
        media.addOption(":network-caching=3000")
        media.addOption(":live-caching=3000")
        mediaPlayer?.media = media
        media.release()

        mediaPlayer?.play()
        pendingUrl = null
    }

    private fun updateTracks() {
        val audioTracks = mediaPlayer?.audioTracks
        _availableAudioTracks.value = audioTracks?.map { 
            PlayerTrack(it.id.toString(), it.name, isSelected = it.id == mediaPlayer?.audioTrack)
        } ?: emptyList()

        val spuTracks = mediaPlayer?.spuTracks
        _availableSubtitleTracks.value = spuTracks?.map { 
            PlayerTrack(it.id.toString(), it.name, isSelected = it.id == mediaPlayer?.spuTrack)
        } ?: emptyList()
    }

    private fun updateVideoFormat() {
        val track = mediaPlayer?.currentVideoTrack ?: return
        if (track.width <= 0 || track.height <= 0) return
        val fps = if (track.frameRateDen > 0) {
            track.frameRateNum.toFloat() / track.frameRateDen
        } else {
            0f
        }
        _videoFormat.value = VideoFormat(
            width = track.width,
            height = track.height,
            frameRate = fps,
            bitrate = track.bitrate,
            codec = track.codec
        )
    }

    override fun play() { mediaPlayer?.play() }
    override fun pause() { mediaPlayer?.pause() }
    override fun stop() {
        mediaPlayer?.stop()
        _playbackState.value = PlaybackState.IDLE
        _isPlaying.value = false
        pendingUrl = null
    }

    override fun seekTo(positionMs: Long) { mediaPlayer?.time = positionMs }
    override fun setVolume(volume: Float) { mediaPlayer?.volume = (volume * 100).toInt() }
    override fun setMuted(muted: Boolean) { 
        _isMuted.value = muted
        mediaPlayer?.volume = if (muted) 0 else 100 
    }
    override fun toggleMute() { setMuted(!_isMuted.value) }
    override fun setPlaybackSpeed(speed: Float) { 
        _playbackSpeed.value = speed
        mediaPlayer?.rate = speed 
    }
    
    override fun selectAudioTrack(trackId: String) { 
        mediaPlayer?.setAudioTrack(trackId.toInt())
        updateTracks()
    }
    override fun selectVideoTrack(trackId: String) { /* VLC doesn't easily support dynamic video track switching same way */ }
    override fun selectSubtitleTrack(trackId: String?) { 
        mediaPlayer?.setSpuTrack(trackId?.toInt() ?: -1)
        updateTracks()
    }
    override fun setSubtitleStyle(style: PlayerSubtitleStyle) { /* VLC uses its own styling system */ }
    override fun setAudioDelay(delayMs: Long) {
        // VLC audio delay is in microseconds
        mediaPlayer?.setAudioDelay(delayMs * 1000L)
    }
    override fun setSubtitleDelay(delayMs: Long) {
        // VLC subtitle delay is in microseconds
        mediaPlayer?.setSpuDelay(delayMs * 1000L)
    }
    override fun renewStreamUrl(streamInfo: StreamInfo) { prepare(streamInfo) }

    override fun release() {
        Log.d(TAG, "Releasing engine resources")
        mediaPlayer?.stop()
        detachVlcViews()
        mediaPlayer?.release()
        libVLC?.release()
        mediaPlayer = null
        libVLC = null
        videoLayout = null
        isSurfaceAttached = false
        attachedLayout = null
    }

    override fun createRenderView(context: Context, resizeMode: PlayerSurfaceResizeMode, surfaceType: PlayerRenderSurfaceType): View {
        Log.d(TAG, "createRenderView")
        val layout = VLCVideoLayout(context)
        videoLayout = layout
        attachVlcViews(layout)
        applyAspectRatio(resizeMode)
        return layout
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        val layout = renderView as? VLCVideoLayout ?: return
        videoLayout = layout
        attachVlcViews(layout)
        applyAspectRatio(resizeMode)
    }

    private fun applyAspectRatio(resizeMode: PlayerSurfaceResizeMode) {
        mediaPlayer?.let { player ->
            when (resizeMode) {
                PlayerSurfaceResizeMode.FIT -> {
                    player.aspectRatio = null
                    player.scale = 0f
                }
                PlayerSurfaceResizeMode.FILL -> {
                    player.aspectRatio = "16:9"
                    player.scale = 0f
                }
                PlayerSurfaceResizeMode.ZOOM -> {
                    player.aspectRatio = null
                    player.scale = 1.25f
                }
            }
        }
    }

    override fun unbindRenderView(renderView: View) {
        Log.d(TAG, "unbindRenderView")
        detachVlcViews()
        if (videoLayout === renderView) videoLayout = null
    }

    override fun clearRenderBinding() {
        Log.d(TAG, "clearRenderBinding")
        detachVlcViews()
        videoLayout = null
    }

    private fun attachVlcViews(layout: VLCVideoLayout) {
        if (attachedLayout === layout && isSurfaceAttached) return
        try {
            ensureLibInitialized()
            Log.d(TAG, "Attaching views to MediaPlayer")
            mediaPlayer?.attachViews(layout, null, true, false)
            attachedLayout = layout
            isSurfaceAttached = true
            if (pendingUrl != null) {
                loadPendingUrl()
            }
        } catch (e: Exception) {
            Log.e(TAG, "attachVlcViews: Failed", e)
        }
    }

    private fun detachVlcViews() {
        if (!isSurfaceAttached) return
        try {
            Log.d(TAG, "Detaching views from MediaPlayer")
            mediaPlayer?.detachViews()
            isSurfaceAttached = false
            attachedLayout = null
        } catch (e: Exception) {
            Log.e(TAG, "detachVlcViews: Failed", e)
        }
    }
}
