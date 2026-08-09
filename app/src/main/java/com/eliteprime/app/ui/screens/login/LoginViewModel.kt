package com.eliteprime.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val xtreamRepository: XtreamRepository,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.fetchConfig()
        }
    }

    fun login(user: String, pass: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val success = xtreamRepository.login(user, pass)
            if (success) {
                // 1. Sync initial data
                xtreamRepository.syncCategories()
                xtreamRepository.syncAllStreams(
                    onLiveProgress = { count -> _syncProgress.update { it.copy(liveCount = count) } },
                    onMovieProgress = { count -> _syncProgress.update { it.copy(movieCount = count) } },
                    onSeriesProgress = { count -> _syncProgress.update { it.copy(seriesCount = count) } }
                )
                
                // 2. UX Countdown (15 seconds)
                for (i in 15 downTo 0) {
                    _syncProgress.update { it.copy(countdown = i) }
                    delay(1000)
                }
                
                _loginState.value = LoginState.Success
            } else {
                _loginState.value = LoginState.Error("Invalid credentials or server error")
            }
        }
    }
}

data class SyncProgress(
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val countdown: Int = -1
)

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}
