package com.eliteprime.app.ui.screens.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.R
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.data.repository.XtreamRepository
import com.eliteprime.app.pairing.ProviderQrPairingManager
import com.eliteprime.app.pairing.ProviderQrPairingState
import com.eliteprime.app.pairing.ProviderQrPairingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val xtreamRepository: XtreamRepository,
    private val configRepository: ConfigRepository,
    private val preferenceRepository: PreferenceRepository,
    private val pairingManager: ProviderQrPairingManager,
    private val application: Application
) : ViewModel() {

    private val TAG = "LoginViewModel"
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    val isSecureConnectionEnabled = preferenceRepository.isSecureConnectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isByeDpiAvailable = configRepository.isByeDpiAvailable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pairingState: StateFlow<ProviderQrPairingState> = pairingManager.state

    init {
        viewModelScope.launch {
            configRepository.fetchConfig()
        }

        // Auto-handle successful pairing login
        pairingState.onEach { state ->
            if (state.status == ProviderQrPairingStatus.COMPLETE) {
                _loginState.value = LoginState.Loading
                startSync()
            }
        }.launchIn(viewModelScope)
    }

    fun setSecureConnectionEnabled(enabled: Boolean) {
        preferenceRepository.setSecureConnectionEnabled(enabled)
    }

    fun startQrPairing() {
        viewModelScope.launch {
            pairingManager.startPairing()
        }
    }

    fun stopQrPairing() {
        viewModelScope.launch {
            pairingManager.stopPairing()
        }
    }

    fun login(user: String, pass: String) {
        viewModelScope.launch {
            Log.d(TAG, "Starting login for user: $user")
            _loginState.value = LoginState.Loading
            try {
                val success = xtreamRepository.login(user, pass)
                Log.d(TAG, "Login result: $success")
                if (success) {
                    startSync()
                } else {
                    val activeHosts = configRepository.getActiveHosts()
                    val errorMsg = if (activeHosts.isEmpty()) {
                        "Service initialization failed. Please check your internet connection."
                    } else {
                        application.getString(R.string.login_error_invalid)
                    }
                    _loginState.value = LoginState.Error(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login crash: ${e.message}", e)
                _loginState.value = LoginState.Error("Login error: ${e.message}")
            }
        }
    }

    private fun startSync() {
        viewModelScope.launch {
            _syncProgress.update { it.copy(countdown = 15) }
            
            // 1. Run sync
            xtreamRepository.syncCategories()
            xtreamRepository.syncAllStreams(
                onLiveProgress = { count -> _syncProgress.update { it.copy(liveCount = count) } },
                onMovieProgress = { count -> _syncProgress.update { it.copy(movieCount = count) } },
                onSeriesProgress = { count -> _syncProgress.update { it.copy(seriesCount = count) } }
            )
            
            // 2. Dynamic Finalization: If sync was fast, don't make user wait the full 15s
            // We show a quick "Finishing" countdown
            for (i in 3 downTo 0) {
                _syncProgress.update { it.copy(countdown = i) }
                delay(400)
            }
            
            _loginState.value = LoginState.Success
            preferenceRepository.setLastSyncTimestamp(System.currentTimeMillis())
        }
        
        // Background countdown just to keep the UI number ticking if sync is slow
        viewModelScope.launch {
            while (_loginState.value is LoginState.Loading && _syncProgress.value.countdown > 3) {
                delay(1000)
                _syncProgress.update { 
                    if (it.countdown > 3) it.copy(countdown = it.countdown - 1) else it 
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopQrPairing()
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
