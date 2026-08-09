package com.eliteprime.app.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.repository.TvContinuationRepository
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.StreamType
import com.eliteprime.app.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerEngine: PlayerEngine,
    private val boxStore: BoxStore,
    private val tvContinuationRepository: TvContinuationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val streamId: Long = savedStateHandle.get<Long>("id") ?: 0
    private val type: String = savedStateHandle.get<String>("type") ?: "live"
    val title: String = savedStateHandle.get<String>("title") ?: ""

    val playbackState = playerEngine.playbackState
    val isPlaying = playerEngine.isPlaying
    val currentPosition = playerEngine.currentPosition
    val duration = playerEngine.duration
    val videoFormat = playerEngine.videoFormat
    val isMuted = playerEngine.isMuted
    val availableAudioTracks = playerEngine.availableAudioTracks
    val availableSubtitleTracks = playerEngine.availableSubtitleTracks
    val availableVideoTracks = playerEngine.availableVideoTracks
    val playerStats = playerEngine.playerStats

    private val _showControls = MutableStateFlow(true)
    val showControls = _showControls.asStateFlow()

    private val _aspectRatio = MutableStateFlow("FIT")
    val aspectRatio = _aspectRatio.asStateFlow()

    private val _showDiagnostics = MutableStateFlow(false)
    val showDiagnostics = _showDiagnostics.asStateFlow()

    init {
        resolveAndPlay()
        observePlayback()
        startAutoHideTimer()
    }

    private fun resolveAndPlay() {
        val account = boxStore.boxFor(XtreamAccount::class.java).query().equal(XtreamAccount_.isActive, true).build().findFirst()
        if (account != null) {
            val url = when (type) {
                "live" -> "${account.serverUrl}live/${account.username}/${account.password}/$streamId.ts"
                "movie" -> "${account.serverUrl}movie/${account.username}/${account.password}/$streamId.mp4"
                "series" -> "${account.serverUrl}series/${account.username}/${account.password}/$streamId.mp4"
                "download" -> title // For downloads, we pass the local path in title field for now
                else -> ""
            }
            if (url.isNotEmpty()) {
                playerEngine.prepare(StreamInfo(streamId, title, url, when(type){
                    "live" -> StreamType.LIVE
                    "movie" -> StreamType.MOVIE
                    else -> StreamType.SERIES
                }))
                playerEngine.play()
            }
        }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            currentPosition.collectLatest { pos ->
                if (type != "live" && pos > 0) {
                    savePlaybackProgress(pos)
                }
            }
        }
    }

    private fun savePlaybackProgress(pos: Long) {
        val historyBox = boxStore.boxFor(PlaybackHistory::class.java)
        val history = PlaybackHistory(
            streamId = streamId,
            name = title,
            type = type,
            lastPositionMs = pos,
            durationMs = duration.value
        )
        val existing = historyBox.query().equal(PlaybackHistory_.streamId, streamId).build().findFirst()
        if (existing != null) {
            history.id = existing.id
        }
        historyBox.put(history)
        
        viewModelScope.launch {
            tvContinuationRepository.pushToLauncher(listOf(history))
        }
    }

    private fun startAutoHideTimer() {
        viewModelScope.launch {
            while (true) {
                if (_showControls.value) {
                    delay(5000)
                    if (_showControls.value) {
                        _showControls.value = false
                    }
                }
                delay(1000)
            }
        }
    }

    fun toggleControls() {
        _showControls.value = !_showControls.value
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            playerEngine.pause()
        } else {
            playerEngine.play()
        }
    }

    fun seekTo(positionMs: Long) {
        playerEngine.seekTo(positionMs)
    }

    fun seekForward() {
        playerEngine.seekTo(currentPosition.value + 10000)
    }

    fun seekBackward() {
        playerEngine.seekTo(currentPosition.value - 10000)
    }

    fun toggleMute() {
        playerEngine.toggleMute()
    }

    fun selectAudioTrack(trackId: String) {
        playerEngine.selectAudioTrack(trackId)
    }

    fun selectSubtitleTrack(trackId: String?) {
        playerEngine.selectSubtitleTrack(trackId)
    }

    fun toggleAspectRatio() {
        _aspectRatio.value = when (_aspectRatio.value) {
            "FIT" -> "FILL"
            "FILL" -> "ZOOM"
            else -> "FIT"
        }
    }

    fun toggleDiagnostics() {
        _showDiagnostics.value = !_showDiagnostics.value
    }

    override fun onCleared() {
        super.onCleared()
        playerEngine.release()
    }
}
