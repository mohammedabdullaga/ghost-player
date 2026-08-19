package com.eliteprime.app.player

import android.util.Log
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlayerViewMode {
    HIDDEN, FULLSCREEN, PREVIEW
}

@Singleton
class GlobalPlayerState @Inject constructor() {

    private val _mode = MutableStateFlow(PlayerViewMode.HIDDEN)
    val mode: StateFlow<PlayerViewMode> = _mode.asStateFlow()

    private val _activeEngine = MutableStateFlow<PlayerEngine?>(null)
    val activeEngine: StateFlow<PlayerEngine?> = _activeEngine.asStateFlow()

    private val _resizeMode = MutableStateFlow(PlayerSurfaceResizeMode.FIT)
    val resizeMode: StateFlow<PlayerSurfaceResizeMode> = _resizeMode.asStateFlow()

    // Target bounds for preview mode
    private val _previewBounds = MutableStateFlow<Rect?>(null)
    val previewBounds: StateFlow<Rect?> = _previewBounds.asStateFlow()

    private val _useTransformHandoff = MutableStateFlow(true) 
    val useTransformHandoff: StateFlow<Boolean> = _useTransformHandoff.asStateFlow()

    // TV & 4K STABILITY FIX: SurfaceView is always required for hardware decoding on Android TV.
    // TextureView causes software rendering / memory-bandwidth throttling on 4K HEVC streams.
    private val _surfaceType = MutableStateFlow(PlayerRenderSurfaceType.SURFACE_VIEW)
    val surfaceType: StateFlow<PlayerRenderSurfaceType> = _surfaceType.asStateFlow()

    fun setSurfaceType(type: PlayerRenderSurfaceType) {
        if (_surfaceType.value != type) {
            Log.i("GlobalPlayerState", "Switching surface type to $type")
            _surfaceType.value = type
        }
    }

    fun updateSurfaceTypeForResolution(width: Int, height: Int) {
        // Reserved for future resolution-based logic
    }

    private fun updateSurfaceForMode(mode: PlayerViewMode) {
        // Always maintain SURFACE_VIEW to keep zero-copy direct hardware overlays active
        setSurfaceType(PlayerRenderSurfaceType.SURFACE_VIEW)
    }

    fun enterFullScreen(engine: PlayerEngine) {
        Log.d("GlobalPlayerState", "Entering FULLSCREEN mode")
        updateSurfaceForMode(PlayerViewMode.FULLSCREEN)
        _activeEngine.value = engine
        _mode.value = PlayerViewMode.FULLSCREEN
    }

    fun enterPreview(engine: PlayerEngine) {
        Log.d("GlobalPlayerState", "Entering PREVIEW mode")
        updateSurfaceForMode(PlayerViewMode.PREVIEW)
        _activeEngine.value = engine
        _mode.value = PlayerViewMode.PREVIEW
    }

    fun updatePreviewBounds(bounds: Rect?) {
        if (bounds != _previewBounds.value) {
            Log.d("TransformDebug", "Preview bounds updated: $bounds")
            _previewBounds.value = bounds
        }
    }

    fun updateResizeMode(mode: PlayerSurfaceResizeMode) {
        Log.d("GlobalPlayerState", "Updating resize mode to $mode")
        _resizeMode.value = mode
    }

    fun hide() {
        Log.d("GlobalPlayerState", "Hiding global player")
        _mode.value = PlayerViewMode.HIDDEN
        _activeEngine.value = null
        _previewBounds.value = null
    }
}
