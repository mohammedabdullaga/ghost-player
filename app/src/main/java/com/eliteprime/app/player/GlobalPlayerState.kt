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

    // POC: Target bounds for preview mode
    private val _previewBounds = MutableStateFlow<Rect?>(null)
    val previewBounds: StateFlow<Rect?> = _previewBounds.asStateFlow()

    // POC: Toggle for transform-based handoff vs old switchTargetView
    private val _useTransformHandoff = MutableStateFlow(true) 
    val useTransformHandoff: StateFlow<Boolean> = _useTransformHandoff.asStateFlow()

    // POC: Force TextureView for POC validation (SurfaceView doesn't transform well)
    // OPTIMIZATION: We will auto-fallback to SURFACE_VIEW for 4K to save memory
    private val _surfaceType = MutableStateFlow(PlayerRenderSurfaceType.TEXTURE_VIEW)
    val surfaceType: StateFlow<PlayerRenderSurfaceType> = _surfaceType.asStateFlow()

    fun updateSurfaceTypeForResolution(width: Int, height: Int) {
        // AM-LOGIC 4K MANDATORY RULE: Any resolution > FHD REQUIRES SurfaceView 
        // to engage the hardware overlay scaler. TextureView will cause slow-motion.
        val isHighRes = width > 1920 || height > 1080
        val targetType = if (isHighRes) PlayerRenderSurfaceType.SURFACE_VIEW else PlayerRenderSurfaceType.TEXTURE_VIEW
        
        if (_surfaceType.value != targetType) {
            Log.i("GlobalPlayerState", "MANDATORY SURFACE SWAP: Switching to $targetType for ${width}x${height} UHD content")
            _surfaceType.value = targetType
        }
    }

    fun setSurfaceType(type: PlayerRenderSurfaceType) {
        _surfaceType.value = type
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
        Log.d("GlobalPlayerState", "Hiding global player (returning to preview)")
        _mode.value = PlayerViewMode.HIDDEN
        _activeEngine.value = null
        _previewBounds.value = null
    }
}
