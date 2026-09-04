package com.eliteprime.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.eliteprime.app.BuildConfig
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.update.AppUpdateManager
import com.eliteprime.app.player.PlayerSubtitleStyle
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.data.repository.XtreamRepository
import com.eliteprime.app.domain.model.DownloadStorageConfig
import com.eliteprime.app.domain.repository.DownloadManager
import com.eliteprime.app.util.speedtest.InternetSpeedTestResult
import com.eliteprime.app.util.speedtest.InternetSpeedTestRunner
import com.eliteprime.app.util.speedtest.InternetSpeedTestSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

data class SettingsUiState(
    val account: XtreamAccount? = null,
    val liveCount: Long = 0,
    val movieCount: Long = 0,
    val seriesCount: Long = 0,
    val epgCount: Long = 0,
    val isSyncing: Boolean = false,
    val language: String = "en",
    val storageConfig: DownloadStorageConfig = DownloadStorageConfig(),
    val isSecureConnectionEnabled: Boolean = false,
    val isByeDpiAvailable: Boolean = true,
    val isSpeedTesting: Boolean = false,
    val speedTestResult: InternetSpeedTestSnapshot? = null,
    val speedTestError: String? = null,
    val subtitleStyle: PlayerSubtitleStyle = PlayerSubtitleStyle(),
    val autoPlayNextEpisode: Boolean = true,
    val zapAutoRevert: Boolean = false,
    val preventStandby: Boolean = true,
    val isLiteModeEnabled: Boolean = false,
    val liveBufferSize: Int = 0,
    val decoderMode: String = "AUTO",
    val playerEngineMode: String = "EXO",
    val lastUpdateCheckTimestamp: Long = 0,
    val isCheckingForUpdates: Boolean = false,
    val latestVersion: String? = null,
    val updateStatus: String = "Up to date"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val xtreamRepository: XtreamRepository,
    private val preferenceRepository: PreferenceRepository,
    private val configRepository: ConfigRepository,
    val appUpdateManager: AppUpdateManager,
    private val downloadManager: DownloadManager,
    private val speedTestRunner: InternetSpeedTestRunner,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)
    private val epgBox = boxStore.boxFor(EPGProgram::class.java)

    private val _isSyncing = MutableStateFlow(false)
    private val _isSpeedTesting = MutableStateFlow(false)
    private val _speedTestSnapshot = MutableStateFlow<InternetSpeedTestSnapshot?>(null)
    private val _speedTestError = MutableStateFlow<String?>(null)

    private val _isCheckingForUpdates = MutableStateFlow(false)
    private val _latestVersion = MutableStateFlow<String?>(null)
    private val _updateStatus = MutableStateFlow("Up to date")
    private val _lastUpdateCheckTimestamp = MutableStateFlow(preferenceRepository.getLastUpdateCheckTimestamp())

    // Focus position state — survives navigation
    private val _selectedSettingTab = MutableStateFlow<String>("ACCOUNT")
    val selectedSettingTab: StateFlow<String> = _selectedSettingTab.asStateFlow()

    fun recordSettingTab(tabName: String) {
        _selectedSettingTab.value = tabName
    }

    private fun <T> reactiveCount(box: Box<T>): Flow<Long> = callbackFlow {
        val subscription = boxStore.subscribe(box.entityClass)
            .observer { trySend(box.count()) }
        trySend(box.count())
        awaitClose { subscription.cancel() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SettingsUiState> = combine(
        accountBox.query().equal(XtreamAccount_.isActive, true).build().flow().map { it.firstOrNull() },
        reactiveCount(liveBox),
        reactiveCount(movieBox),
        reactiveCount(seriesBox),
        reactiveCount(epgBox),
        _isSyncing,
        preferenceRepository.language,
        downloadManager.observeStorageState(),
        preferenceRepository.isSecureConnectionEnabled,
        configRepository.isByeDpiAvailable,
        _isSpeedTesting,
        _speedTestSnapshot,
        _speedTestError,
        preferenceRepository.subtitleStyle,
        preferenceRepository.autoPlayNextEpisode,
        preferenceRepository.zapAutoRevert,
        preferenceRepository.preventStandby,
        preferenceRepository.isLiteModeEnabled,
        preferenceRepository.liveBufferSize,
        preferenceRepository.decoderMode,
        preferenceRepository.playerEngineMode,
        _isCheckingForUpdates,
        _latestVersion,
        _updateStatus,
        _lastUpdateCheckTimestamp
    ) { params ->
        SettingsUiState(
            account = params[0] as? XtreamAccount,
            liveCount = params[1] as Long,
            movieCount = params[2] as Long,
            seriesCount = params[3] as Long,
            epgCount = params[4] as Long,
            isSyncing = params[5] as Boolean,
            language = params[6] as String,
            storageConfig = params[7] as DownloadStorageConfig,
            isSecureConnectionEnabled = params[8] as Boolean,
            isByeDpiAvailable = params[9] as Boolean,
            isSpeedTesting = params[10] as Boolean,
            speedTestResult = params[11] as? InternetSpeedTestSnapshot,
            speedTestError = params[12] as? String,
            subtitleStyle = params[13] as PlayerSubtitleStyle,
            autoPlayNextEpisode = params[14] as Boolean,
            zapAutoRevert = params[15] as Boolean,
            preventStandby = params[16] as Boolean,
            isLiteModeEnabled = params[17] as Boolean,
            liveBufferSize = params[18] as Int,
            decoderMode = params[19] as String,
            playerEngineMode = params[20] as String,
            isCheckingForUpdates = params[21] as Boolean,
            latestVersion = params[22] as? String,
            updateStatus = params[23] as String,
            lastUpdateCheckTimestamp = params[24] as Long
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    val account = uiState.map { it.account }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun runSpeedTest() {
        viewModelScope.launch {
            _isSpeedTesting.value = true
            _speedTestSnapshot.value = null
            _speedTestError.value = null
            
            when (val result = speedTestRunner.run()) {
                is InternetSpeedTestResult.Success -> {
                    _speedTestSnapshot.value = result.snapshot
                }
                is InternetSpeedTestResult.Error -> {
                    _speedTestError.value = result.message
                }
            }
            _isSpeedTesting.value = false
        }
    }

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

    fun setLanguage(lang: String) {
        preferenceRepository.setLanguage(lang)
    }

    fun setSecureConnectionEnabled(enabled: Boolean) {
        preferenceRepository.setSecureConnectionEnabled(enabled)
    }

    fun setSubtitleStyle(style: PlayerSubtitleStyle) {
        preferenceRepository.setSubtitleStyle(style)
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        preferenceRepository.setAutoPlayNextEpisode(enabled)
    }

    fun setZapAutoRevert(enabled: Boolean) {
        preferenceRepository.setZapAutoRevert(enabled)
    }

    fun setPreventStandby(enabled: Boolean) {
        preferenceRepository.setPreventStandby(enabled)
    }

    fun setLiteModeEnabled(enabled: Boolean) {
        preferenceRepository.setLiteModeEnabled(enabled)
    }

    fun setLiveBufferSize(sizeMs: Int) {
        preferenceRepository.setLiveBufferSize(sizeMs)
    }

    fun setDecoderMode(mode: String) {
        preferenceRepository.setDecoderMode(mode)
    }

    fun setPlayerEngineMode(mode: String) {
        preferenceRepository.setPlayerEngineMode(mode)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _isCheckingForUpdates.value = true
            _updateStatus.value = "Checking..."

            val now = System.currentTimeMillis()
            preferenceRepository.setLastUpdateCheckTimestamp(now)
            _lastUpdateCheckTimestamp.value = now

            val config = configRepository.fetchConfig()
            val updateConfig = configRepository.checkForAppUpdate(config)

            if (updateConfig != null) {
                _updateStatus.value = "New update available: v${updateConfig.resolvedVersionName}"
                _latestVersion.value = updateConfig.resolvedVersionName
            } else {
                _updateStatus.value = "Up to date"
                _latestVersion.value = BuildConfig.VERSION_NAME
            }

            _isCheckingForUpdates.value = false
        }
    }

    fun updateStorageConfig(uri: Uri) {
        viewModelScope.launch {
            downloadManager.updateStorageConfig(uri.toString(), uri.path)
        }
    }

    fun updateAccount(account: XtreamAccount) {
        accountBox.put(account)
    }

    fun logout() {
        accountBox.removeAll()
    }

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 1. Clear OkHttp Cache
                    okHttpClient.cache?.evictAll()

                    // 2. Clear Coil (ImageLoader) Cache
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()

                    // 3. Clear EPG
                    epgBox.removeAll()

                    _userMessage.emit("Cache cleared successfully")
                } catch (e: Exception) {
                    _userMessage.emit("Failed to clear cache: ${e.message}")
                }
            }
        }
    }
}
