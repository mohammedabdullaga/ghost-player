package com.eliteprime.app.ui.screens.player

import android.app.Activity
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.MainActivity
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.remote.XtreamUrlFactory
import com.eliteprime.app.data.repository.TvContinuationRepository
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.StreamType
import com.eliteprime.app.player.*
import com.eliteprime.app.player.playback.PlayerErrorClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerEngine: PlayerEngine,
    val globalPlayerState: GlobalPlayerState,
    private val boxStore: BoxStore,
    private val tvContinuationRepository: TvContinuationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val streamId: Long = savedStateHandle.get<Long>("id") ?: 0
    val type: String = savedStateHandle.get<String>("type") ?: "live"
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

    val aspectRatio = globalPlayerState.resizeMode.map { mode ->
        when (mode) {
            PlayerSurfaceResizeMode.FIT -> AspectRatio.FIT.modeName
            PlayerSurfaceResizeMode.FILL -> AspectRatio.FILL.modeName
            PlayerSurfaceResizeMode.ZOOM -> AspectRatio.ZOOM.modeName
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AspectRatio.FIT.modeName)

    private val _showDiagnostics = MutableStateFlow(false)
    val showDiagnostics = _showDiagnostics.asStateFlow()

    private val _resumePrompt = MutableStateFlow(ResumePromptState())
    val resumePrompt = _resumePrompt.asStateFlow()

    private val _hasNextEpisode = MutableStateFlow(false)
    val hasNextEpisode = _hasNextEpisode.asStateFlow()

    private val _hasPreviousEpisode = MutableStateFlow(false)
    val hasPreviousEpisode = _hasPreviousEpisode.asStateFlow()

    private var currentEpisodes: List<Episode> = emptyList()

    init {
        checkResumeProgress()
        resolveAndPlay()
        observePlayback()
        startAutoHideTimer()
        if (type == "series") {
            loadSeriesContext()
        }
        observeVideoFormat()
    }

    private fun observeVideoFormat() {
        viewModelScope.launch {
            videoFormat.collectLatest { format ->
                if (format.width > 0 && format.height > 0) {
                    // Update global state or activity for PIP
                    // We need a way to reach the activity from here, but usually it's better via UI side effect
                }
            }
        }
    }

    private fun loadSeriesContext() {
        viewModelScope.launch {
            val currentEp = boxStore.boxFor(Episode::class.java).get(streamId)
            if (currentEp != null) {
                val allEpisodes = boxStore.boxFor(Episode::class.java)
                    .query()
                    .equal(Episode_.seriesId, currentEp.seriesId)
                    .build()
                    .find()
                    .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                
                currentEpisodes = allEpisodes
                updateEpisodeNavigation(currentEp)
            }
        }
    }

    private fun updateEpisodeNavigation(currentEp: Episode) {
        val index = currentEpisodes.indexOfFirst { it.id == currentEp.id }
        _hasNextEpisode.value = index >= 0 && index < currentEpisodes.size - 1
        _hasPreviousEpisode.value = index > 0
    }

    fun playNextEpisode() {
        val index = currentEpisodes.indexOfFirst { it.id == streamId }
        if (index >= 0 && index < currentEpisodes.size - 1) {
            val nextEp = currentEpisodes[index + 1]
            navigateToEpisode(nextEp)
        }
    }

    fun playPreviousEpisode() {
        val index = currentEpisodes.indexOfFirst { it.id == streamId }
        if (index > 0) {
            val prevEp = currentEpisodes[index - 1]
            navigateToEpisode(prevEp)
        }
    }

    private fun navigateToEpisode(episode: Episode) {
        viewModelScope.launch {
            val account = boxStore.boxFor(XtreamAccount::class.java).query().equal(XtreamAccount_.isActive, true).build().findFirst()
            if (account != null) {
                val url = XtreamUrlFactory.buildPlaybackUrl(
                    serverUrl = account.serverUrl,
                    username = account.username,
                    password = account.password,
                    kind = "series",
                    streamId = episode.id,
                    extension = episode.containerExtension
                )
                playerEngine.prepare(StreamInfo(episode.id, episode.title, url, StreamType.PROGRESSIVE))
                playerEngine.play()
                
                updateEpisodeNavigation(episode)
            }
        }
    }

    private fun checkResumeProgress() {
        if (type == "live") return
        val historyBox = boxStore.boxFor(PlaybackHistory::class.java)
        val history = historyBox.query().equal(PlaybackHistory_.streamId, streamId).build().findFirst()
        if (history != null && history.lastPositionMs > 10000) { // More than 10 seconds
            _resumePrompt.value = ResumePromptState(
                show = true,
                positionMs = history.lastPositionMs,
                title = title
            )
        }
    }

    fun dismissResumePrompt(resume: Boolean) {
        val position = _resumePrompt.value.positionMs
        _resumePrompt.value = ResumePromptState(show = false)
        if (resume) {
            playerEngine.seekTo(position)
        }
    }

    private fun resolveAndPlay() {
        viewModelScope.launch {
            val account = boxStore.boxFor(XtreamAccount::class.java).query().equal(XtreamAccount_.isActive, true).build().findFirst()
            if (account != null) {
                var extension: String? = null
                var directUrl: String? = null

                when (type) {
                    "movie" -> {
                        val movie = boxStore.boxFor(Movie::class.java).get(streamId)
                        extension = movie?.containerExtension
                        directUrl = movie?.directSource
                    }
                    "series" -> {
                        val episode = boxStore.boxFor(Episode::class.java).get(streamId)
                        extension = episode?.containerExtension
                    }
                }

                val url = if (type == "download") {
                    title
                } else if (!directUrl.isNullOrBlank()) {
                    directUrl
                } else {
                    XtreamUrlFactory.buildPlaybackUrl(
                        serverUrl = account.serverUrl,
                        username = account.username,
                        password = account.password,
                        kind = if (type == "series") "series" else type,
                        streamId = streamId,
                        extension = extension
                    )
                }

                if (url.isNotEmpty()) {
                    val currentInfo = playerEngine.prepareStatus.value
                    val currentState = playerEngine.playbackState.value

                    if (currentInfo?.url == url && (currentState == PlaybackState.READY || currentState == PlaybackState.BUFFERING)) {
                        Log.d("PlayerVM", "resolveAndPlay: Resuming existing handoff stream")
                        playerEngine.play()
                    } else {
                        Log.d("PlayerVM", "resolveAndPlay: Preparing new stream target=$url")
                        playerEngine.prepare(StreamInfo(
                            streamId = streamId, 
                            name = title, 
                            url = url, 
                            streamType = if (type == "live") StreamType.MPEG_TS else StreamType.fromContainerExtension(extension)
                        ))
                        playerEngine.play()
                    }
                }
            }
        }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            // Observe playback state for auto-play next episode
            playbackState.collectLatest { state ->
                if (state == PlaybackState.ENDED && type == "series" && _hasNextEpisode.value) {
                    playNextEpisode()
                }
            }
        }

        viewModelScope.launch {
            if (type == "live") {
                val liveBox = boxStore.boxFor(LiveStream::class.java)
                val channel = liveBox.get(streamId)
                if (channel != null) {
                    channel.lastWatchedAt = System.currentTimeMillis()
                    liveBox.put(channel)
                }
            }

            currentPosition.collectLatest { pos ->
                if (type != "live" && pos > 0) {
                    savePlaybackProgress(pos)
                }
            }
        }
    }

    private fun savePlaybackProgress(pos: Long) {
        val historyBox = boxStore.boxFor(PlaybackHistory::class.java)
        
        var poster: String? = null
        var backdrop: String? = null
        
        when (type) {
            "movie" -> {
                val movie = boxStore.boxFor(Movie::class.java).get(streamId)
                poster = movie?.streamIcon
                backdrop = movie?.backdropUrl
            }
            "series" -> {
                val episode = boxStore.boxFor(Episode::class.java).get(streamId)
                poster = episode?.coverUrl
                // Series backdrop usually comes from the series entity, but for now we'll just use poster if available
            }
        }

        val history = PlaybackHistory(
            streamId = streamId,
            name = title,
            type = type,
            lastPositionMs = pos,
            durationMs = duration.value,
            posterUrl = poster,
            backdropUrl = backdrop,
            lastPlayedAt = System.currentTimeMillis()
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
        val nextMode = when (globalPlayerState.resizeMode.value) {
            PlayerSurfaceResizeMode.FIT -> PlayerSurfaceResizeMode.FILL
            PlayerSurfaceResizeMode.FILL -> PlayerSurfaceResizeMode.ZOOM
            PlayerSurfaceResizeMode.ZOOM -> PlayerSurfaceResizeMode.FIT
        }
        globalPlayerState.updateResizeMode(nextMode)
    }

    fun enterPip(activity: Activity?) {
        (activity as? MainActivity)?.enterPlayerPictureInPictureMode()
    }

    fun toggleDiagnostics() {
        _showDiagnostics.value = !_showDiagnostics.value
    }

    fun claimFullScreen() {
        globalPlayerState.enterFullScreen(playerEngine)
    }

    fun onPlayerScreenDisposed() {
        globalPlayerState.hide()
        // Explicitly clear binding to allow the preview pane to reclaim the engine surface
        playerEngine.clearRenderBinding()
    }

    override fun onCleared() {
        super.onCleared()
        // DO NOT call playerEngine.stop() here for Live TV.
        // This allows the video to continue in the preview pane (YouTube-like).
        // VOD should still stop to save bandwidth.
        if (type != "live") {
            playerEngine.stop()
        }
    }
}
