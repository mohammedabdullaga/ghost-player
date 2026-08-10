package com.eliteprime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Global Player Host UNDER the NavHost
            GlobalPlayerHost(
                globalPlayerState = globalPlayerState,
                modifier = Modifier.fillMaxSize()
            )

            // Uniform structure for both Mobile and TV
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                AppNavigation(
                    navController = navController,
                    playerEngine = playerEngine,
                    globalPlayerState = globalPlayerState,
                    startDestination = startDestination, 
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
