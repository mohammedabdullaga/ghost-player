package com.eliteprime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.eliteprime.app.ui.tv.dpadFocus

@Composable
fun TvSidebar(
    selectedRoute: String?,
    onNavigate: (String) -> Unit
) {
    val focusRequesters = remember { List(6) { FocusRequester() } }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SidebarItem(Icons.Default.Home, "home", selectedRoute == "home", focusRequesters[0], onNavigate)
        SidebarItem(Icons.Default.LiveTv, "live", selectedRoute == "live", focusRequesters[1], onNavigate)
        SidebarItem(Icons.Default.Movie, "movies", selectedRoute == "movies", focusRequesters[2], onNavigate)
        SidebarItem(Icons.Default.Tv, "series", selectedRoute == "series", focusRequesters[3], onNavigate)
        SidebarItem(Icons.Default.Search, "search", selectedRoute == "search", focusRequesters[4], onNavigate)
        
        Spacer(modifier = Modifier.weight(1f))
        
        SidebarItem(Icons.Default.Settings, "settings", selectedRoute == "settings", focusRequesters[5], onNavigate)
    }
}

@Composable
fun SidebarItem(
    icon: ImageVector,
    route: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    IconButton(
        onClick = { onClick(route) },
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .dpadFocus(focusRequester),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isSelected || isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
        )
    ) {
        Icon(
            icon, 
            contentDescription = null, 
            tint = if (isSelected || isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}
