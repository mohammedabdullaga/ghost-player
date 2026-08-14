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

    // STABILITY FIX: Default to SURFACE_VIEW for TV hardware to avoid slow-motion
    private val _surfaceType = MutableStateFlow(PlayerRenderSurfaceType.SURFACE_VIEW)
    val surfaceType: StateFlow<PlayerRenderSurfaceType> = _surfaceType.asStateFlow()

    fun setSurfaceType(type: PlayerRenderSurfaceType) {
        if (_surfaceType.value != type) {
            Log.i("GlobalPlayerState", "Switching surface type to $type")
            _surfaceType.value = type
        }
    }

    fun updateSurfaceTypeForResolution(width: Int, height: Int) {
        // Only trigger swap on zap, not mid-stream to prevent freezes
        val isUhd = width >= 3000 || height >= 2000
        val targetType = if (isUhd) PlayerRenderSurfaceType.SURFACE_VIEW else PlayerRenderSurfaceType.TEXTURE_VIEW
        
        // Disable mid-stream surface swap as it causes Amlogic freezes
        /*
        if (_surfaceType.value != targetType) {
            Log.i("GlobalPlayerState", "Requesting surface swap to $targetType")
            _surfaceType.value = targetType
        }
        */
    }

    fun enterFullScreen(engine: PlayerEngine) {
        Log.d("GlobalPlayerState", "Entering FULLSCREEN mode")
        _activeEngine.value = engine
        _mode.value = PlayerViewMode.FULLSCREEN
    }

    fun enterPreview(engine: PlayerEngine) {
        Log.d("GlobalPlayerState", "Entering PREVIEW mode")
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
