package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.R
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.settings.SettingsViewModel
import com.eliteprime.app.ui.theme.AppColors
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

    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        delay(200)
        firstTabRequester.requestFocus()
    }

    AppBackground(isTransparent = true) {
        Row(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            
            // --- LEFT: Tabs ---
            Column(modifier = Modifier.weight(0.3f)) {
                Text(
                    text = stringResource(R.string.nav_settings).uppercase(),
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                SettingTab.entries.forEachIndexed { index, tab ->
                    TvTabItem(
                        label = stringResource(tab.labelRes),
                        icon = tab.icon,
                        isSelected = selectedTab == tab,
                        modifier = if (index == 0) Modifier.focusRequester(firstTabRequester) else Modifier,
                        onFocused = { selectedTab = tab }
                    )
                }
            }

            // --- RIGHT: Content ---
            Column(modifier = Modifier.weight(0.7f).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).padding(32.dp)) {
                Text(
                    text = stringResource(selectedTab.labelRes).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), color = Color.White.copy(alpha = 0.1f))

                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (selectedTab) {
                        SettingTab.ACCOUNT -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    TvSettingsButton(
                                        label = if (uiState.isSyncing) stringResource(R.string.player_buffering) else stringResource(R.string.settings_refresh_data), 
                                        icon = Icons.Default.Refresh
                                    ) { viewModel.syncAll() }
                                    
                                    TvSettingsButton(label = stringResource(R.string.nav_logout), icon = Icons.Default.Logout, color = Color(0xFFEF5350)) { onLogout() }
                                }
                            }
                        }
                        SettingTab.PLAYBACK -> {
                            item {
                                TvToggleItem(
                                    title = stringResource(R.string.settings_prevent_standby),
                                    checked = uiState.preventStandby,
                                    onCheckedChange = { viewModel.setPreventStandby(it) }
                                )
                            }
                            item {
                                TvToggleItem(
                                    title = stringResource(R.string.settings_auto_play_next_episode),
                                    checked = uiState.autoPlayNextEpisode,
                                    onCheckedChange = { viewModel.setAutoPlayNextEpisode(it) }
                                )
                            }
                            item {
                                TvToggleItem(
                                    title = stringResource(R.string.settings_zap_auto_revert),
                                    checked = uiState.zapAutoRevert,
                                    onCheckedChange = { viewModel.setZapAutoRevert(it) }
                                )
                            }
                        }
                        SettingTab.NETWORK -> {
                            item {
                                TvToggleItem(
                                    title = stringResource(R.string.settings_secure_connection),
                                    checked = uiState.isSecureConnectionEnabled,
                                    onCheckedChange = { viewModel.setSecureConnectionEnabled(it) }
                                )
                            }
                        }
                        SettingTab.ABOUT -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TvInfoRow("App Name", "ArenaLive")
                                    TvInfoRow("Developed by", "Elit Bahrain")
                                    TvInfoRow("Build Version", "1.1.0 (Media3 1.11.0)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTabItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit
) {
    TvSurface(
        onClick = { /* Focus handles selection */ },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).onFocusChanged { if (it.isFocused) onFocused() },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = AppColors.ArenaOrange
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(text = label, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvSettingsButton(
    label: String,
    icon: ImageVector,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, color)))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = if (isFocused) Color.Black else color)
            Text(text = label, color = if (isFocused) Color.Black else color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold)
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AppColors.ArenaOrange,
                    checkedTrackColor = AppColors.ArenaOrange.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
private fun TvInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.White.copy(alpha = 0.6f))
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
