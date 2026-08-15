package com.eliteprime.app.ui.tv

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eliteprime.app.player.GlobalPlayerState
import com.eliteprime.app.player.PlayerEngine
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.GlobalPlayerHost
import com.eliteprime.app.ui.tv.navigation.TvAppNavigation
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface

@Composable
fun TvMainShell(
    playerEngine: PlayerEngine,
    globalPlayerState: GlobalPlayerState,
    startDestination: String
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""

    // TvSideNavRail is the single navigation rail for the whole app.
    // "home" and "downloads" are now included here — home previously had
    // its own separate rail built directly into TvHomeScreen, which
    // produced two different-looking nav rails depending on which screen
    // you were on. "downloads" was previously missing from this list
    // entirely, so navigating there showed no rail at all.
    val showSidebar = currentRoute in listOf("home", "live", "movies", "series", "downloads", "search", "settings")

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Static Background (Bottom Layer)
        AppBackground(modifier = Modifier.fillMaxSize()) { }

        // 2. Global Player Layer (Middle Layer)
        GlobalPlayerHost(
            globalPlayerState = globalPlayerState,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Navigation Content + Sidebar
        Row(modifier = Modifier.fillMaxSize()) {
            if (showSidebar) {
                TvSideNavRail(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (currentRoute != route) {
                            // STOP PLAYER ON SECTION CHANGE
                            globalPlayerState.hide()
                            playerEngine.stop()

                            navController.navigate(route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TvAppNavigation(
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

@Composable
private fun TvSideNavRail(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val railWidth = if (isExpanded) 240.dp else 80.dp
    val railBackground = Brush.horizontalGradient(
        colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(railBackground)
            .onFocusChanged { isExpanded = it.hasFocus }
            .animateContentSize()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo or Icon at top
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFF8C00), modifier = Modifier.size(28.dp))
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Nav Items
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
        ) {
            TvNavItem(Icons.Default.Home, "Home", "home", currentRoute, isExpanded) { onNavigate("home") }
            TvNavItem(Icons.Default.LiveTv, "Live TV", "live", currentRoute, isExpanded) { onNavigate("live") }
            TvNavItem(Icons.Default.Movie, "Movies", "movies", currentRoute, isExpanded) { onNavigate("movies") }
            TvNavItem(Icons.Default.Tv, "Series", "series", currentRoute, isExpanded) { onNavigate("series") }
            TvNavItem(Icons.Default.FileDownload, "Downloads", "downloads", currentRoute, isExpanded) { onNavigate("downloads") }
            TvNavItem(Icons.Default.Search, "Search", "search", currentRoute, isExpanded) { onNavigate("search") }
        }

        TvNavItem(Icons.Default.Settings, "Settings", "settings", currentRoute, isExpanded) { onNavigate("settings") }
    }
}

@Composable
private fun TvNavItem(
    icon: ImageVector,
    label: String,
    route: String,
    currentRoute: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val isSelected = currentRoute == route
    var isFocused by remember { mutableStateOf(false) }

    TvSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isExpanded) 16.dp else 12.dp)
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.Black else if (isSelected) Color(0xFFFF8C00) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = label,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
        }
    }
}