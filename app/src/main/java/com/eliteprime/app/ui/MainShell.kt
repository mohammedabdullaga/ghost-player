package com.eliteprime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.eliteprime.app.player.GlobalPlayerState
import com.eliteprime.app.player.PlayerEngine
import com.eliteprime.app.ui.navigation.AppNavigation
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.GlobalPlayerHost

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
