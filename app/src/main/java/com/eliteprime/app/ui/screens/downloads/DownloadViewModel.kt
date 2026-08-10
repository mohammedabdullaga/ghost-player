package com.eliteprime.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.domain.model.DownloadItem
import com.eliteprime.app.domain.repository.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = downloadManager.observeAllDownloads()
        .map { DownloadsUiState(downloads = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadsUiState(isLoading = true))

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
