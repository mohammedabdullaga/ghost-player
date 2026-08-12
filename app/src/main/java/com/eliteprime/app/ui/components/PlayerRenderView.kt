package com.eliteprime.app.ui.components

import android.os.Build
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
    // Observe global mode and player state to ensure binding happens as soon as player is ready
    val globalModeState = globalPlayerState?.mode?.collectAsStateWithLifecycle()
    val globalMode = globalModeState?.value ?: PlayerViewMode.HIDDEN
    
    val playbackState by playerEngine.playbackState.collectAsStateWithLifecycle()

    AndroidView(
        factory = { context ->
            playerEngine.createRenderView(context, resizeMode)
        },
        update = { view ->
            val shouldBind = if (isGlobalHost) {
                globalMode == PlayerViewMode.FULLSCREEN
            } else {
                globalMode != PlayerViewMode.FULLSCREEN
            }

            if (shouldBind) {
                playerEngine.bindRenderView(view, resizeMode)
                (view as? PlayerView)?.apply {
                    requestLayout()
                    if (Build.VERSION.SDK_INT >= 35) {
                        try {
                            setRequestedFrameRate(0f)
                        } catch (e: Exception) {}
                    }
                }
            } else {
                playerEngine.unbindRenderView(view)
            }
        },
        modifier = modifier
    )
}
