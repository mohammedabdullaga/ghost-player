package com.eliteprime.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val account: XtreamAccount? = null,
    val liveCount: Long = 0,
    val movieCount: Long = 0,
    val seriesCount: Long = 0,
    val epgCount: Long = 0,
    val isSyncing: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    boxStore: BoxStore,
    private val xtreamRepository: XtreamRepository
) : ViewModel() {

    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)
    private val epgBox = boxStore.boxFor(EPGProgram::class.java)

    private val _isSyncing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SettingsUiState> = combine(
        accountBox.query().equal(XtreamAccount_.isActive, true).build().flow().map { it.firstOrNull() },
        liveBox.query().build().flow().map { it.size.toLong() },
        movieBox.query().build().flow().map { it.size.toLong() },
        seriesBox.query().build().flow().map { it.size.toLong() },
        epgBox.query().build().flow().map { it.size.toLong() },
        _isSyncing
    ) { params ->
        SettingsUiState(
            account = params[0] as? XtreamAccount,
            liveCount = params[1] as Long,
            movieCount = params[2] as Long,
            seriesCount = params[3] as Long,
            epgCount = params[4] as Long,
            isSyncing = params[5] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    // Backward compatibility for existing code if any
    val account = uiState.map { it.account }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun syncAll() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                xtreamRepository.syncCategories()
                xtreamRepository.syncAllStreams(
                    onLiveProgress = {},
                    onMovieProgress = {},
                    onSeriesProgress = {}
                )
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun updateAccount(account: XtreamAccount) {
        accountBox.put(account)
    }

    fun logout() {
        accountBox.removeAll()
    }
}
