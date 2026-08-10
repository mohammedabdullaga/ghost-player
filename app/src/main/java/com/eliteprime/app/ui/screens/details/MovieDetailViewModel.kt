package com.eliteprime.app.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.repository.XtreamRepository
import com.eliteprime.app.domain.model.DownloadContentType
import com.eliteprime.app.domain.model.DownloadRequest
import com.eliteprime.app.domain.model.Result
import com.eliteprime.app.domain.repository.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val xtreamRepository: XtreamRepository,
    private val downloadManager: DownloadManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movieId: Long = savedStateHandle.get<Long>("id") ?: 0
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)

    val movie = movieBox.query().equal(Movie_.id, movieId).build().flow().map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val relatedMovies = movie.flatMapLatest { m ->
        if (m == null) flowOf(emptyList())
        else {
            // Find other movies in the same category
            movieBox.query()
                .equal(Movie_.remoteCategoryId, m.remoteCategoryId ?: "", StringOrder.CASE_INSENSITIVE)
                .notEqual(Movie_.id, m.id)
                .build()
                .flow()
                .map { it.take(12) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadDetails()
    }

    val downloadItem = downloadManager.observeAllDownloads()
        .map { downloads ->
            downloads.find { it.contentId == movieId && it.contentType == DownloadContentType.MOVIE }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun loadDetails() {
        viewModelScope.launch {
            val m = movieBox.get(movieId)
            if (m != null) {
                xtreamRepository.fetchMovieDetails(m)
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = movie.value ?: return@launch
            current.isFavorite = !current.isFavorite
            movieBox.put(current)
        }
    }

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    fun downloadMovie() {
        viewModelScope.launch {
            val m = movie.value ?: return@launch
            val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@launch
            
            val request = DownloadRequest(
                providerId = account.id,
                contentType = DownloadContentType.MOVIE,
                contentId = m.id,
                contentName = m.name,
                streamUrl = "", // Managed by DownloadManager
                containerExtension = m.containerExtension,
                posterUrl = m.streamIcon
            )
            val result = downloadManager.enqueueDownload(request)
            if (result is Result.Success) {
                _userMessage.emit("Movie added to download queue")
            } else if (result is Result.Error) {
                _userMessage.emit(result.message)
            }
        }
    }
}
