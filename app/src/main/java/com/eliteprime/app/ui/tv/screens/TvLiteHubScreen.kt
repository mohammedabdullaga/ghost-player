package com.eliteprime.app.ui.tv.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
                    viewModel.setLiteMode(false) // Switch to Standard Mode
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
            containerColor = Color(0xFF161A22),
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
            containerColor = Color(0xFF161A22),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f)
        )
    }

    // Flat solid background color (Zero blur, zero gradients, zero images for pure performance)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0F14))
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        // Top Bar: Brand Logo + Account Expiry Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArenaLiveLogo(textSize = 22.sp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!uiState.formattedExpiryDate.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF161A22),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = stringResource(R.string.hub_expires_format, uiState.formattedExpiryDate!!),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Center Content Area: Main 4 Primary Tiles (Left/Center) + Secondary Action Rows (Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(top = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Primary 2x2 Square Destination Tiles ──────────────────
            Column(
                modifier = Modifier.weight(0.68f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    HubPrimaryTile(
                        title = stringResource(R.string.nav_live_tv),
                        icon = Icons.Default.LiveTv,
                        tileColor = Color(0xFF1E293B), // Flat Slate Blue
                        focusRequester = firstTileRequester,
                        isTv = isTv,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToLive
                    )

                    HubPrimaryTile(
                        title = stringResource(R.string.nav_movies),
                        icon = Icons.Default.Movie,
                        tileColor = Color(0xFF1E293B),
                        focusRequester = null,
                        isTv = isTv,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToMovies
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    HubPrimaryTile(
                        title = stringResource(R.string.nav_series),
                        icon = Icons.Default.VideoLibrary,
                        tileColor = Color(0xFF1E293B),
                        focusRequester = null,
                        isTv = isTv,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSeries
                    )

                    HubPrimaryTile(
                        title = stringResource(R.string.nav_search),
                        icon = Icons.Default.Search,
                        tileColor = Color(0xFF1E293B),
                        focusRequester = null,
                        isTv = isTv,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSearch
                    )
                }
            }

            // ── Secondary Action Rows Column ──────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.32f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Settings Row
                    HubSecondaryActionRow(
                        title = stringResource(R.string.nav_settings),
                        icon = Icons.Default.Settings,
                        isTv = isTv,
                        onClick = onNavigateToSettings
                    )

                    // Reload / Sync Catalog Row
                    HubSecondaryActionRow(
                        title = if (uiState.isSyncing) stringResource(R.string.hub_syncing) else stringResource(R.string.hub_reload),
                        icon = Icons.Default.Refresh,
                        isLoading = uiState.isSyncing,
                        isTv = isTv,
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

                    // Switch Mode Row (Instead of Exit)
                    HubSecondaryActionRow(
                        title = stringResource(R.string.mode_standard_title),
                        icon = Icons.Default.Dashboard,
                        isTv = isTv,
                        onClick = { showSwitchModeDialog = true }
                    )
                }
            }
        }

        // Bottom Bar: App Version Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )

            Text(
                text = "⚡ Lite Mode",
                color = AppColors.ArenaOrange.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Primary Large Flat Square Tile for Lite Mode Hub.
 * Icon + Label only. Zero shadows, zero gradients, zero glow.
 */
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
            .height(130.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)

        TvSurface(
            onClick = onClick,
            modifier = tvModifier,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = tileColor,
                focusedContainerColor = AppColors.ArenaOrange
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White)),
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
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.height(130.dp),
            shape = RoundedCornerShape(16.dp),
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
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/**
 * Secondary Action Row for Settings, Reload, Exit.
 */
@Composable
private fun HubSecondaryActionRow(
    title: String,
    icon: ImageVector,
    isTv: Boolean,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(58.dp)
                .onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF161A22),
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
                    .padding(horizontal = 20.dp),
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
                        tint = if (isFocused) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = title,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF161A22),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
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
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
