package com.eliteprime.app.ui.components

import android.os.Build
import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.eliteprime.app.player.*

@Composable
fun PlayerRenderView(
    playerEngine: PlayerEngine,
    globalPlayerState: GlobalPlayerState? = null,
    isGlobalHost: Boolean = false,
    resizeMode: PlayerSurfaceResizeMode = PlayerSurfaceResizeMode.FIT,
    surfaceType: PlayerRenderSurfaceType = PlayerRenderSurfaceType.SURFACE_VIEW,
    modifier: Modifier = Modifier
) {
    // Log recomposition reasons
    SideEffect {
        Log.v("PlayerBinding", "Recomposing PlayerRenderView: isGlobalHost=$isGlobalHost | resizeMode=$resizeMode | surfaceType=$surfaceType")
    }

    // Observe global mode and player state to ensure binding happens as soon as player is ready
    val globalModeState = globalPlayerState?.mode?.collectAsStateWithLifecycle()
    val globalMode = globalModeState?.value ?: PlayerViewMode.HIDDEN
    
    val videoFormat by playerEngine.videoFormat.collectAsStateWithLifecycle()
    
    // PRE-SIZE: Apply aspect ratio before AndroidView is measured if we know it.
    // PERFORMANCE: We ONLY apply this for non-global views (e.g. if we ever go back to embedded).
    // For the Global Host, we want it to stay fillMaxSize so the transform math is simple.
    val aspectModifier = if (!isGlobalHost && videoFormat.width > 0 && videoFormat.height > 0 && resizeMode == PlayerSurfaceResizeMode.FIT) {
        val aspect = (videoFormat.width.toFloat() / videoFormat.height)
        Modifier.aspectRatio(aspect)
    } else {
        Modifier
    }

    // PERFORMANCE FIX: Apply aspect ratio directly to the AndroidView and remove wrapper Box.
    // This reduces layout passes which can cause decoder drops on Amlogic SOCs.
    AndroidView(
        factory = { context ->
            Log.d("PlayerBinding", "AndroidView.factory: isGlobalHost=$isGlobalHost | surfaceType=$surfaceType")
            playerEngine.createRenderView(context, resizeMode, surfaceType).apply {
                if (Build.VERSION.SDK_INT >= 35) {
                    try {
                        (this as? PlayerView)?.setRequestedFrameRate(0f)
                    } catch (e: Exception) {}
                }
            }
        },
        update = { view ->
            val useTransform = globalPlayerState?.useTransformHandoff?.value ?: false
            
            val shouldBind = if (isGlobalHost) {
                // If using transform, GlobalHost handles BOTH Fullscreen and Preview
                if (useTransform) {
                    globalMode == PlayerViewMode.FULLSCREEN || globalMode == PlayerViewMode.PREVIEW
                } else {
                    globalMode == PlayerViewMode.FULLSCREEN
                }
            } else {
                // If using transform, Embedded views NEVER bind (they are just "holes" or reporting bounds)
                if (useTransform) {
                    false
                } else {
                    globalMode != PlayerViewMode.FULLSCREEN
                }
            }

            Log.d("PlayerBinding", "AndroidView.update: isGlobalHost=$isGlobalHost | mode=$globalMode | useTransform=$useTransform | shouldBind=$shouldBind")

            if (shouldBind) {
                playerEngine.bindRenderView(view, resizeMode)
            } else {
                playerEngine.unbindRenderView(view)
            }
        },
        modifier = modifier.then(aspectModifier).focusable(false)
    )
}
