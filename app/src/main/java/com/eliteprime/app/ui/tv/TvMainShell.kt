package com.eliteprime.app.ui.tv

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.ui.tv.core.LocalTvFocusManager
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.R
import com.eliteprime.app.ui.components.ArenaLiveLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvMainShell(
    playerEngine: PlayerEngine,
    globalPlayerState: GlobalPlayerState,
    startDestination: String
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""
    
    // Screens that should have the Navbar available
    val navBarRoutes = listOf(
        "home", "live", "movies", "series", "search", "settings", 
        "trending", "continue_watching", "downloads",
        "movie_details/{id}", "series_details/{id}"
    )
    val showNavBar = currentRoute in navBarRoutes

    // Screens that use the Cinematic (Collapsible) Navbar
    val isCinematicRoute = currentRoute in listOf("movies", "series", "movie_details/{id}", "series_details/{id}")
    
    // Advanced UI state: Collapsible Navbar control
    var isNavBarCollapsed by remember(currentRoute) { mutableStateOf(isCinematicRoute) }
    // Tracks whether focus is currently (or was just) inside the navbar, so we only
    // collapse on a real focus *exit*, not when the navbar first appears.
    var navBarHadFocus by remember(currentRoute) { mutableStateOf(false) }

    // Global focus management
    val isOverlayActive = remember { mutableStateOf(false) }
    val navHomeFocusRequester = remember { FocusRequester() }
    val navSettingsFocusRequester = remember { FocusRequester() }
    val arrowFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentRoute) {
        if (currentRoute == "home" || currentRoute == "login") {
            delay(800)
            try { navHomeFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    CompositionLocalProvider(LocalTvFocusManager provides isOverlayActive) {
        ElitPrimeTvTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                AppBackground(modifier = Modifier.fillMaxSize()) { }

                GlobalPlayerHost(
                    globalPlayerState = globalPlayerState,
                    modifier = Modifier.fillMaxSize()
                )

                // 1. BASE SCREEN FLOW
                Column(modifier = Modifier.fillMaxSize()) {
                    // STATIC NAVBAR (Only for HOME, LIVE, SEARCH, etc.)
                    if (showNavBar && !isCinematicRoute) {
                        TvTopNavBar(
                            currentRoute = currentRoute,
                            navHomeFocusRequester = navHomeFocusRequester,
                            navSettingsFocusRequester = navSettingsFocusRequester,
                            modifier = Modifier.focusProperties { canFocus = !isOverlayActive.value },
                            onNavigate = { route ->
                                if (currentRoute != route) {
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

                    // MAIN SCREEN CONTENT
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        TvAppNavigation(
                            navController = navController,
                            playerEngine = playerEngine,
                            globalPlayerState = globalPlayerState,
                            startDestination = startDestination,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 2. FLOATING UI (Movies, Series, Details - Overlays everything)
                if (showNavBar && isCinematicRoute) {
                    // FULL FLOATING NAVBAR
                    AnimatedVisibility(
                        visible = !isNavBarCollapsed,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                    ) {
                        TvTopNavBar(
                            currentRoute = currentRoute,
                            navHomeFocusRequester = navHomeFocusRequester,
                            navSettingsFocusRequester = navSettingsFocusRequester,
                            modifier = Modifier
                                .focusProperties { canFocus = !isOverlayActive.value }
                                .onFocusChanged { 
                                    // onFocusChanged observes the focusGroup() node below, whose
                                    // `isFocused` is always false. `hasFocus` is true while any
                                    // child is focused, so use it to detect a true exit.
                                    if (it.hasFocus) {
                                        navBarHadFocus = true
                                    } else if (navBarHadFocus) {
                                        isNavBarCollapsed = true
                                        navBarHadFocus = false
                                    }
                                }
                                .focusGroup(),
                            onNavigate = { route ->
                                if (currentRoute != route) {
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

                    // FLOATING ARROW AFFORDANCE
                    AnimatedVisibility(
                        visible = isNavBarCollapsed,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        TvNavArrowAffordance(
                            focusRequester = arrowFocusRequester,
                            onClick = { 
                                isNavBarCollapsed = false
                                // High-intensity focus grabber
                                scope.launch {
                                    repeat(5) {
                                        delay(100)
                                        try {
                                            navHomeFocusRequester.requestFocus()
                                            return@launch 
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvNavArrowAffordance(
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arrow")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    var isFocused by remember { mutableStateOf(false) }

    TvSurface(
        onClick = onClick,
        modifier = Modifier
            .padding(top = 4.dp)
            .size(48.dp, 28.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { translationY = bounce },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Show Menu",
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TvTopNavBar(
    currentRoute: String,
    navHomeFocusRequester: FocusRequester,
    navSettingsFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit
) {
    val navBackground = Brush.verticalGradient(
        colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(navBackground)
            .padding(horizontal = 48.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            ArenaLiveLogo(textSize = 20.sp)
        }
        
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvTopNavItem("Home", Icons.Default.Home, currentRoute == "home", modifier = Modifier.focusRequester(navHomeFocusRequester)) { onNavigate("home") }
            TvTopNavItem("Live TV", Icons.Default.LiveTv, currentRoute == "live") { onNavigate("live") }
            TvTopNavItem("Movies", Icons.Default.Movie, currentRoute == "movies") { onNavigate("movies") }
            TvTopNavItem("Series", Icons.Default.VideoLibrary, currentRoute == "series") { onNavigate("series") }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvTopNavIcon(Icons.Default.Search, currentRoute == "search") { onNavigate("search") }
            TvTopNavIcon(Icons.Default.Settings, currentRoute == "settings", modifier = Modifier.focusRequester(navSettingsFocusRequester)) { onNavigate("settings") }
        }
    }
}

@Composable
private fun TvTopNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    TvSurfaceCard(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .height(36.dp)
            .wrapContentWidth(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label.uppercase(),
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun TvTopNavIcon(
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    TvSurfaceCard(
        onClick = onClick,
        modifier = modifier
            .size(36.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.Center)
        )
    }
}
