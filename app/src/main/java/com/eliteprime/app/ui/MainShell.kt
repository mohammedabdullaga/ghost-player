package com.eliteprime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eliteprime.app.ui.navigation.AppNavigation
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice

@Composable
fun MainShell(startDestination: String = "login") {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val isTv = context.isTelevisionDevice()

    val showBars = currentDestination?.route != "login" && currentDestination?.route != "player" && currentDestination?.route?.contains("player") == false

    AppBackground {
        if (isTv && showBars) {
            Row(modifier = Modifier.fillMaxSize()) {
                TvAdaptiveSidebar(navController)
                AppNavigation(navController = navController, startDestination = startDestination, modifier = Modifier.weight(1f))
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                AppNavigation(
                    navController = navController, 
                    startDestination = startDestination, 
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun TvAdaptiveSidebar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp),
        color = Color.Black.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SidebarIcon(Icons.Default.Home, "home", currentDestination?.hierarchy?.any { it.route == "home" } == true) {
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                    launchSingleTop = true
                }
            }
            SidebarIcon(Icons.Default.LiveTv, "live", currentDestination?.hierarchy?.any { it.route == "live" } == true) {
                navController.navigate("live") { launchSingleTop = true }
            }
            SidebarIcon(Icons.Default.Movie, "movies", currentDestination?.hierarchy?.any { it.route == "movies" } == true) {
                navController.navigate("movies") { launchSingleTop = true }
            }
            SidebarIcon(Icons.Default.VideoLibrary, "series", currentDestination?.hierarchy?.any { it.route == "series" } == true) {
                navController.navigate("series") { launchSingleTop = true }
            }
            SidebarIcon(Icons.Default.FileDownload, "downloads", currentDestination?.hierarchy?.any { it.route == "downloads" } == true) {
                navController.navigate("downloads") { launchSingleTop = true }
            }
            Spacer(modifier = Modifier.weight(1f))
            SidebarIcon(Icons.Default.Search, "search", currentDestination?.hierarchy?.any { it.route == "search" } == true) {
                navController.navigate("search") { launchSingleTop = true }
            }
            SidebarIcon(Icons.Default.Settings, "settings", currentDestination?.hierarchy?.any { it.route == "settings" } == true) {
                navController.navigate("settings") { launchSingleTop = true }
            }
        }
    }
}

@Composable
private fun SidebarIcon(icon: ImageVector, route: String, isSelected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(12.dp))
    ) {
        Icon(icon, contentDescription = route, tint = if (isSelected) AppColors.ArenaOrange else Color.White.copy(alpha = 0.6f))
    }
}
