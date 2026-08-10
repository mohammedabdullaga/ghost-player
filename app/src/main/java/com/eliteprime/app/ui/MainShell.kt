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

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Global Player Host drawn behind navigation
            GlobalPlayerHost(
                globalPlayerState = globalPlayerState,
                modifier = Modifier.fillMaxSize()
            )

            // Uniform structure for both Mobile and TV
            Box(modifier = Modifier.fillMaxSize()) {
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
}
