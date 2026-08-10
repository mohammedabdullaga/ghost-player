package com.eliteprime.app.ui.components

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
    // Observe global mode to trigger re-binding when handoff happens
    val globalModeState = globalPlayerState?.mode?.collectAsStateWithLifecycle()
    val globalMode = globalModeState?.value ?: PlayerViewMode.HIDDEN

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
                    // Force a layout pass if the resize mode changed
                    requestLayout()
                }
            }
        },
        modifier = modifier
    )
}
