package com.eliteprime.app.player

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlayerViewMode {
    HIDDEN, FULLSCREEN
}

@Singleton
class GlobalPlayerState @Inject constructor() {

    private val _mode = MutableStateFlow(PlayerViewMode.HIDDEN)
    val mode: StateFlow<PlayerViewMode> = _mode.asStateFlow()

    private val _activeEngine = MutableStateFlow<PlayerEngine?>(null)
    val activeEngine: StateFlow<PlayerEngine?> = _activeEngine.asStateFlow()

    private val _resizeMode = MutableStateFlow(PlayerSurfaceResizeMode.FIT)
    val resizeMode: StateFlow<PlayerSurfaceResizeMode> = _resizeMode.asStateFlow()

    fun enterFullScreen(engine: PlayerEngine) {
        Log.d("GlobalPlayerState", "Entering FULLSCREEN mode")
        _activeEngine.value = engine
        _mode.value = PlayerViewMode.FULLSCREEN
    }

    fun updateResizeMode(mode: PlayerSurfaceResizeMode) {
        Log.d("GlobalPlayerState", "Updating resize mode to $mode")
        _resizeMode.value = mode
    }

    fun hide() {
        Log.d("GlobalPlayerState", "Hiding global player (returning to preview)")
        _mode.value = PlayerViewMode.HIDDEN
        _activeEngine.value = null
    }
}
