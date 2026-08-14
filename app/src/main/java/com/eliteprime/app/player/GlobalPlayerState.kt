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
    private val _surfaceType = MutableStateFlow(PlayerRenderSurfaceType.TEXTURE_VIEW)
    val surfaceType: StateFlow<PlayerRenderSurfaceType> = _surfaceType.asStateFlow()

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
