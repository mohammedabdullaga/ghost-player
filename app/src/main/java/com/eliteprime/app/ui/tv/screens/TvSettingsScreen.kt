package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.intl.LocaleList as ComposeLocaleList
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
import com.eliteprime.app.ui.screens.settings.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.speedtest.InternetSpeedTestSnapshot
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Same design tokens as the mobile SettingsScreen — matching values on
// purpose, so this file's visual language stays in sync with mobile's
// even though it's a separate, TV-only implementation (not a shared
// file — that was tried and abandoned; TvSurface/focus semantics are
// different enough from touch that a single branching file got messy).
private object STSpacing {
    val xs = 10.dp
    val sm = 14.dp
    val md = 20.dp
    val lg = 22.dp
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

@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf(SettingCategory.ACCOUNT) }
    val context = LocalContext.current
    val firstTabRequester = remember { FocusRequester() }

    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        delay(200)
        try { firstTabRequester.requestFocus() } catch (_: Exception) {}
    }

    AppBackground {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(STSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
        ) {
            // --- SIDEBAR (Back button only — no logo) ---
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = STSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isBackFocused by remember { mutableStateOf(false) }
                        TvSurface(
                            onClick = onBack,
                            modifier = Modifier.size(44.dp).onFocusChanged { isBackFocused = it.isFocused },
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
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (isBackFocused) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                    SettingCategory.entries.forEachIndexed { index, category ->
                        TvSidebarItem(
                            label = stringResource(category.labelRes),
                            icon = category.icon,
                            isSelected = selectedCategory == category,
                            modifier = if (index == 0) Modifier.focusRequester(firstTabRequester) else Modifier,
                            onFocused = { selectedCategory = category }
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
                                        TvAccountSettingsCard(
                                            account = account,
                                            liveCount = uiState.liveCount.toInt(),
                                            movieCount = uiState.movieCount.toInt(),
                                            seriesCount = uiState.seriesCount.toInt(),
                                            epgCount = uiState.epgCount.toInt(),
                                            isSyncing = uiState.isSyncing,
                                            onRefresh = { viewModel.syncAll() },
                                            onEdit = { showEditDialog = true },
                                            onClearCache = { viewModel.clearCache() }
                                        )
                                    }
                                }

                                item {
                                    TvLogoutButton(onLogout = { showLogoutDialog = true })
                                }
                            }
                        }
                        SettingCategory.GENERAL -> {
                            TvGeneralSettingsContent(
                                currentLanguage = uiState.language,
                                onLanguageChange = { viewModel.setLanguage(it) }
                            )
                        }
                        SettingCategory.NETWORK -> {
                            TvNetworkSettingsContent(
                                isEnabled = uiState.isSecureConnectionEnabled,
                                onToggle = { viewModel.setSecureConnectionEnabled(it) },
                                isTestingSpeed = uiState.isSpeedTesting,
                                speedResult = uiState.speedTestResult,
                                speedError = uiState.speedTestError,
                                onRunSpeedTest = { viewModel.runSpeedTest() }
                            )
                        }
                        SettingCategory.PLAYBACK -> {
                            TvPlaybackSettingsContent(
                                uiState = uiState,
                                viewModel = viewModel,
                                context = context
                            )
                        }
                        SettingCategory.ABOUT -> {
                            TvAboutSettingsContent(
                                uiState = uiState,
                                onCheckUpdate = { viewModel.checkForUpdates() }
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
        TvEditAccountDialog(
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
private fun TvNetworkSettingsContent(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isTestingSpeed: Boolean,
    speedResult: InternetSpeedTestSnapshot?,
    speedError: String?,
    onRunSpeedTest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(STSpacing.md),
        verticalArrangement = Arrangement.spacedBy(STSpacing.md)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_network).uppercase(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                var isRowFocused by remember { mutableStateOf(false) }
                TvSurface(
                    onClick = { onToggle(!isEnabled) },
                    modifier = Modifier.onFocusChanged { isRowFocused = it.isFocused },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        focusedContainerColor = Color.White
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.lg)),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
                ) {
                    Row(
                        modifier = Modifier.padding(STSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (isEnabled) AppColors.ArenaOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isEnabled) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                null,
                                tint = if (isRowFocused) Color.Black else if (isEnabled) AppColors.ArenaOrange else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_secure_connection),
                                color = if (isRowFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_secure_connection_desc),
                                color = if (isRowFocused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }

                        Switch(
                            checked = isEnabled,
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
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_speed_test_title).uppercase(),
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
                                var isRunFocused by remember { mutableStateOf(false) }
                                TvSurface(
                                    onClick = onRunSpeedTest,
                                    modifier = Modifier.height(40.dp).onFocusChanged { isRunFocused = it.isFocused },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = AppColors.ArenaOrange,
                                        focusedContainerColor = Color.White
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.sm)),
                                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = STSpacing.md).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        Text(
                                            stringResource(R.string.settings_speed_test_run),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = if (isRunFocused) AppColors.ArenaOrange else Color.Black
                                        )
                                    }
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
private fun TvGeneralSettingsContent(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
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
                    TvLanguageChip(
                        label = "English",
                        isActive = currentLanguage == "en",
                        onClick = { onLanguageChange("en") },
                        modifier = Modifier.weight(1f)
                    )
                    TvLanguageChip(
                        label = "العربية",
                        isActive = currentLanguage == "ar",
                        onClick = { onLanguageChange("ar") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TvLanguageChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
}

@Composable
private fun TvPlaybackSettingsContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    context: android.content.Context
) {
    var showPlayerEngineDialog by remember { mutableStateOf(false) }
    var showBufferDialog by remember { mutableStateOf(false) }
    var showDecoderDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(STSpacing.md),
        verticalArrangement = Arrangement.spacedBy(STSpacing.lg)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_playback_options),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                TvSettingClickableItem(
                    title = stringResource(R.string.settings_player_engine),
                    subtitle = if (uiState.playerEngineMode == "VLC") stringResource(R.string.settings_player_vlc) else stringResource(R.string.settings_player_exo),
                    icon = Icons.Default.PlayCircle,
                    onClick = { showPlayerEngineDialog = true }
                )

                TvSettingToggleItem(
                    title = stringResource(R.string.settings_prevent_standby),
                    subtitle = stringResource(R.string.settings_prevent_standby_subtitle),
                    icon = Icons.Default.BrightnessHigh,
                    checked = uiState.preventStandby,
                    onCheckedChange = { viewModel.setPreventStandby(it) }
                )

                TvSettingToggleItem(
                    title = stringResource(R.string.settings_auto_play_next_episode),
                    subtitle = stringResource(R.string.settings_auto_play_next_episode_subtitle),
                    icon = Icons.Default.SkipNext,
                    checked = uiState.autoPlayNextEpisode,
                    onCheckedChange = { viewModel.setAutoPlayNextEpisode(it) }
                )

                TvSettingToggleItem(
                    title = stringResource(R.string.settings_zap_auto_revert),
                    subtitle = stringResource(R.string.settings_zap_auto_revert_subtitle),
                    icon = Icons.Default.Undo,
                    checked = uiState.zapAutoRevert,
                    onCheckedChange = { viewModel.setZapAutoRevert(it) }
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_engine_preferences),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                TvSettingClickableItem(
                    title = stringResource(R.string.settings_live_buffer_size),
                    subtitle = formatLiveBufferLabel(uiState.liveBufferSize, context),
                    icon = Icons.Default.Dns,
                    onClick = { showBufferDialog = true }
                )

                TvSettingClickableItem(
                    title = stringResource(R.string.settings_decoder_mode),
                    subtitle = formatDecoderModeLabel(uiState.decoderMode, context),
                    icon = Icons.Default.Hardware,
                    onClick = { showDecoderDialog = true }
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_subtitle_size).uppercase(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                ) {
                    subtitleSizeOptions().forEach { option ->
                        val isActive = uiState.subtitleStyle.textScale == option.scale
                        TvSubFilterChip(
                            label = stringResource(option.labelResId),
                            icon = Icons.Default.FormatSize,
                            isActive = isActive,
                            onClick = { viewModel.setSubtitleStyle(uiState.subtitleStyle.copy(textScale = option.scale)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_subtitle_text_color).uppercase(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                ) {
                    subtitleTextColorOptions().forEach { option ->
                        val isActive = uiState.subtitleStyle.foregroundColorArgb == option.colorArgb
                        TvSubtitleColorChip(
                            label = stringResource(option.labelResId),
                            color = Color(option.colorArgb),
                            isActive = isActive,
                            onClick = { viewModel.setSubtitleStyle(uiState.subtitleStyle.copy(foregroundColorArgb = option.colorArgb)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_subtitle_background).uppercase(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
                ) {
                    subtitleBackgroundColorOptions().forEach { option ->
                        val isActive = uiState.subtitleStyle.backgroundColorArgb == option.colorArgb
                        TvSubtitleColorChip(
                            label = stringResource(option.labelResId),
                            color = Color(option.colorArgb),
                            isActive = isActive,
                            onClick = { viewModel.setSubtitleStyle(uiState.subtitleStyle.copy(backgroundColorArgb = option.colorArgb)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.generic_preview),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Surface(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    color = Color.Black,
                    shape = RoundedCornerShape(STRadius.lg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(remember { Brush.linearGradient(listOf(Color(0xFF1A2A6C), Color(0xFFB21F1F), Color(0xFFFDBB2D))) })
                        )
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
        TvSettingsSelectionDialog(
            title = stringResource(R.string.settings_select_buffer_size),
            onDismiss = { showBufferDialog = false }
        ) {
            options.forEach { size ->
                TvRadioOptionRow(
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

    if (showPlayerEngineDialog) {
        val options = listOf("VLC", "EXO")
        TvSettingsSelectionDialog(
            title = stringResource(R.string.settings_player_engine),
            onDismiss = { showPlayerEngineDialog = false }
        ) {
            options.forEach { mode ->
                TvRadioOptionRow(
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

    if (showDecoderDialog) {
        val options = listOf("AUTO", "HARDWARE", "SOFTWARE")
        TvSettingsSelectionDialog(
            title = stringResource(R.string.settings_select_decoder_mode),
            onDismiss = { showDecoderDialog = false }
        ) {
            options.forEach { mode ->
                TvRadioOptionRow(
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
}

@Composable
private fun TvSettingClickableItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.lg)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
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
                Icon(icon, null, tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = subtitle, color = if (isFocused) Color.Black.copy(alpha = 0.7f) else AppColors.ArenaOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TvSettingsSelectionDialog(
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
private fun TvRadioOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Transparent,
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
            border = Border(BorderStroke(2.dp, if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = if (isFocused) Color.Black else if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp
            )
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isFocused) Color.Black else AppColors.ArenaOrange,
                    unselectedColor = (if (isFocused) Color.Black else Color.White).copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
private fun TvSettingToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.lg)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
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
                Icon(icon, null, tint = if (isFocused) Color.Black else if (checked) AppColors.ArenaOrange else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = subtitle, color = if (isFocused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f), fontSize = 11.sp, lineHeight = 15.sp)
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
private fun TvSubFilterChip(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
            border = Border(BorderStroke(2.dp, if (isActive) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent))
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = if (isFocused) Color.Black else if (isActive) Color.White else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            Text(
                text = label,
                color = if (isFocused) Color.Black else if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TvSubtitleColorChip(
    label: String,
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
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
                color = when {
                    isFocused -> Color.Black
                    isActive -> Color.White
                    else -> Color.White.copy(alpha = 0.6f)
                },
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TvSidebarItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = { onFocused() },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            },
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = STSpacing.sm, vertical = STSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(if (isSelected || isFocused) AppColors.ArenaOrange else Color.Transparent, RoundedCornerShape(50))
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isFocused -> Color.Black
                    isSelected -> Color.White
                    else -> Color.White.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = when {
                    isFocused -> Color.Black
                    isSelected -> Color.White
                    else -> Color.White.copy(alpha = 0.6f)
                },
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvAccountSettingsCard(
    account: XtreamAccount,
    liveCount: Int,
    movieCount: Int,
    seriesCount: Int,
    epgCount: Int,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onClearCache: () -> Unit
) {
    val cardBackground = remember {
        Brush.linearGradient(
            colors = listOf(Color(0xFF1A1C1E), Color(0xFF0D0E10))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(STRadius.xl))
            .background(cardBackground)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(STRadius.xl))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(220.dp)
                .offset(x = 90.dp, y = (-90).dp)
                .background(
                    remember {
                        Brush.radialGradient(
                            colors = listOf(AppColors.ArenaOrange.copy(alpha = 0.16f), Color.Transparent)
                        )
                    }
                )
        )

        Column(
            modifier = Modifier.padding(STSpacing.md),
            verticalArrangement = Arrangement.spacedBy(STSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(STSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                        Text(
                            text = account.name.ifBlank { "ArenaLive" },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)
            ) {
                TvSubscriptionMetricCard(
                    title = stringResource(R.string.nav_live_tv),
                    count = liveCount,
                    icon = Icons.Default.PlayArrow,
                    accentColor = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                TvSubscriptionMetricCard(
                    title = stringResource(R.string.nav_movies),
                    count = movieCount,
                    icon = Icons.Default.Movie,
                    accentColor = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
                TvSubscriptionMetricCard(
                    title = stringResource(R.string.nav_series),
                    count = seriesCount,
                    icon = Icons.Default.Tv,
                    accentColor = Color(0xFFE91E63),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(STSpacing.sm)
            ) {
                TvSettingsActionButton(
                    label = if (isSyncing) stringResource(R.string.settings_syncing) else stringResource(R.string.settings_sync_data),
                    onClick = onRefresh,
                    isPrimary = true,
                    modifier = Modifier.weight(1.5f).height(44.dp)
                )
                TvSettingsActionButton(
                    label = stringResource(R.string.settings_edit_account_btn),
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(44.dp)
                )
                TvSettingsActionButton(
                    label = stringResource(R.string.settings_cache_btn),
                    onClick = onClearCache,
                    modifier = Modifier.weight(1f).height(44.dp)
                )
            }
        }
    }
}

@Composable
private fun TvSubscriptionMetricCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(STRadius.md),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(STSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
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
private fun TvSettingsActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPrimary) AppColors.ArenaOrange else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(STRadius.sm)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label.uppercase(),
                color = when {
                    isPrimary && isFocused -> AppColors.ArenaOrange
                    isFocused -> Color.Black
                    else -> Color.White
                },
                fontWeight = FontWeight.Black,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TvLogoutButton(onLogout: () -> Unit) {
    val logoutColor = Color(0xFFEF5350)
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
        Row(
            modifier = Modifier.padding(STSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(STSpacing.md)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = if (isFocused) Color.White else logoutColor, modifier = Modifier.size(20.dp))
            Column {
                Text(stringResource(R.string.nav_logout), color = if (isFocused) Color.White else logoutColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = stringResource(R.string.nav_logout_subtitle),
                    color = if (isFocused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun TvAboutSettingsContent(
    uiState: SettingsUiState,
    onCheckUpdate: () -> Unit
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(STRadius.lg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(STSpacing.md), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvAboutInfoRow(label = stringResource(R.string.settings_about_app_name), value = "ArenaLive")
                        TvAboutInfoRow(label = stringResource(R.string.settings_about_developed_by), value = "Elit Bahrain")
                        TvAboutInfoRow(label = stringResource(R.string.settings_about_build_version), value = "1.0.0")
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(STSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_update_status),
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
                    Column(modifier = Modifier.padding(STSpacing.md), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvAboutInfoRow(label = stringResource(R.string.settings_about_latest_release), value = uiState.latestVersion ?: stringResource(R.string.generic_unknown))
                        TvAboutInfoRow(
                            label = stringResource(R.string.settings_about_update_status),
                            value = uiState.updateStatus,
                            valueColor = if (uiState.updateStatus.contains("available", ignoreCase = true)) AppColors.ArenaOrange else Color.Green.copy(alpha = 0.8f)
                        )

                        val lastChecked = if (uiState.lastUpdateCheckTimestamp > 0) {
                            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(uiState.lastUpdateCheckTimestamp))
                        } else stringResource(R.string.generic_never)

                        TvAboutInfoRow(label = stringResource(R.string.settings_about_last_checked), value = lastChecked)

                        Spacer(modifier = Modifier.height(8.dp))

                        TvSettingsActionButton(
                            label = if (uiState.isCheckingForUpdates) stringResource(R.string.generic_checking) else stringResource(R.string.settings_about_check_updates),
                            onClick = onCheckUpdate,
                            isPrimary = true,
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvAboutInfoRow(
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
private fun TvEditAccountDialog(
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
                    keyboardOptions = KeyboardOptions(
                        hintLocales = ComposeLocaleList(ComposeLocale("en"), ComposeLocale("ar"))
                    ),
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
                    keyboardOptions = KeyboardOptions(
                        hintLocales = ComposeLocaleList(ComposeLocale("en"), ComposeLocale("ar"))
                    ),
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