package com.eliteprime.app.ui.components

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
import com.eliteprime.app.player.GlobalPlayerState
import com.eliteprime.app.player.PlayerViewMode
import com.eliteprime.app.util.LocalRenderTier
import com.eliteprime.app.util.RenderTier

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

    // PERSISTENT MOUNT: We must stay in the composition as long as we have an active engine.
    // Early returning here (on HIDDEN mode) is what caused the surface unmount/lag/audio loop.
    if (engine == null) {
        return
    }

    val isFullScreen = mode == PlayerViewMode.FULLSCREEN
    val isHidden = mode == PlayerViewMode.HIDDEN
    
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

    val tier = LocalRenderTier.current
    val isReducedTier = tier == RenderTier.REDUCED

    val animScaleX by animateFloatAsState(
        targetValue = targetScaleX, 
        animationSpec = if (isReducedTier) snap() else tween(250),
        label = "playerScaleX"
    )
    val animScaleY by animateFloatAsState(
        targetValue = targetScaleY, 
        animationSpec = if (isReducedTier) snap() else tween(250),
        label = "playerScaleY"
    )
    val animTranslationX by animateFloatAsState(
        targetValue = targetTranslationX, 
        animationSpec = if (isReducedTier) snap() else tween(250),
        label = "playerTransX"
    )
    val animTranslationY by animateFloatAsState(
        targetValue = targetTranslationY, 
        animationSpec = if (isReducedTier) snap() else tween(250),
        label = "playerTransY"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { hostSize = it.size }
            .graphicsLayer {
                // FORCE TOP-LEFT PIVOT
                this.transformOrigin = TransformOrigin(0f, 0f)
                
                this.scaleX = animScaleX
                this.scaleY = animScaleY
                this.translationX = animTranslationX
                this.translationY = animTranslationY
                
                // Hide completely if mode is HIDDEN but engine is still held (rare)
                this.alpha = if (isHidden) 0f else 1f
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
                globalPlayerState.enterFullScreen(engine!!)
            }
    ) {
        PlayerRenderView(
            playerEngine = engine!!,
            globalPlayerState = globalPlayerState,
            isGlobalHost = true,
            resizeMode = resizeMode,
            surfaceType = surfaceType,
            modifier = Modifier.fillMaxSize()
        )
    }
}
