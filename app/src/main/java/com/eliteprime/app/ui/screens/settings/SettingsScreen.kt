package com.eliteprime.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.XtreamAccount
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import java.text.SimpleDateFormat
import java.util.*

private enum class SettingCategory(val label: String, val icon: ImageVector) {
    ACCOUNT("Account", Icons.Default.Person),
    GENERAL("General", Icons.Default.Settings),
    PLAYBACK("Playback", Icons.Default.PlayCircle),
    ABOUT("About", Icons.Default.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf(SettingCategory.ACCOUNT) }
    val isTv = LocalContext.current.isTelevisionDevice()
    
    var showEditDialog by remember { mutableStateOf(false) }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        titleContentColor = Color.White
                    ),
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                )
            }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- SIDEBAR ---
                Surface(
                    modifier = Modifier.weight(0.32f).fillMaxHeight(),
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SettingCategory.entries.forEach { category ->
                            SidebarItem(
                                label = category.label,
                                icon = category.icon,
                                isSelected = selectedCategory == category,
                                isTv = isTv,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }

                // --- CONTENT ---
                Surface(
                    modifier = Modifier.weight(0.68f).fillMaxHeight(),
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(selectedCategory.icon, contentDescription = null, tint = AppColors.ArenaOrange, modifier = Modifier.size(18.dp))
                                Text(
                                    text = selectedCategory.label.uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                            
                            if (selectedCategory == SettingCategory.ACCOUNT && uiState.isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.ArenaOrange, strokeWidth = 2.dp)
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                        when (selectedCategory) {
                            SettingCategory.ACCOUNT -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    item {
                                        uiState.account?.let { account ->
                                            AccountSettingsCard(
                                                account = account,
                                                liveCount = uiState.liveCount.toInt(),
                                                movieCount = uiState.movieCount.toInt(),
                                                seriesCount = uiState.seriesCount.toInt(),
                                                epgCount = uiState.epgCount.toInt(),
                                                isSyncing = uiState.isSyncing,
                                                onRefresh = { viewModel.syncAll() },
                                                onEdit = { showEditDialog = true },
                                                isTv = isTv
                                            )
                                        }
                                    }
                                    
                                    item {
                                        LogoutButton(
                                            isTv = isTv,
                                            onLogout = {
                                                viewModel.logout()
                                                onLogout()
                                            }
                                        )
                                    }
                                }
                            }
                            else -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Coming Soon", color = Color.White.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showEditDialog && uiState.account != null) {
        EditAccountDialog(
            account = uiState.account!!,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                viewModel.updateAccount(updated)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isTv: Boolean,
    onClick: () -> Unit
) {
    if (isTv) {
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.FocusBrown.copy(alpha = 0.8f) else Color.Transparent,
                focusedContainerColor = AppColors.FocusBrown
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            SidebarItemContent(label, icon, isSelected || isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.12f) else Color.Transparent,
            shape = RoundedCornerShape(12.dp)
        ) {
            SidebarItemContent(label, icon, isSelected)
        }
    }
}

@Composable
private fun SidebarItemContent(label: String, icon: ImageVector, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(if (isActive) AppColors.ArenaOrange else Color.Transparent, RoundedCornerShape(50))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AccountSettingsCard(
    account: XtreamAccount,
    liveCount: Int,
    movieCount: Int,
    seriesCount: Int,
    epgCount: Int,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    isTv: Boolean
) {
    val cardBackground = Brush.linearGradient(
        colors = listOf(Color(0xFF1A1C1E), Color(0xFF0D0E10))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBackground)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
    ) {
        // Glowing corner accent
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(240.dp)
                .offset(x = 100.dp, y = (-100).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AppColors.ArenaOrange.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = account.name.ifBlank { "ArenaLive Account" },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    
                    val expirationDate = account.expiryDate?.let {
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
                    } ?: "Unknown"
                    
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Event, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(14.dp))
                            Text("EXPIRES ON: $expirationDate", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                StatusPill(label = "ACTIVE", containerColor = AppColors.ArenaOrange)
            }

            // 2. Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SubscriptionMetricCard(
                    title = "Live TV",
                    count = liveCount,
                    icon = Icons.Default.PlayArrow,
                    accentColor = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                SubscriptionMetricCard(
                    title = "Movies",
                    count = movieCount,
                    icon = Icons.Default.Movie,
                    accentColor = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
                SubscriptionMetricCard(
                    title = "Series",
                    count = seriesCount,
                    icon = Icons.Default.Tv,
                    accentColor = Color(0xFFE91E63),
                    modifier = Modifier.weight(1f)
                )
                SubscriptionMetricCard(
                    title = "EPG",
                    count = epgCount,
                    icon = Icons.Default.Book,
                    accentColor = Color(0xFFFFC107),
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsActionButton(
                    label = if (isSyncing) "Syncing..." else "Refresh Data",
                    onClick = onRefresh,
                    isPrimary = true,
                    isTv = isTv,
                    modifier = Modifier.weight(1.5f)
                )
                SettingsActionButton(
                    label = "Edit Account",
                    onClick = onEdit,
                    isTv = isTv,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SubscriptionMetricCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Column {
                Text(
                    text = String.format(Locale.getDefault(), "%,d", count),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title.uppercase(),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isTv: Boolean
) {
    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier.height(42.dp),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isPrimary) AppColors.ArenaOrange else Color.White.copy(alpha = 0.08f),
                focusedContainerColor = if (isPrimary) Color.White else AppColors.FocusBrown.copy(alpha = 0.8f)
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = label.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(42.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPrimary) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LogoutButton(isTv: Boolean, onLogout: () -> Unit) {
    val logoutColor = Color(0xFFEF5350)
    if (isTv) {
        TvSurface(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = logoutColor.copy(alpha = 0.08f),
                focusedContainerColor = logoutColor.copy(alpha = 0.8f)
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
        ) {
            LogoutButtonContent(logoutColor)
        }
    } else {
        Surface(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            color = logoutColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, logoutColor.copy(alpha = 0.25f))
        ) {
            LogoutButtonContent(logoutColor)
        }
    }
}

@Composable
private fun LogoutButtonContent(color: Color) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Column {
            Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Sign out and clear all local data", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun EditAccountDialog(
    account: XtreamAccount,
    onDismiss: () -> Unit,
    onSave: (XtreamAccount) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var serverUrl by remember { mutableStateOf(account.serverUrl) }
    var username by remember { mutableStateOf(account.username) }
    var password by remember { mutableStateOf(account.password) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(account.copy(name = name, serverUrl = serverUrl, username = username, password = password))
            }) {
                Text("SAVE", color = AppColors.ArenaOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1C1E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
