package com.ghost.tv.player

import android.content.Context
import android.util.Log
import android.view.View
import com.ghost.tv.data.repository.PreferenceRepository
import com.ghost.tv.domain.model.StreamInfo
import com.ghost.tv.domain.model.VideoFormat
import com.ghost.tv.player.timeshift.LiveTimeshiftState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceRepository: PreferenceRepository,
    private val media3Engine: Media3PlayerEngine,
    private val vlcEngine: VlcPlayerEngine
) : PlayerEngine {

    private val TAG = "MasterPlayerEngine"
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    
    // We use a StateFlow to track which engine is currently active (Default: ExoPlayer / Media3)
    private val _activePlayer = MutableStateFlow<PlayerEngine>(media3Engine)
    
    override val engineType: StateFlow<PlayerType> = _activePlayer.flatMapLatest { it.engineType }
        .stateIn(scope, SharingStarted.Eagerly, PlayerType.EXO)

    // Delegated properties that always point to the active engine
    @OptIn(ExperimentalCoroutinesApi::class)
    override val playbackState = _activePlayer.flatMapLatest { it.playbackState }.stateIn(scope, SharingStarted.Eagerly, PlaybackState.IDLE)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val isPlaying = _activePlayer.flatMapLatest { it.isPlaying }.stateIn(scope, SharingStarted.Eagerly, false)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentPosition = _activePlayer.flatMapLatest { it.currentPosition }.stateIn(scope, SharingStarted.Eagerly, 0L)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val duration = _activePlayer.flatMapLatest { it.duration }.stateIn(scope, SharingStarted.Eagerly, 0L)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val videoFormat = _activePlayer.flatMapLatest { it.videoFormat }.stateIn(scope, SharingStarted.Eagerly, VideoFormat(0, 0, 0f, 0))
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val error = _activePlayer.flatMapLatest { it.error }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val retryStatus = _activePlayer.flatMapLatest { it.retryStatus }.stateIn(scope, SharingStarted.Eagerly, null)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val playerStats = _activePlayer.flatMapLatest { it.playerStats }.stateIn(scope, SharingStarted.Eagerly, PlayerStats())
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val availableAudioTracks = _activePlayer.flatMapLatest { it.availableAudioTracks }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val availableSubtitleTracks = _activePlayer.flatMapLatest { it.availableSubtitleTracks }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val availableVideoTracks = _activePlayer.flatMapLatest { it.availableVideoTracks }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val playbackSpeed = _activePlayer.flatMapLatest { it.playbackSpeed }.stateIn(scope, SharingStarted.Eagerly, 1f)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val isMuted = _activePlayer.flatMapLatest { it.isMuted }.stateIn(scope, SharingStarted.Eagerly, false)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val timeshiftState = _activePlayer.flatMapLatest { it.timeshiftState }.stateIn(scope, SharingStarted.Eagerly, LiveTimeshiftState())
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val renderSurfaceType = _activePlayer.flatMapLatest { it.renderSurfaceType }.stateIn(scope, SharingStarted.Eagerly, PlayerRenderSurfaceType.AUTO)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override val prepareStatus = _activePlayer.flatMapLatest { it.prepareStatus }.stateIn(scope, SharingStarted.Eagerly, null)

    private var lastStreamInfo: StreamInfo? = null

    init {
        // React to preference changes for ALL content types (Live + VOD)
        preferenceRepository.playerEngineMode
            .onEach { mode ->
                val type = if (mode == "VLC") PlayerType.VLC else PlayerType.EXO
                val newEngine = if (type == PlayerType.VLC) vlcEngine else media3Engine

                if (_activePlayer.value != newEngine) {
                    Log.i(TAG, "Settings-based engine switch to: $type")
                    switchEngine(newEngine)
                }
            }
            .launchIn(scope)
    }

    private fun switchEngine(newEngine: PlayerEngine) {
        val oldEngine = _activePlayer.value
        if (oldEngine == newEngine) return

        // Stop old engine and release its surface before switching.
        oldEngine.stop()
        oldEngine.clearRenderBinding()
        
        _activePlayer.value = newEngine
        
        // If we were playing something, resume it on the new engine.
        // The new engine will prepare once its surface is ready.
        lastStreamInfo?.let { info ->
            Log.d(TAG, "Restoring playback on new engine: ${info.url}")
            newEngine.prepare(info)
        }
    }

    override fun prepare(streamInfo: StreamInfo) {
        lastStreamInfo = streamInfo
        
        // Always respect the user's player engine preference
        val mode = preferenceRepository.playerEngineMode.value
        val targetEngine = if (mode == "VLC") vlcEngine else media3Engine

        if (_activePlayer.value != targetEngine) {
            Log.i(TAG, "Preference-based engine switch: mode=$mode isLive=${streamInfo.isLive} -> ${if (targetEngine == vlcEngine) "VLC" else "EXO"}")
            switchEngine(targetEngine)
        } else {
            _activePlayer.value.prepare(streamInfo)
        }
    }

    override fun play() = _activePlayer.value.play()
    override fun pause() = _activePlayer.value.pause()
    override fun stop() {
        lastStreamInfo = null
        _activePlayer.value.stop()
    }
    override fun seekTo(positionMs: Long) = _activePlayer.value.seekTo(positionMs)
    override fun setVolume(volume: Float) = _activePlayer.value.setVolume(volume)
    override fun setMuted(muted: Boolean) = _activePlayer.value.setMuted(muted)
    override fun toggleMute() = _activePlayer.value.toggleMute()
    override fun setPlaybackSpeed(speed: Float) = _activePlayer.value.setPlaybackSpeed(speed)
    override fun selectAudioTrack(trackId: String) = _activePlayer.value.selectAudioTrack(trackId)
    override fun selectVideoTrack(trackId: String) = _activePlayer.value.selectVideoTrack(trackId)
    override fun selectSubtitleTrack(trackId: String?) = _activePlayer.value.selectSubtitleTrack(trackId)
    override fun setSubtitleStyle(style: PlayerSubtitleStyle) = _activePlayer.value.setSubtitleStyle(style)
    override fun setAudioDelay(delayMs: Long) = _activePlayer.value.setAudioDelay(delayMs)
    override fun setSubtitleDelay(delayMs: Long) = _activePlayer.value.setSubtitleDelay(delayMs)
    override fun renewStreamUrl(streamInfo: StreamInfo) {
        lastStreamInfo = streamInfo
        _activePlayer.value.renewStreamUrl(streamInfo)
    }
    override fun release() {
        Log.d(TAG, "Releasing Master engine")
        media3Engine.release()
        vlcEngine.release()
    }

    override fun createRenderView(context: Context, resizeMode: PlayerSurfaceResizeMode, surfaceType: PlayerRenderSurfaceType): View {
        return _activePlayer.value.createRenderView(context, resizeMode, surfaceType)
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        _activePlayer.value.bindRenderView(renderView, resizeMode)
    }

    override fun unbindRenderView(renderView: View) {
        _activePlayer.value.unbindRenderView(renderView)
    }

    override fun clearRenderBinding() {
        _activePlayer.value.clearRenderBinding()
    }
}
