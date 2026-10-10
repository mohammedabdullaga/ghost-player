package com.ghost.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import android.util.Log
import androidx.compose.animation.core.snap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghost.tv.player.GlobalPlayerState
import com.ghost.tv.player.PlayerViewMode
import com.ghost.tv.util.LocalRenderTier
import com.ghost.tv.util.RenderTier

@Composable
fun GlobalPlayerHost(
    globalPlayerState: GlobalPlayerState,
    modifier: Modifier = Modifier
) {
    val mode by globalPlayerState.mode.collectAsStateWithLifecycle()
    val engine by globalPlayerState.activeEngine.collectAsStateWithLifecycle()
    val resizeMode by globalPlayerState.resizeMode.collectAsStateWithLifecycle()
    val previewBounds by globalPlayerState.previewBounds.collectAsStateWithLifecycle()
    val surfaceType by globalPlayerState.surfaceType.collectAsStateWithLifecycle()

    var hostSize by remember { mutableStateOf(IntSize.Zero) }

    val activeEngine = engine
    val isFullScreen = mode == PlayerViewMode.FULLSCREEN
    val isHidden = mode == PlayerViewMode.HIDDEN || activeEngine == null

    // Calculate target transform using graphicsLayer (scale + translation)
    // This allows the persistent PlayerView to stay full-screen in layout
    // but visually shrink/move to match the preview box.
    val targetScaleX = if (isFullScreen || isHidden || previewBounds == null || hostSize.width == 0) 1f 
                      else (previewBounds!!.width / hostSize.width.toFloat()).coerceAtLeast(0.01f)
    
    val targetScaleY = if (isFullScreen || isHidden || previewBounds == null || hostSize.height == 0) 1f 
                      else (previewBounds!!.height / hostSize.height.toFloat()).coerceAtLeast(0.01f)
                      
    val targetTranslationX = if (isFullScreen || isHidden || previewBounds == null) 0f 
                            else previewBounds!!.left
                            
    val targetTranslationY = if (isFullScreen || isHidden || previewBounds == null) 0f 
                            else previewBounds!!.top

    val isLite = LocalRenderTier.current == RenderTier.LITE
    val animationSpec = if (isLite) snap<Float>() else tween(250)

    val animScaleX by animateFloatAsState(targetScaleX, animationSpec, label = "playerScaleX")
    val animScaleY by animateFloatAsState(targetScaleY, animationSpec, label = "playerScaleY")
    val animTranslationX by animateFloatAsState(targetTranslationX, animationSpec, label = "playerTransX")
    val animTranslationY by animateFloatAsState(targetTranslationY, animationSpec, label = "playerTransY")

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { hostSize = it.size }
            .graphicsLayer {
                // FORCE TOP-LEFT PIVOT
                this.transformOrigin = TransformOrigin(0f, 0f)
                
                if (isHidden || hostSize.width == 0 || hostSize.height == 0) {
                    this.scaleX = 1f
                    this.scaleY = 1f
                    this.translationX = 0f
                    this.translationY = 0f
                    this.alpha = 0f
                } else {
                    this.scaleX = animScaleX
                    this.scaleY = animScaleY
                    this.translationX = animTranslationX
                    this.translationY = animTranslationY
                    this.alpha = 1f
                }
                this.clip = true
                
                // Final verify log
                if (animScaleX < 0.99f && !isHidden) {
                    Log.d("TransformDebug", "Applied: Scale=($scaleX, $scaleY) Trans=($translationX, $translationY) Origin=$transformOrigin")
                }
            }
            .background(if (isFullScreen) Color.Black else Color.Transparent)
            .clickable(
                enabled = !isFullScreen && !isHidden,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                activeEngine?.let { globalPlayerState.enterFullScreen(it) }
            }
    ) {
        if (activeEngine != null) {
            PlayerRenderView(
                playerEngine = activeEngine,
                globalPlayerState = globalPlayerState,
                isGlobalHost = true,
                resizeMode = resizeMode,
                surfaceType = surfaceType,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
