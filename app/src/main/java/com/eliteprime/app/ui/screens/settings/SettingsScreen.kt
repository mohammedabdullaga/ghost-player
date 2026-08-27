package com.eliteprime.app.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.eliteprime.app.domain.model.DownloadStorageConfig
import com.eliteprime.app.player.PlayerSubtitleStyle
import com.eliteprime.app.util.speedtest.InternetSpeedTestSnapshot
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────
// Design tokens — reined back in from Gemini's oversized values. File-scoped
// so they can't collide with anything else in the module.
// ─────────────────────────────────────────────────────────────────────────
private object STSpacing {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 18.dp
}

private object STRadius {
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 20.dp
}

private enum class SettingCategory(val labelRes: Int, val icon: ImageVector) {
    ACCOUNT(R.string.settings_account, Icons.Default.Person),
    GENERAL(R.string.settings_general, Icons.Default.Settings),
    NETWORK(R.string.settings_network, Icons.Default.NetworkCheck),
    PLAYBACK(R.string.settings_playback, Icons.Default.PlayCircle),
    ABOUT(R.string.settings_about, Icons.Default.Info),
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
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    AppBackground {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(STSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
        ) {
            // --- SIDEBAR (Includes Logo & Back) ---
            Surface(
                modifier = Modifier.weight(0.26f).fillMaxHeight(),
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(STRadius.xl),
                border = BorderStroke(1.dp, AppColors.ArenaOrange.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier.padding(STSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(STSpacing.xs)
                ) {
                    // Header area in Sidebar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = STSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)
                    ) {
                        if (isTv) {
                            TvSurface(
                                onClick = onBack,
                                modifier = Modifier.size(40.dp),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = Color.White
                                ),
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                )
                            ) {
                                var isFocused by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxSize().onFocusChanged { isFocused = it.isFocused }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        ArenaLiveLogo(textSize = 18.sp)
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                    SettingCategory.entries.forEach { category ->
                        SidebarItem(
                            label = stringResource(category.labelRes),
                            icon = category.icon,
                            isSelected = selectedCategory == category,
                            isTv = isTv,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }

            // --- CONTENT (Starts from Top Edge) ---
            Surface(
                modifier = Modifier.weight(0.74f).fillMaxHeight(),
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(STRadius.xl),
                border = BorderStroke(1.dp, AppColors.ArenaOrange.copy(alpha = 0.18f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(STSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                            Icon(selectedCategory.icon, contentDescription = null, tint = AppColors.ArenaOrange, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(selectedCategory.labelRes).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        if (selectedCategory == SettingCategory.ACCOUNT && uiState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AppColors.ArenaOrange, strokeWidth = 2.dp)
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                    when (selectedCategory) {
                        SettingCategory.ACCOUNT -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(STSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(STSpacing.md)
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
                                            onClearCache = { viewModel.clearCache() },
                                            isTv = isTv
                                        )
                                    }
                                }

                                item {
                                    LogoutButton(
                                        isTv = isTv,
                                        onLogout = { showLogoutDialog = true }
                                    )
                                }
                            }
                        }
                        SettingCategory.GENERAL -> {
                            GeneralSettingsContent(
                                currentLanguage = uiState.language,
                                onLanguageChange = { viewModel.setLanguage(it) },
                                storageConfig = uiState.storageConfig,
                                onFolderSelected = { viewModel.updateStorageConfig(it) },
                                isTv = isTv
                            )
                        }
                        SettingCategory.NETWORK -> {
                            NetworkSettingsContent(
                                isEnabled = uiState.isSecureConnectionEnabled,
                                isAvailable = uiState.isByeDpiAvailable,
                                onToggle = { viewModel.setSecureConnectionEnabled(it) },
                                isTestingSpeed = uiState.isSpeedTesting,
                                speedResult = uiState.speedTestResult,
                                speedError = uiState.speedTestError,
                                onRunSpeedTest = { viewModel.runSpeedTest() },
                                isTv = isTv
                            )
                        }
                        SettingCategory.PLAYBACK -> {
                            PlaybackSettingsContent(
                                uiState = uiState,
                                viewModel = viewModel,
                                isTv = isTv
                            )
                        }
                        SettingCategory.ABOUT -> {
                            AboutSettingsContent(
                                uiState = uiState,
                                onCheckUpdate = { viewModel.checkForUpdates() },
                                isTv = isTv
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_logout_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_logout_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    onLogout()
                }) {
                    Text(stringResource(R.string.nav_logout).uppercase(), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(android.R.string.cancel), color = Color.White.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(STRadius.lg),
            containerColor = Color(0xFF1A1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f)
        )
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
private fun NetworkSettingsContent(
    isEnabled: Boolean,
    isAvailable: Boolean,
    onToggle: (Boolean) -> Unit,
    isTestingSpeed: Boolean,
    speedResult: InternetSpeedTestSnapshot?,
    speedError: String?,
    onRunSpeedTest: () -> Unit,
    isTv: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(STSpacing.md),
        verticalArrangement = Arrangement.spacedBy(STSpacing.md)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = "SECURE STREAMING",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Surface(
                    onClick = { if (isAvailable) onToggle(!isEnabled) },
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(STRadius.lg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.alpha(if (isAvailable) 1f else 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(STSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (isEnabled && isAvailable) AppColors.ArenaOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isEnabled && isAvailable) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                null,
                                tint = if (isEnabled && isAvailable) AppColors.ArenaOrange else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_secure_connection),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isAvailable) stringResource(R.string.settings_secure_connection_desc) else "Currently unavailable",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }

                        Switch(
                            checked = isEnabled && isAvailable,
                            onCheckedChange = null,
                            enabled = isAvailable,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppColors.ArenaOrange,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                                disabledCheckedTrackColor = AppColors.ArenaOrange.copy(alpha = 0.5f),
                                disabledUncheckedTrackColor = Color.White.copy(alpha = 0.05f)
                            )
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = "INTERNET SPEED TEST",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(STRadius.lg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(STSpacing.md), verticalArrangement = Arrangement.spacedBy(STSpacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(stringResource(R.string.settings_speed_test_check), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (speedResult != null) {
                                    Text(
                                        text = "${"%.2f".format(speedResult.megabitsPerSecond)} Mbps",
                                        color = AppColors.ArenaOrange,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp
                                    )
                                }
                            }

                            if (isTestingSpeed) {
                                CircularProgressIndicator(color = AppColors.ArenaOrange, modifier = Modifier.size(28.dp))
                            } else {
                                Button(
                                    onClick = onRunSpeedTest,
                                    modifier = Modifier.height(40.dp).padding(horizontal = STSpacing.xs),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.ArenaOrange),
                                    shape = RoundedCornerShape(STRadius.sm)
                                ) {
                                    Text(stringResource(R.string.settings_speed_test_run), fontWeight = FontWeight.Black, fontSize = 13.sp)
                                }
                            }
                        }

                        if (speedError != null) {
                            Text(text = speedError, color = Color.Red, fontSize = 12.sp)
                        }

                        if (speedResult != null) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val recommendation = when (speedResult.recommendedMaxVideoHeight) {
                                        480 -> stringResource(R.string.settings_speed_test_sd)
                                        720 -> stringResource(R.string.settings_speed_test_hd)
                                        1080 -> stringResource(R.string.settings_speed_test_fhd)
                                        2160 -> stringResource(R.string.settings_speed_test_4k)
                                        else -> stringResource(R.string.settings_speed_test_max)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(STSpacing.xs)) {
                                        Icon(Icons.Default.Recommend, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(15.dp))
                                        Text(text = recommendation, color = Color.White.copy(alpha = 0.95f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Surface(
                                    color = Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(STRadius.sm)
                                ) {
                                    Text(
                                        text = speedResult.transport.name,
                                        modifier = Modifier.padding(horizontal = STSpacing.sm, vertical = 5.dp),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
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
private fun GeneralSettingsContent(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    storageConfig: DownloadStorageConfig,
    onFolderSelected: (Uri) -> Unit,
    isTv: Boolean
) {
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onFolderSelected)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(STSpacing.md),
        verticalArrangement = Arrangement.spacedBy(STSpacing.lg)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_language).uppercase(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                ) {
                    LanguageChip(
                        label = "English",
                        isActive = currentLanguage == "en",
                        onClick = { onLanguageChange("en") },
                        isTv = isTv,
                        modifier = Modifier.weight(1f)
                    )
                    LanguageChip(
                        label = "العربية",
                        isActive = currentLanguage == "ar",
                        onClick = { onLanguageChange("ar") },
                        isTv = isTv,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_download_folder),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Surface(
                    onClick = { folderLauncher.launch(null) },
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(STRadius.md),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(STSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(AppColors.ArenaOrange.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Folder, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = storageConfig.displayName ?: "Default (App Storage)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_download_folder_desc),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    if (isTv) {
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .height(48.dp)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.sm)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                border = Border(BorderStroke(2.dp, if (isActive) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = if (isActive || isFocused) FontWeight.Black else FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            color = if (isActive) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(STRadius.sm),
            border = BorderStroke(1.dp, if (isActive) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    isTv: Boolean
) {
    val context = LocalContext.current
    var showBufferDialog by remember { mutableStateOf(false) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var showPlayerEngineDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(STSpacing.md),
        verticalArrangement = Arrangement.spacedBy(STSpacing.lg)
    ) {
        // --- Core Playback Toggles ---
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                            Text(
                                text = stringResource(R.string.settings_playback_options),
                                color = Color.White.copy(alpha = 0.6f),
                                style = EyebrowStyle
                            )

                            SettingClickableItem(
                                title = stringResource(R.string.settings_player_engine),
                                subtitle = if (uiState.playerEngineMode == "VLC") stringResource(R.string.settings_player_vlc) else stringResource(R.string.settings_player_exo),
                                icon = Icons.Default.PlayCircle,
                                onClick = { showPlayerEngineDialog = true }
                            )
                            
                            SettingToggleItem(
                                title = stringResource(R.string.settings_prevent_standby),
                                subtitle = stringResource(R.string.settings_prevent_standby_subtitle),
                                icon = Icons.Default.BrightnessHigh,
                                checked = uiState.preventStandby,
                                onCheckedChange = { viewModel.setPreventStandby(it) }
                            )

                            SettingToggleItem(
                                title = stringResource(R.string.settings_auto_play_next_episode),
                                subtitle = stringResource(R.string.settings_auto_play_next_episode_subtitle),
                                icon = Icons.Default.SkipNext,
                                checked = uiState.autoPlayNextEpisode,
                                onCheckedChange = { viewModel.setAutoPlayNextEpisode(it) }
                            )

                            SettingToggleItem(
                                title = stringResource(R.string.settings_zap_auto_revert),
                                subtitle = stringResource(R.string.settings_zap_auto_revert_subtitle),
                                icon = Icons.Default.Undo,
                                checked = uiState.zapAutoRevert,
                                onCheckedChange = { viewModel.setZapAutoRevert(it) }
                            )
                        }
                    }

                    // --- Engine Preferences ---
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                            Text(
                                text = stringResource(R.string.settings_engine_preferences),
                                color = Color.White.copy(alpha = 0.6f),
                                style = EyebrowStyle
                            )

                            SettingClickableItem(
                                title = stringResource(R.string.settings_live_buffer_size),
                                subtitle = formatLiveBufferLabel(uiState.liveBufferSize, context),
                                icon = Icons.Default.Dns,
                                onClick = { showBufferDialog = true }
                            )

                            SettingClickableItem(
                                title = stringResource(R.string.settings_decoder_mode),
                                subtitle = formatDecoderModeLabel(uiState.decoderMode, context),
                                icon = Icons.Default.Hardware,
                                onClick = { showDecoderDialog = true }
                            )
                        }
                    }

                    // --- Subtitle Styling (Merged here) ---
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                            Text(
                                text = stringResource(R.string.settings_subtitle_size),
                                color = Color.White.copy(alpha = 0.6f),
                                style = EyebrowStyle
                            )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                ) {
                    subtitleSizeOptions().forEach { option ->
                        val isActive = uiState.subtitleStyle.textScale == option.scale
                        SubFilterChip(
                            label = stringResource(option.labelResId),
                            icon = Icons.Default.FormatSize,
                            isActive = isActive,
                            onClick = { viewModel.setSubtitleStyle(uiState.subtitleStyle.copy(textScale = option.scale)) }
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_subtitle_text_color),
                    color = Color.White.copy(alpha = 0.6f),
                    style = EyebrowStyle
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                ) {
                    subtitleTextColorOptions().forEach { option ->
                        val isActive = uiState.subtitleStyle.foregroundColorArgb == option.colorArgb
                        SubtitleColorChip(
                            label = stringResource(option.labelResId),
                            color = Color(option.colorArgb),
                            isActive = isActive,
                            onClick = { viewModel.setSubtitleStyle(uiState.subtitleStyle.copy(foregroundColorArgb = option.colorArgb)) }
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_subtitle_background),
                    color = Color.White.copy(alpha = 0.6f),
                    style = EyebrowStyle
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                ) {
                    subtitleBackgroundColorOptions().forEach { option ->
                        val isActive = uiState.subtitleStyle.backgroundColorArgb == option.colorArgb
                        SubtitleColorChip(
                            label = stringResource(option.labelResId),
                            color = Color(option.colorArgb),
                            isActive = isActive,
                            onClick = { viewModel.setSubtitleStyle(uiState.subtitleStyle.copy(backgroundColorArgb = option.colorArgb)) }
                        )
                    }
                }
            }
        }

        // Preview Card
        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.generic_preview),
                    color = Color.White.copy(alpha = 0.6f),
                    style = EyebrowStyle
                )
                
                Surface(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    color = Color.Black,
                    shape = RoundedCornerShape(STRadius.lg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Dummy image simulation
                        Box(modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(Color(0xFF1A2A6C), Color(0xFFB21F1F), Color(0xFFFDBB2D)))
                        ).alpha(0.4f))

                        Surface(
                            color = Color(uiState.subtitleStyle.backgroundColorArgb),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_subtitle_preview_text),
                                color = Color(uiState.subtitleStyle.foregroundColorArgb),
                                fontSize = (16 * uiState.subtitleStyle.textScale).sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBufferDialog) {
        val options = listOf(0, 5000, 10000, 20000, 30000)
        SettingsSelectionDialog(
            title = stringResource(R.string.settings_select_buffer_size),
            onDismiss = { showBufferDialog = false }
        ) {
            options.forEach { size ->
                RadioOptionRow(
                    label = formatLiveBufferLabel(size, context),
                    isSelected = uiState.liveBufferSize == size,
                    onClick = {
                        viewModel.setLiveBufferSize(size)
                        showBufferDialog = false
                    }
                )
            }
        }
    }

    if (showDecoderDialog) {
        val options = listOf("AUTO", "HARDWARE", "SOFTWARE")
        SettingsSelectionDialog(
            title = stringResource(R.string.settings_select_decoder_mode),
            onDismiss = { showDecoderDialog = false }
        ) {
            options.forEach { mode ->
                RadioOptionRow(
                    label = formatDecoderModeLabel(mode, context),
                    isSelected = uiState.decoderMode == mode,
                    onClick = {
                        viewModel.setDecoderMode(mode)
                        showDecoderDialog = false
                    }
                )
            }
        }
    }

    if (showPlayerEngineDialog) {
        val options = listOf("VLC", "EXO")
        SettingsSelectionDialog(
            title = stringResource(R.string.settings_select_player_engine),
            onDismiss = { showPlayerEngineDialog = false }
        ) {
            options.forEach { mode ->
                RadioOptionRow(
                    label = if (mode == "VLC") stringResource(R.string.settings_player_vlc) else stringResource(R.string.settings_player_exo),
                    isSelected = uiState.playerEngineMode == mode,
                    onClick = {
                        viewModel.setPlayerEngineMode(mode)
                        showPlayerEngineDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingClickableItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(STRadius.lg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(STSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    color = AppColors.ArenaOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSelectionDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.generic_cancel), color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1C1E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun RadioOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp
            )
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = AppColors.ArenaOrange,
                    unselectedColor = Color.White.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
private fun SettingToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(STRadius.lg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(STSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (checked) AppColors.ArenaOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (checked) AppColors.ArenaOrange else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.ArenaOrange,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}

@Composable
private fun SubtitleColorChip(
    label: String,
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }
    
    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.85f),
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                border = Border(BorderStroke(2.dp, if (isActive) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
            )
        ) {
            SubtitleColorChipContent(label, color, isActive, isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            color = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (isActive) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.15f))
        ) {
            SubtitleColorChipContent(label, color, isActive, false)
        }
    }
}

@Composable
private fun SubtitleColorChipContent(label: String, color: Color, active: Boolean, isFocused: Boolean) {
    val contentColor = when {
        isFocused -> Color.Black
        active -> Color.White
        else -> Color.White.copy(alpha = 0.6f)
    }
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
        )
        Text(
            text = label,
            color = contentColor,
            fontWeight = if (active || isFocused) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp
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
                containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Transparent,
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.md)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
            )
        ) {
            SidebarItemContent(label, icon, isSelected, isFocused)
        }
    } else {
        Surface(
            onClick = onClick,
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Transparent,
            shape = RoundedCornerShape(STRadius.md),
            border = if (isSelected) BorderStroke(1.dp, AppColors.ArenaOrange.copy(alpha = 0.85f)) else null
        ) {
            SidebarItemContent(label, icon, isSelected, false)
        }
    }
}

@Composable
private fun SidebarItemContent(label: String, icon: ImageVector, isActive: Boolean, isFocused: Boolean) {
    val contentColor = when {
        isFocused -> Color.Black
        isActive -> Color.White
        else -> Color.White.copy(alpha = 0.6f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = STSpacing.sm, vertical = STSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(if (isActive || isFocused) AppColors.ArenaOrange else Color.Transparent, RoundedCornerShape(50))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Medium,
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
    onClearCache: () -> Unit,
    isTv: Boolean
) {
    val cardBackground = Brush.linearGradient(
        colors = listOf(Color(0xFF1A1C1E), Color(0xFF0D0E10))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(STRadius.xl))
            .background(cardBackground)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(STRadius.xl))
    ) {
        // Glowing corner accent — scaled down from Gemini's 300dp/120dp
        // offset, which pushed a huge chunk of orange glow into the card.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(220.dp)
                .offset(x = 90.dp, y = (-90).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AppColors.ArenaOrange.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier.padding(STSpacing.md),
            verticalArrangement = Arrangement.spacedBy(STSpacing.lg)
        ) {
            // 1. Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(STSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                        Text(
                            text = account.name.ifBlank { "Elite TV" },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "(${account.username})",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val expirationDate = account.expiryDate?.let {
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
                    } ?: stringResource(R.string.generic_unknown)

                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(STRadius.sm),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = STSpacing.sm, vertical = STSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(STSpacing.xs)
                        ) {
                            Icon(Icons.Default.Event, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(15.dp))
                            Text(stringResource(R.string.settings_account_expires, expirationDate), color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                StatusPill(label = stringResource(R.string.generic_status_active), containerColor = AppColors.ArenaOrange, horizontalPadding = 10.dp, verticalPadding = 4.dp)
            }

            // 2. Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)
            ) {
                SubscriptionMetricCard(
                    title = stringResource(R.string.nav_live_tv),
                    count = liveCount,
                    icon = Icons.Default.PlayArrow,
                    accentColor = Color(0xFF4CAF50),
                    isTv = isTv,
                    modifier = Modifier.weight(1f)
                )
                SubscriptionMetricCard(
                    title = stringResource(R.string.nav_movies),
                    count = movieCount,
                    icon = Icons.Default.Movie,
                    accentColor = Color(0xFF2196F3),
                    isTv = isTv,
                    modifier = Modifier.weight(1f)
                )
                SubscriptionMetricCard(
                    title = stringResource(R.string.nav_series),
                    count = seriesCount,
                    icon = Icons.Default.Tv,
                    accentColor = Color(0xFFE91E63),
                    isTv = isTv,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)
            ) {
                SettingsActionButton(
                    label = if (isSyncing) stringResource(R.string.player_buffering) else stringResource(R.string.settings_refresh_data),
                    onClick = onRefresh,
                    isPrimary = true,
                    isTv = isTv,
                    modifier = Modifier.weight(1.5f).height(44.dp)
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_edit_account),
                    onClick = onEdit,
                    isTv = isTv,
                    modifier = Modifier.weight(1f).height(44.dp)
                )

                SettingsActionButton(
                    label = stringResource(R.string.settings_clear_cache),
                    onClick = onClearCache,
                    isTv = isTv,
                    modifier = Modifier.weight(1f).height(44.dp)
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
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    if (isTv) {
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = {},
            modifier = modifier.onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.04f),
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.md)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
        ) {
            SubscriptionMetricCardContent(title, count, icon, accentColor, isFocused)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(STRadius.md),
            color = Color.White.copy(alpha = 0.04f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        ) {
            SubscriptionMetricCardContent(title, count, icon, accentColor)
        }
    }
}

@Composable
private fun SubscriptionMetricCardContent(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    isFocused: Boolean = false
) {
    Column(
        modifier = Modifier.padding(STSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (isFocused) Color.Black else accentColor.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
        Column {
            Text(
                text = String.format(Locale.getDefault(), "%,d", count),
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title.uppercase(),
                color = if (isFocused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
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
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = onClick,
            modifier = modifier.onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isPrimary) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.sm)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                border = Border(BorderStroke(2.dp, if (isPrimary) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label.uppercase(),
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPrimary) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(STRadius.sm)
        ) {
            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LogoutButton(isTv: Boolean, onLogout: () -> Unit) {
    val logoutColor = Color(0xFFEF5350)
    if (isTv) {
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = logoutColor.copy(alpha = 0.08f),
                focusedContainerColor = logoutColor
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.md)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
        ) {
            LogoutButtonContent(if (isFocused) Color.White else logoutColor, isFocused)
        }
    } else {
        Surface(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            color = logoutColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(STRadius.md),
            border = BorderStroke(1.dp, logoutColor.copy(alpha = 0.25f))
        ) {
            LogoutButtonContent(logoutColor, false)
        }
    }
}

@Composable
private fun LogoutButtonContent(color: Color, isFocused: Boolean) {
    Row(
        modifier = Modifier.padding(STSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Column {
            Text(stringResource(R.string.nav_logout), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                text = stringResource(R.string.nav_logout_subtitle),
                color = if (isFocused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AboutSettingsContent(
    uiState: SettingsUiState,
    onCheckUpdate: () -> Unit,
    isTv: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(STSpacing.md),
        verticalArrangement = Arrangement.spacedBy(STSpacing.lg)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_about_app_info),
                    color = Color.White.copy(alpha = 0.6f),
                    style = EyebrowStyle
                )
                
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(STRadius.lg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(STSpacing.md), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AboutInfoRow(label = stringResource(R.string.settings_about_app_name), value = "Elite TV")
                        AboutInfoRow(label = stringResource(R.string.settings_about_developed_by), value = "Elit Bahrain")
                        AboutInfoRow(label = stringResource(R.string.settings_about_build_version), value = "1.0.0")
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_update_status),
                    color = Color.White.copy(alpha = 0.6f),
                    style = EyebrowStyle
                )
                
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(STRadius.lg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(STSpacing.md), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AboutInfoRow(label = stringResource(R.string.settings_about_latest_release), value = uiState.latestVersion ?: stringResource(R.string.generic_unknown))
                        AboutInfoRow(
                            label = stringResource(R.string.settings_about_update_status), 
                            value = uiState.updateStatus,
                            valueColor = if (uiState.updateStatus.contains("available", ignoreCase = true)) AppColors.ArenaOrange else Color.Green.copy(alpha = 0.8f)
                        )
                        
                        val lastChecked = if (uiState.lastUpdateCheckTimestamp > 0) {
                            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(uiState.lastUpdateCheckTimestamp))
                        } else stringResource(R.string.generic_never)
                        
                        AboutInfoRow(label = stringResource(R.string.settings_about_last_checked), value = lastChecked)

                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsActionButton(
                            label = if (uiState.isCheckingForUpdates) stringResource(R.string.generic_checking) else stringResource(R.string.settings_about_check_updates),
                            onClick = onCheckUpdate,
                            isPrimary = true,
                            isTv = isTv,
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EditAccountDialog(
    account: XtreamAccount,
    onDismiss: () -> Unit,
    onSave: (XtreamAccount) -> Unit
) {
    var username by remember { mutableStateOf(account.username) }
    var password by remember { mutableStateOf(account.password) }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(R.string.settings_edit_account), 
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = AppColors.ArenaOrange
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = username, 
                    onValueChange = { username = it }, 
                    label = { Text(stringResource(R.string.login_username)) }, 
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AppColors.ArenaOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        cursorColor = AppColors.ArenaOrange
                    )
                )
                
                OutlinedTextField(
                    value = password, 
                    onValueChange = { password = it }, 
                    label = { Text(stringResource(R.string.login_password)) }, 
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AppColors.ArenaOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        cursorColor = AppColors.ArenaOrange
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(account.copy(username = username, password = password)) },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ArenaOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.generic_save_changes), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(STRadius.xl),
        containerColor = Color(0xFF14171C),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

