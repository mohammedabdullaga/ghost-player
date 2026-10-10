package com.ghost.tv.ui.tv.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tv.data.config.ConfigRepository
import com.ghost.tv.data.local.entity.LiveStream
import com.ghost.tv.data.local.entity.Movie
import com.ghost.tv.data.local.entity.Series
import com.ghost.tv.data.local.entity.XtreamAccount
import com.ghost.tv.data.local.entity.XtreamAccount_
import com.ghost.tv.data.repository.PreferenceRepository
import com.ghost.tv.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class LiteHubUiState(
    val accountName: String = "",
    val formattedExpiryDate: String? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val isSecureConnectionEnabled: Boolean = false,
    val isByeDpiAvailable: Boolean = true
)

private data class SystemStatusState(
    val isSyncing: Boolean,
    val syncMessage: String?,
    val isSecureEnabled: Boolean,
    val isByeDpiAvailable: Boolean
)

@HiltViewModel
class TvLiteHubViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val xtreamRepository: XtreamRepository,
    private val preferenceRepository: PreferenceRepository,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)

    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LiteHubUiState> = combine(
        combine(
            boxStore.boxFor(LiveStream::class.java).query().build().flow().map { it.size },
            boxStore.boxFor(Movie::class.java).query().build().flow().map { it.size },
            boxStore.boxFor(Series::class.java).query().build().flow().map { it.size }
        ) { live, movies, series -> Triple(live, movies, series) },
        accountBox.query().equal(XtreamAccount_.isActive, true).build().flow().map { it.firstOrNull() },
        combine(
            _isSyncing,
            _syncMessage,
            preferenceRepository.isSecureConnectionEnabled,
            configRepository.isByeDpiAvailable
        ) { syncing, message, isSecureEnabled, isByeDpiAvail ->
            SystemStatusState(syncing, message, isSecureEnabled, isByeDpiAvail)
        }
    ) { counts, account, status ->
        val (liveCount, movieCount, seriesCount) = counts
        val expiryFormatted = account?.expiryDate?.let { timestamp ->
            if (timestamp > 0) {
                try {
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
                } catch (_: Exception) {
                    null
                }
            } else null
        }

        LiteHubUiState(
            accountName = account?.username ?: account?.name ?: "",
            formattedExpiryDate = expiryFormatted,
            isSyncing = status.isSyncing,
            syncMessage = status.syncMessage,
            liveCount = liveCount,
            movieCount = movieCount,
            seriesCount = seriesCount,
            isSecureConnectionEnabled = status.isSecureEnabled,
            isByeDpiAvailable = status.isByeDpiAvailable
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiteHubUiState())

    fun reloadData(onFinished: ((Boolean) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            var success = false
            try {
                xtreamRepository.syncCategories()
                xtreamRepository.syncAllStreams(
                    onLiveProgress = {},
                    onMovieProgress = {},
                    onSeriesProgress = {}
                )
                preferenceRepository.setLastSyncTimestamp(System.currentTimeMillis())
                success = true
            } catch (e: Exception) {
                success = false
            } finally {
                _isSyncing.value = false
                onFinished?.invoke(success)
            }
        }
    }

    fun setLiteMode(enabled: Boolean) {
        preferenceRepository.setLiteModeEnabled(enabled)
    }

    fun setSecureConnectionEnabled(enabled: Boolean) {
        preferenceRepository.setSecureConnectionEnabled(enabled)
    }
}
