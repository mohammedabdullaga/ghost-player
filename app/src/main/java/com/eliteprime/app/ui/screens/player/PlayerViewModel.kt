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
import kotlinx.coroutines.Dispatchers
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
    private val from: String? = savedStateHandle.get<String>("from")
    private var initialTitle: String = savedStateHandle.get<String>("title") ?: ""
    private val overrideUrl: String? = savedStateHandle.get<String>("url")

    private val _displayTitle = MutableStateFlow(initialTitle)
    val title: StateFlow<String> = _displayTitle.asStateFlow()

    private val _channelLogoUrl = MutableStateFlow<String?>(null)
    val channelLogoUrl: StateFlow<String?> = _channelLogoUrl.asStateFlow()

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
            PlayerSurfaceResizeMode.MATCH_PARENT -> AspectRatio.MATCH_PARENT.modeName
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
        val account = runCatching {
            boxStore.boxFor(XtreamAccount::class.java).query().equal(XtreamAccount_.isActive, true).build().use { it.findFirst() }
        }.getOrNull()
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
        viewModelScope.launch(Dispatchers.IO) {
            val liveBox = boxStore.boxFor(LiveStream::class.java)
            val currentChannel = runCatching { liveBox.get(activeStreamId) }.getOrNull()
            if (currentChannel != null) {
                _channelLogoUrl.value = currentChannel.streamIcon
                
                // If the channel is marked as favorite, channel switching (D-pad up/down / CH+/CH-)
                // cycles through all favorite channels.
                val allChannels = runCatching {
                    if (currentChannel.isFavorite) {
                        liveBox.query()
                            .equal(LiveStream_.isFavorite, true)
                            .order(LiveStream_.order)
                            .build()
                            .use { it.find().filter { stream -> !stream.isHidden } }
                    } else {
                        val catId = currentChannel.remoteCategoryId
                        if (!catId.isNullOrEmpty()) {
                            liveBox.query()
                                .equal(LiveStream_.remoteCategoryId, catId, StringOrder.CASE_INSENSITIVE)
                                .order(LiveStream_.order)
                                .build()
                                .use { it.find().filter { stream -> !stream.isHidden } }
                        } else emptyList()
                    }
                }.getOrDefault(emptyList())

                // Fallback: If category query produced an empty list or doesn't contain current channel,
                // fetch all non-hidden live channels so navigation never breaks.
                val finalChannels = if (allChannels.none { it.id == currentChannel.id }) {
                    runCatching {
                        liveBox.query()
                            .equal(LiveStream_.isHidden, false)
                            .order(LiveStream_.order)
                            .build()
                            .use { it.find() }
                    }.getOrDefault(listOf(currentChannel))
                } else {
                    allChannels
                }

                currentChannels = finalChannels
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
        _hasNextEpisode.value = currentChannels.size > 1
        _hasPreviousEpisode.value = currentChannels.size > 1
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
        } else if (type == "live" && currentChannels.isNotEmpty()) {
            val index = currentChannels.indexOfFirst { it.id == activeStreamId }
            val nextIndex = if (index < 0 || index >= currentChannels.size - 1) 0 else index + 1
            val nextChannel = currentChannels[nextIndex]
            activeStreamId = nextChannel.id
            _displayTitle.value = nextChannel.name
            _channelLogoUrl.value = nextChannel.streamIcon
            navigateToLive(nextChannel)
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
        } else if (type == "live" && currentChannels.isNotEmpty()) {
            val index = currentChannels.indexOfFirst { it.id == activeStreamId }
            val prevIndex = if (index <= 0) currentChannels.size - 1 else index - 1
            val prevChannel = currentChannels[prevIndex]
            activeStreamId = prevChannel.id
            _displayTitle.value = prevChannel.name
            _channelLogoUrl.value = prevChannel.streamIcon
            navigateToLive(prevChannel)
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
                val allHostUrls = xtreamRepository.getActiveHostUrls()
                val accountBase = account.serverUrl.trimEnd('/') + "/"
                val primaryUrl = XtreamUrlFactory.buildPlaybackUrl(
                    serverUrl = account.serverUrl,
                    username = account.username,
                    password = account.password,
                    kind = "live",
                    streamId = channel.id
                )
                val fallbacks = allHostUrls
                    .filter { it != accountBase }
                    .map { hostUrl ->
                        XtreamUrlFactory.buildPlaybackUrl(hostUrl, account.username, account.password, "live", channel.id)
                    }
                Log.w("PlayerVM", "navigateToLive: ${fallbacks.size} fallbacks from ${allHostUrls.size} hosts. Account=$accountBase. Hosts=$allHostUrls")
                
                playerEngine.prepare(StreamInfo(
                    streamId = channel.id,
                    name = channel.name,
                    url = primaryUrl,
                    streamType = StreamType.MPEG_TS,
                    isLive = true,
                    fallbackUrls = fallbacks
                ))
                playerEngine.play()
                
                updateLiveNavigation(channel)
            }
        }
    }

    private fun resolveAndPlay(targetStreamId: Long, targetTitle: String, resumeAt: Long? = null) {
        Log.e("PlayerVM", "resolveAndPlay CALLED: id=$targetStreamId type=$type overrideUrl=$overrideUrl")
        viewModelScope.launch {
            val account = xtreamRepository.getAccount()
            Log.e("PlayerVM", "resolveAndPlay account=$account")
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
                    val streamType = if (type == "live") StreamType.MPEG_TS else StreamType.fromContainerExtension(extension)
                    // Build fallback URLs directly from all active hosts
                    Log.e("PlayerVM", "BUILDING FALLBACKS overrideUrl=$overrideUrl directUrl=$directUrl")
                    val fallbackUrls = if (overrideUrl.isNullOrBlank() && directUrl.isNullOrBlank()) {
                        val allHostUrls = xtreamRepository.getActiveHostUrls()
                        Log.e("PlayerVM", "Hosts from repo: ${allHostUrls.size} -> $allHostUrls")
                        val accountBase = account.serverUrl.trimEnd('/') + "/"
                        val kind = if (type == "series") "series" else type
                        val result = allHostUrls
                            .filter { it != accountBase }
                            .map { hostUrl ->
                                XtreamUrlFactory.buildPlaybackUrl(hostUrl, account.username, account.password, kind, targetStreamId, extension)
                            }
                        Log.e("PlayerVM", "Fallback URLs: ${result.size} (filtered from ${allHostUrls.size})")
                        result
                    } else emptyList()

                    playerEngine.prepare(StreamInfo(
                        streamId = targetStreamId, 
                        name = targetTitle, 
                        url = url, 
                        streamType = streamType,
                        isLive = (type == "live"),
                        fallbackUrls = fallbackUrls
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
            PlayerSurfaceResizeMode.ZOOM -> PlayerSurfaceResizeMode.MATCH_PARENT
            PlayerSurfaceResizeMode.MATCH_PARENT -> PlayerSurfaceResizeMode.FIT
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
        Log.d("FullscreenTransition", "Player screen disposed (leaving fullscreen) at elapsed=${SystemClock.elapsedRealtime()} | type=$type | from=$from")
        
        // Direct transition from FULLSCREEN to PREVIEW is ONLY for Live TV guide
        // where an embedded preview player window is rendered on screen.
        if (type == "live" && (from == "live_guide" || from == "live_preview")) {
            globalPlayerState.enterPreview(playerEngine)
        } else {
            // Stop the engine and clear its surface so audio/video stops
            // completely when returning to Sports, Home, Search, VOD, etc.
            playerEngine.stop()
            playerEngine.clearRenderBinding()
            globalPlayerState.hide()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (type != "live" || (from != "live_guide" && from != "live_preview")) {
            playerEngine.stop()
            playerEngine.clearRenderBinding()
            globalPlayerState.hide()
        }
    }
}
