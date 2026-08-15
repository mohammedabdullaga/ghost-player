package com.eliteprime.app.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.eliteprime.app.player.GlobalPlayerState
import com.eliteprime.app.player.PlayerEngine
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.GlobalPlayerHost
import com.eliteprime.app.ui.tv.navigation.TvAppNavigation

@Composable
fun TvMainShell(
    playerEngine: PlayerEngine,
    globalPlayerState: GlobalPlayerState,
    startDestination: String
) {
    val navController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Static Background (Bottom Layer)
        // This provides the app's look and feel globally.
        AppBackground(modifier = Modifier.fillMaxSize()) { }

        // 2. Global Player Layer (Middle Layer)
        // For TV/SurfaceView, this sits on top of the static background.
        // SurfaceView will punch through the background to the window.
        GlobalPlayerHost(
            globalPlayerState = globalPlayerState,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Navigation Layer (Top Layer)
        // Screens here use AppBackground(isTransparent = true) to let video show through.
        TvAppNavigation(
            navController = navController,
            playerEngine = playerEngine,
            globalPlayerState = globalPlayerState,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        )
    }
}
