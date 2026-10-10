package com.ghost.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ghost.tv.player.GlobalPlayerState
import com.ghost.tv.player.PlayerEngine
import com.ghost.tv.ui.navigation.AppNavigation
import com.ghost.tv.ui.components.AppBackground
import com.ghost.tv.ui.components.GlobalPlayerHost

@Composable
fun MainShell(
    playerEngine: PlayerEngine,
    globalPlayerState: GlobalPlayerState,
    startDestination: String = "login"
) {
    val navController = rememberNavController()

    AppBackground(isTransparent = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Global Player Host (Bottom)
            // Sitting on top of background image but behind navigation
            GlobalPlayerHost(
                globalPlayerState = globalPlayerState,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Navigation Layer (Top)
            AppNavigation(
                navController = navController,
                playerEngine = playerEngine,
                globalPlayerState = globalPlayerState,
                startDestination = startDestination, 
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
