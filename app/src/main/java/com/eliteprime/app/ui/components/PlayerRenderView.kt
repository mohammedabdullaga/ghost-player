package com.eliteprime.app.ui.components

import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.aspectRatio
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
    modifier: Modifier = Modifier
) {
    // Log recomposition reasons
    SideEffect {
        Log.v("PlayerBinding", "Recomposing PlayerRenderView: isGlobalHost=$isGlobalHost | resizeMode=$resizeMode")
    }

    // Observe global mode and player state to ensure binding happens as soon as player is ready
    val globalModeState = globalPlayerState?.mode?.collectAsStateWithLifecycle()
    val globalMode = globalModeState?.value ?: PlayerViewMode.HIDDEN
    
    val videoFormat by playerEngine.videoFormat.collectAsStateWithLifecycle()
    
    // PRE-SIZE: Apply aspect ratio before AndroidView is measured if we know it
    // This helps avoid the internal PlayerView.updateAspectRatio triggering a second layout pass
    val aspectModifier = if (videoFormat.width > 0 && videoFormat.height > 0 && resizeMode == PlayerSurfaceResizeMode.FIT) {
        val aspect = (videoFormat.width.toFloat() / videoFormat.height)
        Modifier.aspectRatio(aspect)
    } else {
        Modifier
    }

    AndroidView(
        factory = { context ->
            Log.d("PlayerBinding", "AndroidView.factory: isGlobalHost=$isGlobalHost")
            playerEngine.createRenderView(context, resizeMode).apply {
                if (Build.VERSION.SDK_INT >= 35) {
                    try {
                        (this as? PlayerView)?.setRequestedFrameRate(0f)
                    } catch (e: Exception) {}
                }
            }
        },
        update = { view ->
            val shouldBind = if (isGlobalHost) {
                globalMode == PlayerViewMode.FULLSCREEN
            } else {
                globalMode != PlayerViewMode.FULLSCREEN
            }

            Log.d("PlayerBinding", "AndroidView.update: isGlobalHost=$isGlobalHost | globalMode=$globalMode | shouldBind=$shouldBind | view=${view.hashCode()}")

            if (shouldBind) {
                playerEngine.bindRenderView(view, resizeMode)
            } else {
                playerEngine.unbindRenderView(view)
            }
        },
        modifier = modifier.then(aspectModifier)
    )
}
