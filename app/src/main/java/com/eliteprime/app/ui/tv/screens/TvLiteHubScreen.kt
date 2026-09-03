package com.eliteprime.app.ui.tv.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.eliteprime.app.BuildConfig
import com.eliteprime.app.MainActivity
import com.eliteprime.app.R
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.ArenaLiveLogo
import com.eliteprime.app.ui.components.TvConfirmDialog
import com.eliteprime.app.ui.components.TvExitDialog
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private object HubBrand {
    // Neutral gray, not brownish — the previous 0xFF141010 had a red
    // channel bias that read as "dark brown" rather than a clean
    // neutral tone.
    val PanelDark = Color(0xFF1B1C1F)
    val Background = Color(0xFF0C0F14)
    val GlassBorder = Color.White.copy(alpha = 0.14f)
}

/**
 * Live-ticking clock, driven by real system time — not fabricated data.
 * Matches the reference mockup's top-bar clock.
 */
@Composable
private fun rememberNowStrings(): Pair<String, String> {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000L)
        }
    }
    val time = remember(now) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now) }
    return time to date
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
        FirebaseCrashlytics.getInstance().log("Entering Lite Hub Screen (isTv=$isTv)")
        if (isTv) {
            delay(200)
            try { firstTileRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    BackHandler { showExitDialog = true }

    if (showSwitchModeDialog) {
        TvConfirmDialog(
            title = stringResource(R.string.settings_lite_mode_restart_title),
            message = stringResource(R.string.settings_lite_mode_restart_msg),
            confirmText = stringResource(R.string.generic_yes),
            icon = Icons.Default.Refresh,
            onConfirm = {
                showSwitchModeDialog = false
                viewModel.setLiteMode(false)
                (activity as? MainActivity)?.restartApp()
            },
            onDismiss = { showSwitchModeDialog = false }
        )
    }

    if (showExitDialog) {
        TvExitDialog(
            onConfirm = { activity?.finish() },
            onDismiss = { showExitDialog = false }
        )
    }

    fun logNav(label: String, action: () -> Unit) {
        try {
            FirebaseCrashlytics.getInstance().log("Hub -> $label")
            action()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTv) 48.dp else 20.dp, vertical = if (isTv) 28.dp else 24.dp)
        ) {
            if (isTv) {
                run {
                    val (time, date) = rememberNowStrings()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArenaLiveLogo(textSize = 24.sp)

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!uiState.formattedExpiryDate.isNullOrBlank()) {
                                Surface(
                                    color = HubBrand.PanelDark.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(9.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
                                ) {
                                    Text(
                                        text = stringResource(R.string.hub_expires_format, uiState.formattedExpiryDate!!),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = time, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text(text = date, color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(0.5f))

                // Cards defined in natural priority order: Live TV (Start), Movies, Series
                data class CardSpec(
                    val title: String,
                    val subtitle: String,
                    val icon: ImageVector,
                    val count: Int,
                    val countLabel: String,
                    val extraChip: String? = null,
                    val featured: Boolean = false,
                    val weight: Float,
                    val focusRequester: FocusRequester?,
                    val onClick: () -> Unit
                )

                val orderedSpecs = listOf(
                    CardSpec(stringResource(R.string.nav_live_tv), "تصفح جميع القنوات المباشرة", Icons.Default.LiveTv, uiState.liveCount, "قناة", "HD / 4K", true, 1.25f, firstTileRequester, onClick = { logNav("Live TV", onNavigateToLive) }),
                    CardSpec(stringResource(R.string.nav_movies), "شاهد أحدث وأفضل الأفلام", Icons.Default.Movie, uiState.movieCount, "فيلم", weight = 1f, focusRequester = null, onClick = { logNav("Movies", onNavigateToMovies) }),
                    CardSpec(stringResource(R.string.nav_series), "مجموعة كبيرة من المسلسلات", Icons.Default.VideoLibrary, uiState.seriesCount, "مسلسل", weight = 1f, focusRequester = null, onClick = { logNav("Series", onNavigateToSeries) })
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    orderedSpecs.forEach { spec ->
                        HubCard(
                            title = spec.title,
                            subtitle = spec.subtitle,
                            icon = spec.icon,
                            count = spec.count,
                            countLabel = spec.countLabel,
                            extraChip = spec.extraChip,
                            featured = spec.featured,
                            focusRequester = spec.focusRequester,
                            modifier = Modifier.weight(spec.weight).height(280.dp),
                            onClick = spec.onClick
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HubUtilRow(icon = Icons.Default.Search, label = stringResource(R.string.nav_search), modifier = Modifier.weight(1f), onClick = { logNav("Search", onNavigateToSearch) })
                    HubUtilRow(icon = Icons.Default.Settings, label = stringResource(R.string.nav_settings), modifier = Modifier.weight(1f), onClick = { logNav("Settings", onNavigateToSettings) })
                    HubUtilRow(
                        icon = Icons.Default.Refresh,
                        label = if (uiState.isSyncing) stringResource(R.string.hub_syncing) else stringResource(R.string.hub_reload),
                        isLoading = uiState.isSyncing,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!uiState.isSyncing) {
                                viewModel.reloadData { success ->
                                    Toast.makeText(context, if (success) syncSuccessMsg else syncFailedMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    HubToggleRow(
                        label = stringResource(R.string.hub_fast_vpn),
                        isEnabled = uiState.isSecureConnectionEnabled,
                        isAvailable = uiState.isByeDpiAvailable,
                        isTv = true,
                        modifier = Modifier.weight(1.8f),
                        onToggle = { enabled -> viewModel.setSecureConnectionEnabled(enabled) }
                    )
                }

                Spacer(Modifier.weight(0.5f))
            } else {
                // Top Bar (mobile — unchanged from before)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (!uiState.formattedExpiryDate.isNullOrBlank()) {
                        Surface(
                            color = HubBrand.PanelDark.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, AppColors.ArenaOrange.copy(alpha = 0.8f))
                        ) {
                            Text(
                                text = stringResource(R.string.hub_expires_format, uiState.formattedExpiryDate!!),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HubCard(
                            title = stringResource(R.string.nav_live_tv), subtitle = "تصفح جميع القنوات المباشرة",
                            icon = Icons.Default.LiveTv, count = uiState.liveCount, countLabel = "قناة",
                            featured = false, focusRequester = null, isTv = false,
                            modifier = Modifier.weight(1f), onClick = { logNav("Mobile Live TV", onNavigateToLive) }
                        )
                        HubCard(
                            title = stringResource(R.string.nav_movies), subtitle = "شاهد أحدث وأفضل الأفلام",
                            icon = Icons.Default.Movie, count = uiState.movieCount, countLabel = "فيلم",
                            featured = false, focusRequester = null, isTv = false,
                            modifier = Modifier.weight(1f), onClick = { logNav("Mobile Movies", onNavigateToMovies) }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HubCard(
                            title = stringResource(R.string.nav_series), subtitle = "مجموعة كبيرة من المسلسلات",
                            icon = Icons.Default.VideoLibrary, count = uiState.seriesCount, countLabel = "مسلسل",
                            featured = false, focusRequester = null, isTv = false,
                            modifier = Modifier.weight(1f), onClick = { logNav("Mobile Series", onNavigateToSeries) }
                        )
                        HubCard(
                            title = stringResource(R.string.nav_search), subtitle = "ابحث عن أي محتوى",
                            icon = Icons.Default.Search, count = null, countLabel = null,
                            featured = false, focusRequester = null, isTv = false,
                            modifier = Modifier.weight(1f), onClick = { logNav("Mobile Search", onNavigateToSearch) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                        HubUtilRow(icon = Icons.Default.Settings, label = stringResource(R.string.nav_settings), isTv = false, modifier = Modifier.height(56.dp), onClick = { logNav("Mobile Settings", onNavigateToSettings) })
                        HubUtilRow(
                            icon = Icons.Default.Refresh,
                            label = if (uiState.isSyncing) stringResource(R.string.hub_syncing) else stringResource(R.string.hub_reload),
                            isTv = false, isLoading = uiState.isSyncing,
                            modifier = Modifier.height(56.dp),
                            onClick = {
                                if (!uiState.isSyncing) {
                                    viewModel.reloadData { success -> Toast.makeText(context, if (success) syncSuccessMsg else syncFailedMsg, Toast.LENGTH_SHORT).show() }
                                }
                            }
                        )
                        HubToggleRow(
                            label = stringResource(R.string.hub_fast_vpn),
                            isEnabled = uiState.isSecureConnectionEnabled,
                            isAvailable = uiState.isByeDpiAvailable,
                            isTv = false,
                            modifier = Modifier.height(56.dp),
                            onToggle = { enabled -> viewModel.setSecureConnectionEnabled(enabled) }
                        )
                    }
            }

            // Bottom Bar
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "v${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
            }
        }
    }
}

/**
 * Dashboard-style card, matching the approved mockup: icon chip, title,
 * subtitle, a count chip with a real unit label, and (for the featured
 * card only) a static quality chip. Sized for TV viewing distance —
 * significantly larger type than a mobile/desktop card would use.
 */
@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    count: Int?,
    countLabel: String?,
    featured: Boolean = false,
    extraChip: String? = null,
    focusRequester: FocusRequester?,
    isTv: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Deliberately NO full-color fill anywhere — not on focus, not on
    // the "featured" card. A large saturated block (a full-white focus
    // fill, or a full-orange gradient sitting there constantly) is
    // genuinely fatiguing on a TV screen in a dim room, confirmed on
    // device. Every card keeps the SAME calm dark background at all
    // times; the only accent is a colored BORDER, which is far cheaper
    // on the eyes over a long viewing session while still being
    // perfectly clear as an indicator. A faint (6% alpha) tint layer
    // adds gentle per-card color variety on top of that same dark base
    // — nowhere near the intensity of a full saturated fill.
    val content = @Composable {
        // One shared "glass" treatment for every card — a soft white
        // sheen fading from the top corner, same on all three, instead
        // of a different hue per card. Distinction between "featured"
        // (Live TV) and the others comes entirely from the border
        // accent below, not from a mismatched color per tile.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        radius = 420f
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(if (isTv) 26.dp else 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(if (isTv) 52.dp else 38.dp)
                        .clip(RoundedCornerShape(if (isTv) 13.dp else 9.dp))
                        .background(
                            if (featured) AppColors.ArenaOrange.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (featured) AppColors.ArenaOrange else Color.White,
                        modifier = Modifier.size(if (isTv) 24.dp else 18.dp)
                    )
                }
                Spacer(Modifier.height(if (isTv) 16.dp else 8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isTv) 26.sp else 17.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = if (isTv) 15.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            if (count != null && countLabel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HubChip(text = "$count $countLabel", featured = featured)
                    if (extraChip != null) {
                        HubChip(text = extraChip, featured = featured)
                    }
                }
            }
        }
    }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .onFocusChanged { isFocused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
            colors = ClickableSurfaceDefaults.colors(
                // Genuinely translucent (not opaque) — the app's own
                // backdrop (the diagonal red-line brand pattern) shows
                // through faintly, giving a real glass effect rather
                // than a decorative sheen on a solid panel.
                containerColor = HubBrand.PanelDark.copy(alpha = 0.55f),
                focusedContainerColor = HubBrand.PanelDark.copy(alpha = 0.55f)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, AppColors.ArenaOrange)),
                border = Border(
                    BorderStroke(
                        if (featured) 1.5.dp else 1.dp,
                        if (featured) AppColors.ArenaOrange.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.16f)
                    )
                )
            )
        ) {
            content()
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = HubBrand.PanelDark,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun HubChip(text: String, featured: Boolean) {
    Surface(
        color = if (featured) AppColors.ArenaOrange.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(9.dp)
    ) {
        Text(
            text = text,
            color = if (featured) AppColors.ArenaOrange else Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * A TOGGLE row (on/off) for Fast VPN / Secure Connection, visually distinct
 * with a track+thumb switch and state indication.
 */
@Composable
private fun HubToggleRow(
    label: String,
    isEnabled: Boolean,
    isAvailable: Boolean = true,
    isTv: Boolean = true,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val iconVector = if (isEnabled && isAvailable) Icons.Default.Shield else Icons.Default.ShieldMoon

    if (isTv) {
        TvSurface(
            onClick = { if (isAvailable) onToggle(!isEnabled) },
            modifier = modifier
                .height(72.dp)
                .onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = HubBrand.PanelDark.copy(alpha = if (isAvailable) 0.55f else 0.3f),
                focusedContainerColor = AppColors.ArenaOrange
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White)),
                border = Border(
                    BorderStroke(
                        1.dp,
                        if (isEnabled && isAvailable) AppColors.ArenaOrange.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.16f)
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        iconVector,
                        contentDescription = null,
                        tint = if (isFocused) Color.Black else if (isEnabled && isAvailable) AppColors.ArenaOrange else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = label,
                        color = if (isFocused) Color.Black else if (isAvailable) Color.White else Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Track + thumb switch — the actual on/off indicator
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            when {
                                !isAvailable -> Color.White.copy(alpha = 0.08f)
                                isFocused -> Color.Black.copy(alpha = 0.25f)
                                isEnabled -> AppColors.ArenaOrange
                                else -> Color.White.copy(alpha = 0.14f)
                            }
                        )
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .align(if (isEnabled && isAvailable) Alignment.CenterEnd else Alignment.CenterStart)
                            .clip(RoundedCornerShape(50))
                            .background(if (isFocused) Color.Black else Color.White)
                    )
                }
            }
        }
    } else {
        Surface(
            onClick = { if (isAvailable) onToggle(!isEnabled) },
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = HubBrand.PanelDark,
            border = BorderStroke(
                1.dp,
                if (isEnabled && isAvailable) AppColors.ArenaOrange.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        iconVector,
                        contentDescription = null,
                        tint = if (isEnabled && isAvailable) AppColors.ArenaOrange else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = label,
                        color = if (isAvailable) Color.White else Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Switch(
                    checked = isEnabled && isAvailable,
                    onCheckedChange = { if (isAvailable) onToggle(it) },
                    enabled = isAvailable,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppColors.ArenaOrange,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.14f)
                    )
                )
            }
        }
    }
}

/**
 * Horizontal utility row (Search / Settings / Reload / Exit), matching
 * the mockup's bottom row treatment — replaces the previous vertical
 * icon-only rail on the screen edge.
 */
@Composable
private fun HubUtilRow(
    icon: ImageVector,
    label: String,
    isTv: Boolean = true,
    isLoading: Boolean = false,
    accentColor: Color = Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier.height(72.dp).onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
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
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = if (isFocused) Color.Black else AppColors.ArenaOrange, strokeWidth = 2.dp)
                } else {
                    Icon(icon, null, tint = if (isFocused) Color.Black else accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(text = label, color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = HubBrand.PanelDark,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.ArenaOrange, strokeWidth = 2.dp)
                } else {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}