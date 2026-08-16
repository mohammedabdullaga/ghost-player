package com.eliteprime.app.ui.tv.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import com.eliteprime.app.R
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.settings.SettingsViewModel
import com.eliteprime.app.ui.tv.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SettingTab(val labelRes: Int, val icon: ImageVector) {
    ACCOUNT(R.string.settings_account, Icons.Default.Person),
    PLAYBACK(R.string.settings_playback, Icons.Default.PlayCircle),
    NETWORK(R.string.settings_network, Icons.Default.NetworkCheck),
    ABOUT(R.string.settings_about, Icons.Default.Info),
}

@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(SettingTab.ACCOUNT) }
    val scope = rememberCoroutineScope()
    val firstTabRequester = remember { FocusRequester() }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        delay(200)
        firstTabRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 24.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.nav_settings).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                
                // --- LEFT: TABS (Glass Sidebar) ---
                TvGlassPane(modifier = Modifier.weight(0.3f)) {
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                        Text(text = "CATEGORIES", color = TvColors.TextTertiary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp)
                    }
                    HorizontalDivider(color = TvColors.GlassBorder)

                    SettingTab.entries.forEachIndexed { index, tab ->
                        TvNavItem(
                            label = stringResource(tab.labelRes),
                            icon = tab.icon,
                            isSelected = selectedTab == tab,
                            modifier = if (index == 0) Modifier.focusRequester(firstTabRequester) else Modifier,
                            onFocused = { selectedTab = tab }
                        )
                    }
                }

                // --- RIGHT: CONTENT (Dynamic Panel) ---
                TvGlassPane(modifier = Modifier.weight(0.7f), backgroundColor = Color.White.copy(alpha = 0.03f)) {
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
                        Text(text = stringResource(selectedTab.labelRes).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                    }
                    HorizontalDivider(color = TvColors.GlassBorder)

                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (selectedTab) {
                            SettingTab.ACCOUNT -> {
                                item {
                                    TvSettingsAction(
                                        label = if (uiState.isSyncing) "Syncing..." else stringResource(R.string.settings_refresh_data), 
                                        icon = Icons.Default.Refresh,
                                        subtitle = "Update channels and VOD lists"
                                    ) { viewModel.syncAll() }
                                }
                                item {
                                    TvSettingsAction(
                                        label = stringResource(R.string.nav_logout), 
                                        icon = Icons.Default.Logout, 
                                        color = Color(0xFFEF5350),
                                        subtitle = "Sign out from this account"
                                    ) { onLogout() }
                                }
                            }
                            SettingTab.PLAYBACK -> {
                                item {
                                    TvToggleRow(
                                        title = stringResource(R.string.settings_prevent_standby), 
                                        subtitle = "Keep screen active during playback",
                                        checked = uiState.preventStandby
                                    ) { viewModel.setPreventStandby(it) }
                                }
                                item {
                                    TvToggleRow(
                                        title = stringResource(R.string.settings_auto_play_next_episode), 
                                        subtitle = "Start next episode automatically",
                                        checked = uiState.autoPlayNextEpisode
                                    ) { viewModel.setAutoPlayNextEpisode(it) }
                                }
                                item {
                                    TvSelectionRow(
                                        title = stringResource(R.string.settings_decoder_mode),
                                        value = uiState.decoderMode,
                                        icon = Icons.Default.Hardware
                                    ) { 
                                        val modes = listOf("AUTO", "HARDWARE", "SOFTWARE")
                                        val next = modes[(modes.indexOf(uiState.decoderMode) + 1) % modes.size]
                                        viewModel.setDecoderMode(next)
                                    }
                                }
                            }
                            SettingTab.NETWORK -> {
                                item {
                                    TvToggleRow(
                                        title = stringResource(R.string.settings_secure_connection), 
                                        subtitle = "Enable shadowsocks VPN tunnel",
                                        checked = uiState.isSecureConnectionEnabled
                                    ) { viewModel.setSecureConnectionEnabled(it) }
                                }
                            }
                            SettingTab.ABOUT -> {
                                item { TvInfoItem("App Name", "ArenaLive") }
                                item { TvInfoItem("Hardware", Build.MODEL) }
                                item { TvInfoItem("OS Version", "Android ${Build.VERSION.RELEASE}") }
                                item { TvInfoItem("Engine", "Media3 1.11.0 (Stable)") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = { /* Focus handles selection */ },
        modifier = modifier.padding(vertical = 4.dp).onFocusChanged { 
            isFocused = it.isFocused
            if (it.isFocused) onFocused() 
        },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
            Text(text = label, color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvSettingsAction(
    label: String, 
    icon: ImageVector, 
    subtitle: String,
    color: Color = Color.White, 
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(modifier = Modifier.size(44.dp).background(if(isFocused) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (isFocused) Color.Black else color)
            }
            Column {
                Text(text = label, color = if (isFocused) Color.Black else color, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(text = subtitle, color = if (isFocused) Color.Black.copy(alpha = 0.6f) else TvColors.TextTertiary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TvToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = { onToggle(!checked) },
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f), focusedContainerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(text = subtitle, color = if (isFocused) Color.Black.copy(alpha = 0.6f) else TvColors.TextTertiary, fontSize = 12.sp)
            }
            Switch(
                checked = checked, 
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TvColors.Primary, 
                    checkedTrackColor = TvColors.Primary.copy(alpha = 0.3f),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}

@Composable
private fun TvSelectionRow(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f), focusedContainerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isFocused) Color.Black else TvColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text(text = value, color = if (isFocused) Color.Black else TvColors.Primary, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TvInfoItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = TvColors.TextSecondary, fontSize = 14.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}
