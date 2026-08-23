package com.eliteprime.app.ui.tv.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.BuildConfig
import com.eliteprime.app.MainActivity
import com.eliteprime.app.R
import com.eliteprime.app.ui.components.ArenaLiveLogo
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay

// One unified tile color for all four primary tiles, deliberately
// desaturated — a saturated hue repeated across four large TV-sized
// blocks is tiring on the eyes over a long viewing session. A calm,
// slightly blue-leaning slate reads comfortably at a distance and
// doesn't compete with the app's orange accent used for focus states
// elsewhere.
private object HubBrand {
    val Tile = Color(0xFF25344A)
    val PanelDark = Color(0xFF161A22)
    val Background = Color(0xFF0C0F14)
}

@Composable
fun TvLiteHubScreen(
    onNavigateToLive: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TvLiteHubViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isTv = context.isTelevisionDevice()
    val uiState by viewModel.uiState.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    var showSwitchModeDialog by remember { mutableStateOf(false) }
    val firstTileRequester = remember { FocusRequester() }

    val syncSuccessMsg = stringResource(R.string.hub_sync_success)
    val syncFailedMsg = stringResource(R.string.hub_sync_failed)

    LaunchedEffect(Unit) {
        if (isTv) {
            delay(200)
            try { firstTileRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    BackHandler {
        showExitDialog = true
    }

    if (showSwitchModeDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchModeDialog = false },
            title = { Text(stringResource(R.string.settings_lite_mode_restart_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_lite_mode_restart_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchModeDialog = false
                    viewModel.setLiteMode(false)
                    (activity as? MainActivity)?.restartApp()
                }) {
                    Text(
                        text = stringResource(R.string.generic_yes).uppercase(),
                        color = AppColors.ArenaOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchModeDialog = false }) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = HubBrand.PanelDark,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f)
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.exit_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.exit_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) {
                    Text(
                        text = stringResource(R.string.generic_yes).uppercase(),
                        color = Color(0xFFEF5350),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = HubBrand.PanelDark,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HubBrand.Background)
    ) {
        Image(
            painter = painterResource(R.drawable.lite_mode_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 36.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArenaLiveLogo(textSize = 26.sp)

                if (!uiState.formattedExpiryDate.isNullOrBlank()) {
                    Surface(
                        color = HubBrand.PanelDark,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = stringResource(R.string.hub_expires_format, uiState.formattedExpiryDate!!),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Main content — now takes up the full available height
            // between the top and bottom bars, tiles sized generously
            // instead of a small centered cluster.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(0.7f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        HubPrimaryTile(
                            title = stringResource(R.string.nav_live_tv),
                            icon = Icons.Default.LiveTv,
                            tileColor = HubBrand.Tile,
                            focusRequester = firstTileRequester,
                            isTv = isTv,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = onNavigateToLive
                        )
                        HubPrimaryTile(
                            title = stringResource(R.string.nav_movies),
                            icon = Icons.Default.Movie,
                            tileColor = HubBrand.Tile,
                            focusRequester = null,
                            isTv = isTv,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = onNavigateToMovies
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        HubPrimaryTile(
                            title = stringResource(R.string.nav_series),
                            icon = Icons.Default.VideoLibrary,
                            tileColor = HubBrand.Tile,
                            focusRequester = null,
                            isTv = isTv,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = onNavigateToSeries
                        )
                        HubPrimaryTile(
                            title = stringResource(R.string.nav_search),
                            icon = Icons.Default.Search,
                            tileColor = HubBrand.Tile,
                            focusRequester = null,
                            isTv = isTv,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = onNavigateToSearch
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(0.3f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    HubSecondaryActionRow(
                        title = stringResource(R.string.nav_settings),
                        icon = Icons.Default.Settings,
                        isTv = isTv,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSettings
                    )
                    HubSecondaryActionRow(
                        title = if (uiState.isSyncing) stringResource(R.string.hub_syncing) else stringResource(R.string.hub_reload),
                        icon = Icons.Default.Refresh,
                        isLoading = uiState.isSyncing,
                        isTv = isTv,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!uiState.isSyncing) {
                                viewModel.reloadData { success ->
                                    Toast.makeText(
                                        context,
                                        if (success) syncSuccessMsg else syncFailedMsg,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                    HubSecondaryActionRow(
                        title = stringResource(R.string.mode_standard_title),
                        icon = Icons.Default.Dashboard,
                        isTv = isTv,
                        modifier = Modifier.weight(1f),
                        onClick = { showSwitchModeDialog = true }
                    )
                    HubSecondaryActionRow(
                        title = stringResource(R.string.hub_exit),
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        isTv = isTv,
                        accentColor = Color(0xFFEF5350),
                        modifier = Modifier.weight(1f),
                        onClick = { showExitDialog = true }
                    )
                }
            }

            // Bottom Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = "⚡ Lite Mode",
                    color = AppColors.ArenaOrange.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HubPrimaryTile(
    title: String,
    icon: ImageVector,
    tileColor: Color,
    focusRequester: FocusRequester?,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        val tvModifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)

        TvSurface(
            onClick = onClick,
            modifier = tvModifier,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = tileColor,
                focusedContainerColor = Color.White
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(4.dp, Color.White)),
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else Color.White,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = title,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            color = tileColor,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
private fun HubSecondaryActionRow(
    title: String,
    icon: ImageVector,
    isTv: Boolean,
    isLoading: Boolean = false,
    accentColor: Color = Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = HubBrand.PanelDark,
                focusedContainerColor = AppColors.ArenaOrange
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White)),
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = if (isFocused) Color.Black else AppColors.ArenaOrange,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) Color.Black else accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = title,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = HubBrand.PanelDark,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = AppColors.ArenaOrange,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}