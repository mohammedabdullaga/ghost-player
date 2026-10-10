package com.ghost.tv.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tv.domain.model.DownloadContentType
import com.ghost.tv.domain.model.DownloadItem
import com.ghost.tv.domain.repository.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DownloadFilter { ALL, MOVIES, SERIES }

data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val filteredDownloads: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = false,
    val currentFilter: DownloadFilter = DownloadFilter.ALL,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val allCount: Int = 0
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _currentFilter = MutableStateFlow(DownloadFilter.ALL)

    val uiState: StateFlow<DownloadsUiState> = combine(
        downloadManager.observeAllDownloads(),
        _currentFilter
    ) { downloads, filter ->
        val uniqueDownloads = downloads.distinctBy { it.id }
        val filtered = when (filter) {
            DownloadFilter.ALL -> uniqueDownloads
            DownloadFilter.MOVIES -> uniqueDownloads.filter { it.contentType == DownloadContentType.MOVIE }
            DownloadFilter.SERIES -> uniqueDownloads.filter { it.contentType == DownloadContentType.SERIES_EPISODE }
        }
        
        DownloadsUiState(
            downloads = uniqueDownloads,
            filteredDownloads = filtered,
            isLoading = false,
            currentFilter = filter,
            allCount = uniqueDownloads.size,
            movieCount = uniqueDownloads.count { it.contentType == DownloadContentType.MOVIE },
            seriesCount = uniqueDownloads.count { it.contentType == DownloadContentType.SERIES_EPISODE }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadsUiState(isLoading = true))

    fun setFilter(filter: DownloadFilter) {
        _currentFilter.value = filter
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch {
            downloadManager.resumeDownload(id)
        }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch {
            downloadManager.cancelDownload(id)
        }
    }

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            downloadManager.deleteDownload(id)
        }
    }
}
