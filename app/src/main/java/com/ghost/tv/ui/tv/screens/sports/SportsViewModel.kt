package com.ghost.tv.ui.tv.screens.sports

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tv.data.local.entity.LiveStream
import com.ghost.tv.data.local.entity.LiveStream_
import com.ghost.tv.data.model.EventItem
import com.ghost.tv.data.model.HomeFeedResponse
import com.ghost.tv.data.remote.SportsApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SportsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val feed: HomeFeedResponse? = null
)

@HiltViewModel
class SportsViewModel @Inject constructor(
    private val sportsApiService: SportsApiService,
    private val boxStore: BoxStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SportsUiState())
    val uiState: StateFlow<SportsUiState> = _uiState.asStateFlow()

    init {
        loadSportsFeed()
    }

    fun loadSportsFeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            Log.i("SportsViewModel", "Requesting sports feed from GET https://cms.ghosttvs.com/api/v1/home?lang=ar")
            try {
                val response = sportsApiService.getHomeFeed("application/json", "ar", "ar")
                val shelfCount = response.shelves?.size ?: 0
                val totalItems = response.shelves?.sumOf { it.items?.size ?: 0 } ?: 0
                Log.i("SportsViewModel", "Sports feed loaded successfully: $shelfCount shelves, $totalItems total events")
                
                response.shelves?.forEachIndexed { index, shelf ->
                    Log.d("SportsViewModel", "Shelf [$index]: ID=${shelf.id}, Title='${shelf.resolvedTitle}', Items=${shelf.items?.size ?: 0}")
                }

                _uiState.update { it.copy(isLoading = false, feed = response) }
            } catch (e: Exception) {
                Log.e("SportsViewModel", "Failed to fetch sports feed", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Network error") }
            }
        }
    }

    /**
     * Resolves local channel entity for the given event by matching upstream streamId or channel name.
     * Guaranteed to use existing local channel entity to preserve player decoders & auth.
     */
    fun findChannelForEvent(event: EventItem): LiveStream? {
        val targetStream = event.streams?.firstOrNull { it.primary } ?: event.streams?.firstOrNull()
        if (targetStream == null) {
            Log.w("SportsViewModel", "No streams found for event: '${event.title}'")
            return null
        }

        val upstreamStreamId = targetStream.streamId.toLongOrNull() ?: targetStream.id.toLongOrNull()
        val channelName = targetStream.resolvedChannelName

        Log.d("SportsViewModel", "Matching event '${event.title}' -> Stream ID: $upstreamStreamId, Channel Name: '$channelName'")

        return try {
            val liveStreamBox = boxStore.boxFor(LiveStream::class.java)

            // 1. Primary: Match by stream_id (ObjectBox ID)
            var match: LiveStream? = null
            if (upstreamStreamId != null && upstreamStreamId > 0) {
                match = liveStreamBox.get(upstreamStreamId)
                    ?: liveStreamBox.query(LiveStream_.id.equal(upstreamStreamId)).build().findFirst()
            }

            // 2. Fallback: Search by channel name
            if (match == null && channelName.isNotBlank()) {
                match = liveStreamBox.query(LiveStream_.name.contains(channelName)).build().findFirst()
            }

            if (match != null) {
                Log.i("SportsViewModel", "Found matching LiveStream entity for event '${event.title}': ID=${match.id}, Name='${match.name}'")
            } else {
                Log.w("SportsViewModel", "No matching LiveStream found in ObjectBox for event '${event.title}' (Stream ID: $upstreamStreamId, Name: '$channelName')")
            }
            match
        } catch (e: Exception) {
            Log.e("SportsViewModel", "Error querying LiveStream for event '${event.title}'", e)
            null
        }
    }
}
