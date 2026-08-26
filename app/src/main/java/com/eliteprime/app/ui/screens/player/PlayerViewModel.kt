package com.eliteprime.app.ui.screens.player

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.MainActivity
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.remote.XtreamUrlFactory
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.data.repository.XtreamRepository
import com.eliteprime.app.data.repository.TvContinuationRepository
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.StreamType
import com.eliteprime.app.domain.repository.DownloadManager
import com.eliteprime.app.domain.repository.PlaybackHistoryRepository
import com.eliteprime.app.player.*
import com.eliteprime.app.player.playback.PlayerErrorClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerEngine: PlayerEngine,
    val globalPlayerState: GlobalPlayerState,
    private val boxStore: BoxStore,
    private val tvContinuationRepository: TvContinuationRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val downloadManager: DownloadManager,
    private val xtreamRepository: XtreamRepository,
    private val preferenceRepository: PreferenceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val streamId: Long = savedStateHandle.get<Long>("id") ?: 0
    val type: String = savedStateHandle.get<String>("type") ?: "live"
    private var initialTitle: String = savedStateHandle.get<String>("title") ?: ""
    private val overrideUrl: String? = savedStateHandle.get<String>("url")

    private val _displayTitle = MutableStateFlow(initialTitle)
    val title: StateFlow<String> = _displayTitle.asStateFlow()

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

    private val _audioDelayMs = MutableStateFlow(0L)
    val audioDelayMs = _audioDelayMs.asStateFlow()

    private val _subtitleDelayMs = MutableStateFlow(0L)
    val subtitleDelayMs = _subtitleDelayMs.asStateFlow()

    private val _seekAccumulator = MutableStateFlow(0)
    val seekAccumulator = _seekAccumulator.asStateFlow()

    private val _resumePrompt = MutableStateFlow(ResumePromptState())
    val resumePrompt = _resumePrompt.asStateFlow()

    private val _hasNextEpisode = MutableStateFlow(false)
    val hasNextEpisode = _hasNextEpisode.asStateFlow()

    private val _hasPreviousEpisode = MutableStateFlow(false)
    val hasPreviousEpisode = _hasPreviousEpisode.asStateFlow()

    val preventStandby = preferenceRepository.preventStandby
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var currentEpisodes: List<Episode> = emptyList()
    private var currentChannels: List<LiveStream> = emptyList()
    private var activeStreamId: Long = streamId
    private var activeProviderId: Long = 0
    private var autoResumeJob: Job? = null
    private val _userActionTrigger = MutableSharedFlow<Unit>(replay = 1)
    private var seekJob: Job? = null

    init {
        viewModelScope.launch {
            loadActiveProvider()
            
            val resumePoint = getResumePoint()
            if (resumePoint != null && resumePoint > 10000) {
                _resumePrompt.value = ResumePromptState(
                    show = true,
                    positionMs = resumePoint,
                    title = _displayTitle.value
                )
                startAutoResumeTimer()
                resolveAndPlay(activeStreamId, initialTitle, resumeAt = resumePoint)
            } else {
                resolveAndPlay(activeStreamId, initialTitle)
            }

            observePlayback()
            startAutoHideTimer()
            observeSubtitleStyle()
            if (type == "series") {
                loadSeriesContext()
            } else if (type == "live") {
                loadLiveContext()
            }
            observeVideoFormat()
        }
    }

    private suspend fun loadActiveProvider() {
        val account = boxStore.boxFor(XtreamAccount::class.java).query().equal(XtreamAccount_.isActive, true).build().findFirst()
        if (account != null) {
            activeProviderId = account.id
        }
    }

    private suspend fun getResumePoint(): Long? {
        if (type == "live") return null
        var count = 0
        while (activeProviderId == 0L && count < 20) {
            delay(50)
            count++
        }
        return playbackHistoryRepository.getPlaybackHistory(activeStreamId, type, activeProviderId)?.lastPositionMs
    }

    private fun startAutoResumeTimer() {
        autoResumeJob?.cancel()
        autoResumeJob = viewModelScope.launch {
            delay(10000)
            if (_resumePrompt.value.show) {
                Log.d("PlayerVM", "Auto-resuming after 10s timeout")
                dismissResumePrompt(true)
            }
        }
    }

    fun dismissResumePrompt(resume: Boolean) {
        autoResumeJob?.cancel()
        val position = _resumePrompt.value.positionMs
        _resumePrompt.value = ResumePromptState(show = false)
        if (resume) {
            playerEngine.seekTo(position)
            playerEngine.play()
        } else {
            playerEngine.seekTo(0)
            playerEngine.play()
        }
    }

    private fun startAutoHideTimer() {
        viewModelScope.launch {
            _userActionTrigger.emit(Unit)
            _userActionTrigger.collectLatest {
                if (_showControls.value) {
                    delay(5000)
                    _showControls.value = false
                }
            }
        }
    }

    fun onUserAction() {
        viewModelScope.launch {
            _userActionTrigger.emit(Unit)
        }
    }

    fun handleOkClick() {
        onUserAction()
        if (!_showControls.value) {
            _showControls.value = true
        } else {
            togglePlayPause()
        }
    }

    fun accumulateSeek(deltaSeconds: Int) {
        if (type == "live") return 
        onUserAction()
        
        _seekAccumulator.value += deltaSeconds
        
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            delay(800)
            val finalSeekAmountMs = _seekAccumulator.value * 1000L
            seekTo(currentPosition.value + finalSeekAmountMs)
            _seekAccumulator.value = 0
        }
    }

    private fun observeSubtitleStyle() {
        preferenceRepository.subtitleStyle
            .onEach { style ->
                playerEngine.setSubtitleStyle(style)
            }
            .launchIn(viewModelScope)
    }

    private fun observeVideoFormat() {
        viewModelScope.launch {
            videoFormat.collectLatest { format ->
                if (format.width > 0 && format.height > 0) {}
            }
        }
    }

    private fun loadSeriesContext() {
        viewModelScope.launch {
            val currentEp = boxStore.boxFor(Episode::class.java).get(activeStreamId)
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

    private fun loadLiveContext() {
        viewModelScope.launch {
            val currentChannel = boxStore.boxFor(LiveStream::class.java).get(activeStreamId)
            if (currentChannel != null) {
                val allChannels = boxStore.boxFor(LiveStream::class.java)
                    .query()
                    .equal(LiveStream_.remoteCategoryId, currentChannel.remoteCategoryId ?: "", StringOrder.CASE_INSENSITIVE)
                    .build()
                    .find()
                
                currentChannels = allChannels
                updateLiveNavigation(currentChannel)
            }
        }
    }

    private fun updateEpisodeNavigation(currentEp: Episode) {
        val index = currentEpisodes.indexOfFirst { it.id == currentEp.id }
        _hasNextEpisode.value = index >= 0 && index < currentEpisodes.size - 1
        _hasPreviousEpisode.value = index > 0
    }

    private fun updateLiveNavigation(currentChannel: LiveStream) {
        val index = currentChannels.indexOfFirst { it.id == currentChannel.id }
        _hasNextEpisode.value = index >= 0 && index < currentChannels.size - 1
        _hasPreviousEpisode.value = index > 0
    }

    fun playNextEpisode() {
        if (type == "series") {
            val index = currentEpisodes.indexOfFirst { it.id == activeStreamId }
            if (index >= 0 && index < currentEpisodes.size - 1) {
                val nextEp = currentEpisodes[index + 1]
                activeStreamId = nextEp.id
                _displayTitle.value = nextEp.title
                navigateToEpisode(nextEp)
            }
        } else if (type == "live") {
            val index = currentChannels.indexOfFirst { it.id == activeStreamId }
            if (index >= 0 && index < currentChannels.size - 1) {
                val nextChannel = currentChannels[index + 1]
                activeStreamId = nextChannel.id
                _displayTitle.value = nextChannel.name
                navigateToLive(nextChannel)
            }
        }
    }

    fun playPreviousEpisode() {
        if (type == "series") {
            val index = currentEpisodes.indexOfFirst { it.id == activeStreamId }
            if (index > 0) {
                val prevEp = currentEpisodes[index - 1]
                activeStreamId = prevEp.id
                _displayTitle.value = prevEp.title
                navigateToEpisode(prevEp)
            }
        } else if (type == "live") {
            val index = currentChannels.indexOfFirst { it.id == activeStreamId }
            if (index > 0) {
                val prevChannel = currentChannels[index - 1]
                activeStreamId = prevChannel.id
                _displayTitle.value = prevChannel.name
                navigateToLive(prevChannel)
            }
        }
    }

    private fun navigateToEpisode(episode: Episode) {
        viewModelScope.launch {
            val account = xtreamRepository.getAccount()
            if (account != null) {
                val url = XtreamUrlFactory.buildPlaybackUrl(
                    serverUrl = account.serverUrl,
                    username = account.username,
                    password = account.password,
                    kind = "series",
                    streamId = episode.id,
                    extension = episode.containerExtension
                )
                
                playbackHistoryRepository.recordPlayback(
                    PlaybackHistory(
                        streamId = episode.id,
                        providerId = account.id,
                        name = episode.title,
                        type = "series",
                        posterUrl = episode.coverUrl,
                        seriesId = episode.seriesId,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber
                    )
                )

                playerEngine.prepare(StreamInfo(episode.id, episode.title, url, StreamType.PROGRESSIVE, isLive = false))
                playerEngine.play()
                
                updateEpisodeNavigation(episode)
            }
        }
    }

    private fun navigateToLive(channel: LiveStream) {
        viewModelScope.launch {
            val account = xtreamRepository.getAccount()
            if (account != null) {
                val url = XtreamUrlFactory.buildPlaybackUrl(
                    serverUrl = account.serverUrl,
                    username = account.username,
                    password = account.password,
                    kind = "live",
                    streamId = channel.id
                )
                
                playerEngine.prepare(StreamInfo(channel.id, channel.name, url, StreamType.MPEG_TS, isLive = true))
                playerEngine.play()
                
                updateLiveNavigation(channel)
            }
        }
    }

    private fun resolveAndPlay(targetStreamId: Long, targetTitle: String, resumeAt: Long? = null) {
        viewModelScope.launch {
            val account = xtreamRepository.getAccount()
            if (account != null) {
                activeProviderId = account.id
                var extension: String? = null
                var directUrl: String? = null
                var poster: String? = null
                var backdrop: String? = null
                var sId: Long? = null
                var seasonNum: Int? = null
                var epNum: Int? = null

                if (type == "movie" || type == "series") {
                    var attempts = 0
                    while (attempts < 5) {
                        when (type) {
                            "movie" -> {
                                val movie = boxStore.boxFor(Movie::class.java).get(targetStreamId)
                                if (movie != null) {
                                    extension = movie.containerExtension
                                    directUrl = movie.directSource
                                    poster = movie.streamIcon
                                    backdrop = movie.backdropUrl
                                    attempts = 99
                                }
                            }
                            "series" -> {
                                val episode = boxStore.boxFor(Episode::class.java).get(targetStreamId)
                                if (episode != null && (episode.seriesId != 0L)) {
                                    extension = episode.containerExtension
                                    poster = episode.coverUrl
                                    sId = episode.seriesId
                                    seasonNum = episode.seasonNumber
                                    epNum = episode.episodeNumber
                                    attempts = 99
                                }
                            }
                        }
                        
                        if (attempts < 99) {
                            val dl = downloadManager.observeAllDownloads().first().find { 
                                it.contentId == targetStreamId && it.contentType.name.lowercase().contains(type) 
                            }
                            if (dl != null) {
                                extension = dl.containerExtension
                                poster = dl.posterUrl
                                sId = dl.seriesId
                                seasonNum = dl.seasonNumber
                                epNum = dl.episodeNumber
                                attempts = 99
                            }
                        }

                        if (attempts < 99) {
                            attempts++
                            delay(200)
                        }
                    }
                }

                val url = if (!overrideUrl.isNullOrBlank()) {
                    if (overrideUrl.startsWith("/") && !overrideUrl.startsWith("file://")) "file://$overrideUrl" else overrideUrl
                } else if (!directUrl.isNullOrBlank()) {
                    directUrl
                } else {
                    XtreamUrlFactory.buildPlaybackUrl(
                        serverUrl = account.serverUrl,
                        username = account.username,
                        password = account.password,
                        kind = if (type == "series") "series" else type,
                        streamId = targetStreamId,
                        extension = extension
                    )
                }

                if (url.isNotEmpty()) {
                    playerEngine.prepare(StreamInfo(
                        streamId = targetStreamId, 
                        name = targetTitle, 
                        url = url, 
                        streamType = if (type == "live") StreamType.MPEG_TS else StreamType.fromContainerExtension(extension),
                        isLive = (type == "live")
                    ))
                    
                    if (resumeAt != null) {
                        playerEngine.seekTo(resumeAt)
                    }
                    playerEngine.play()
                }

                launch {
                    playbackHistoryRepository.recordPlayback(
                        PlaybackHistory(
                            streamId = targetStreamId,
                            providerId = account.id,
                            name = targetTitle,
                            type = type,
                            posterUrl = poster,
                            backdropUrl = backdrop,
                            seriesId = sId,
                            seasonNumber = seasonNum,
                            episodeNumber = epNum
                        )
                    )
                }
            }
        }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            playbackState.collectLatest { state ->
                if (state == PlaybackState.ENDED && type == "series" && _hasNextEpisode.value) {
                    if (preferenceRepository.autoPlayNextEpisode.value) {
                        playNextEpisode()
                    }
                }
            }
        }

        viewModelScope.launch {
            if (type == "live") {
                val liveBox = boxStore.boxFor(LiveStream::class.java)
                val channel = liveBox.get(activeStreamId)
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
        viewModelScope.launch {
            if (activeProviderId == 0L) return@launch
            playbackHistoryRepository.updateResumePosition(
                streamId = activeStreamId,
                type = type,
                providerId = activeProviderId,
                positionMs = pos,
                durationMs = duration.value
            )
        }
    }

    fun toggleControls() {
        onUserAction()
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

    fun adjustAudioDelay(deltaMs: Long) {
        val newDelay = (_audioDelayMs.value + deltaMs).coerceIn(-5000L, 5000L)
        _audioDelayMs.value = newDelay
        playerEngine.setAudioDelay(newDelay)
    }

    fun resetAudioDelay() {
        _audioDelayMs.value = 0L
        playerEngine.setAudioDelay(0L)
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        val newDelay = (_subtitleDelayMs.value + deltaMs).coerceIn(-10000L, 10000L)
        _subtitleDelayMs.value = newDelay
        playerEngine.setSubtitleDelay(newDelay)
    }

    fun resetSubtitleDelay() {
        _subtitleDelayMs.value = 0L
        playerEngine.setSubtitleDelay(0L)
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
        Log.d("FullscreenTransition", "User requested fullscreen at elapsed=${SystemClock.elapsedRealtime()}")
        globalPlayerState.enterFullScreen(playerEngine)
    }

    fun onPlayerScreenDisposed() {
        Log.d("FullscreenTransition", "Player screen disposed (leaving fullscreen) at elapsed=${SystemClock.elapsedRealtime()}")
        
        // POC FIX: Direct transition from FULLSCREEN to PREVIEW for Live TV.
        // This avoids the intermediate HIDDEN state that unmounts the surface.
        if (type == "live") {
            globalPlayerState.enterPreview(playerEngine)
        } else {
            // Stop the engine and clear its surface so VOD doesn't keep
            // playing audio / hold a dangling surface after leaving fullscreen.
            playerEngine.stop()
            playerEngine.clearRenderBinding()
            globalPlayerState.hide()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (type != "live") {
            playerEngine.stop()
        }
    }
}
