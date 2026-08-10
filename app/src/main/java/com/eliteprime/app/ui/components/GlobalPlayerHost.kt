package com.eliteprime.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliteprime.app.player.GlobalPlayerState
import com.eliteprime.app.player.PlayerViewMode

@Composable
fun GlobalPlayerHost(
    globalPlayerState: GlobalPlayerState,
    modifier: Modifier = Modifier
) {
    val mode by globalPlayerState.mode.collectAsStateWithLifecycle()
    val engine by globalPlayerState.activeEngine.collectAsStateWithLifecycle()
    val resizeMode by globalPlayerState.resizeMode.collectAsStateWithLifecycle()

    // LIGHTWEIGHT optimization: Completely remove from composition when hidden
    if (mode == PlayerViewMode.HIDDEN || engine == null) {
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PlayerRenderView(
            playerEngine = engine!!,
            globalPlayerState = globalPlayerState,
            isGlobalHost = true,
            resizeMode = resizeMode,
            modifier = Modifier.fillMaxSize()
        )
    }
}
