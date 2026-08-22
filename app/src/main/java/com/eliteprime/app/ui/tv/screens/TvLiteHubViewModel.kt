package com.eliteprime.app.ui.tv.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.XtreamAccount
import com.eliteprime.app.data.local.entity.XtreamAccount_
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.data.repository.XtreamRepository
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
    val syncMessage: String? = null
)

@HiltViewModel
class TvLiteHubViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val xtreamRepository: XtreamRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)

    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LiteHubUiState> = combine(
        accountBox.query().equal(XtreamAccount_.isActive, true).build().flow().map { it.firstOrNull() },
        _isSyncing,
        _syncMessage
    ) { account, syncing, message ->
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
            isSyncing = syncing,
            syncMessage = message
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
}
