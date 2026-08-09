package com.eliteprime.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.eliteprime.app.player.PlayerEngine

@Composable
fun PlayerRenderView(
    playerEngine: PlayerEngine,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            playerEngine.createRenderView(context)
        },
        modifier = modifier
    )
}
