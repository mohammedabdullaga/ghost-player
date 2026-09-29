package com.eliteprime.app.ui.tv.screens.sports

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.LiveStream_
import com.eliteprime.app.data.model.EventItem
import com.eliteprime.app.data.model.HomeFeedResponse
import com.eliteprime.app.data.remote.SportsApiService
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
            try {
                val response = sportsApiService.getHomeFeed("application/json", "ar", "ar")
                _uiState.update { it.copy(isLoading = false, feed = response) }
            } catch (e: Exception) {
                Log.e("SportsViewModel", "Failed to fetch sports feed", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Network error") }
            }
        }
    }

    /**
     * Resolves local channel entity for the given event by matching upstream streamId.
     * Guaranteed to use existing local channel entity to preserve player decoders & auth.
     */
    fun findChannelForEvent(event: EventItem): LiveStream? {
        val targetStream = event.streams?.firstOrNull { it.primary } ?: event.streams?.firstOrNull() ?: return null
        val upstreamStreamId = targetStream.streamId?.toLongOrNull() ?: targetStream.id?.toLongOrNull() ?: return null

        return try {
            val liveStreamBox = boxStore.boxFor(LiveStream::class.java)
            liveStreamBox.query(LiveStream_.id.equal(upstreamStreamId)).build().findFirst()
                ?: liveStreamBox.get(upstreamStreamId)
        } catch (e: Exception) {
            Log.e("SportsViewModel", "Error querying LiveStream for streamId=$upstreamStreamId", e)
            null
        }
    }
}
