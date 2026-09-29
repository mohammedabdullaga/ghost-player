package com.eliteprime.app.ui.tv.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
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
    val PanelDark = Color(0xFF0C101A) // Deep Midnight Obsidian Glass
    val Background = Color(0xFF070A12)
    val GlassBorder = Color.White.copy(alpha = 0.18f)
}

// Control dashboard visibility of Fast VPN (set to true to restore anytime)
private const val SHOW_FAST_VPN_ON_DASHBOARD = false

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
    onNavigateToSports: () -> Unit = {},
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

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Top Header Utilities (Search, Settings, Reload)
                            HubIconButton(
                                icon = Icons.Default.Search,
                                contentDescription = stringResource(R.string.nav_search),
                                onClick = { logNav("Search", onNavigateToSearch) }
                            )
                            HubIconButton(
                                icon = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.nav_settings),
                                onClick = { logNav("Settings", onNavigateToSettings) }
                            )
                            HubIconButton(
                                icon = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.hub_reload),
                                isLoading = uiState.isSyncing,
                                onClick = {
                                    if (!uiState.isSyncing) {
                                        viewModel.reloadData { success ->
                                            Toast.makeText(context, if (success) syncSuccessMsg else syncFailedMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            if (!uiState.formattedExpiryDate.isNullOrBlank()) {
                                Surface(
                                    color = Color(0xFF0F172A).copy(alpha = 0.70f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.2.dp,
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF00E5FF).copy(alpha = 0.5f), Color.White.copy(alpha = 0.2f))
                                        )
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0xFF00E676))
                                        )
                                        Text(
                                            text = stringResource(R.string.hub_expires_format, uiState.formattedExpiryDate!!),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = time, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text(text = date, color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Column 1: Live TV (Top) + Sports Card (Placed directly below Live TV)
                    Column(
                        modifier = Modifier.weight(1.25f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HubCard(
                            title = stringResource(R.string.nav_live_tv),
                            subtitle = "تصفح جميع القنوات المباشرة",
                            icon = Icons.Default.LiveTv,
                            iconRes = R.drawable.arena_tv_icon,
                            count = uiState.liveCount,
                            countLabel = "قناة",
                            extraChip = "HD / 4K",
                            featured = true,
                            focusRequester = firstTileRequester,
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            onClick = { logNav("Live TV", onNavigateToLive) }
                        )
                        HubCard(
                            title = stringResource(R.string.nav_sports),
                            subtitle = "شاهد الفعاليات والمباريات المباشرة",
                            icon = Icons.Default.EmojiEvents,
                            count = null,
                            countLabel = null,
                            extraChip = "مباشر",
                            featured = true,
                            focusRequester = null,
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            onClick = { logNav("Sports", onNavigateToSports) }
                        )
                    }

                    // Column 2: Movies Card
                    HubCard(
                        title = stringResource(R.string.nav_movies),
                        subtitle = "شاهد أحدث وأفضل الأفلام",
                        icon = Icons.Default.Movie,
                        count = uiState.movieCount,
                        countLabel = "فيلم",
                        featured = false,
                        focusRequester = null,
                        modifier = Modifier.weight(1f).height(336.dp),
                        onClick = { logNav("Movies", onNavigateToMovies) }
                    )

                    // Column 3: Series Card
                    HubCard(
                        title = stringResource(R.string.nav_series),
                        subtitle = "مجموعة كبيرة من المسلسلات",
                        icon = Icons.Default.VideoLibrary,
                        count = uiState.seriesCount,
                        countLabel = "مسلسل",
                        featured = false,
                        focusRequester = null,
                        modifier = Modifier.weight(1f).height(336.dp),
                        onClick = { logNav("Series", onNavigateToSeries) }
                    )
                }

                Spacer(Modifier.weight(0.5f))
            } else {
                // Top Bar (mobile)
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
                            icon = Icons.Default.LiveTv, iconRes = R.drawable.arena_tv_icon, count = uiState.liveCount, countLabel = "قناة",
                            featured = false, focusRequester = null, isTv = false,
                            modifier = Modifier.weight(1f), onClick = { logNav("Mobile Live TV", onNavigateToLive) }
                        )
                        HubCard(
                            title = stringResource(R.string.nav_sports), subtitle = "شاهد الفعاليات والمباريات المباشرة",
                            icon = Icons.Default.EmojiEvents, count = null, countLabel = null,
                            featured = false, focusRequester = null, isTv = false,
                            modifier = Modifier.weight(1f), onClick = { logNav("Mobile Sports", onNavigateToSports) }
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
    iconRes: Int? = null,
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

    val glassBackground = Brush.verticalGradient(
        colors = if (isFocused) {
            listOf(
                Color.White.copy(alpha = 0.28f),
                Color(0xFF1E293B).copy(alpha = 0.60f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color(0xFF0F172A).copy(alpha = 0.40f)
            )
        }
    )

    val glassBorderBrush = Brush.linearGradient(
        colors = if (isFocused) {
            listOf(Color(0xFFFFC107), Color(0xFFFF9800))
        } else {
            listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.12f)
            )
        }
    )

    val content = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(if (isTv) 18.dp else 16.dp))
                .background(glassBackground)
                .border(
                    border = BorderStroke(if (isFocused) 2.5.dp else 1.2.dp, glassBorderBrush),
                    shape = RoundedCornerShape(if (isTv) 18.dp else 16.dp)
                )
        ) {
            // Top diagonal glass reflection sheen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                            start = Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(400f, 400f)
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
                            .size(
                                if (iconRes != null) (if (isTv) 98.dp else 64.dp)
                                else (if (isTv) 52.dp else 38.dp)
                            )
                            .clip(RoundedCornerShape(if (isTv) 13.dp else 9.dp))
                            .background(
                                if (iconRes != null) Color.Transparent
                                else if (featured) AppColors.ArenaOrange.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconRes != null) {
                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(if (isTv) 85.dp else 56.dp)
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (featured) AppColors.ArenaOrange else Color.White,
                                modifier = Modifier.size(if (isTv) 24.dp else 18.dp)
                            )
                        }
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
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = if (isTv) 15.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                if (count != null && countLabel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HubChip(text = "$count $countLabel")
                        if (extraChip != null) {
                            HubChip(text = extraChip)
                        }
                    }
                }
            }
        }
    }

    if (isTv) {
        val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f, label = "hub_card_scale")
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .onFocusChanged { isFocused = it.isFocused }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(0.dp, Color.Transparent)),
                border = Border(BorderStroke(0.dp, Color.Transparent))
            )
        ) {
            content()
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun HubChip(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = Color.White,
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
        val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f, label = "toggle_row_scale")
        val glassBg = if (isFocused) {
            AppColors.ArenaOrange
        } else {
            Color(0xFF0F172A).copy(alpha = if (isAvailable) 0.65f else 0.35f)
        }
        val glassBorder = if (isFocused) {
            BorderStroke(2.5.dp, Color.White)
        } else {
            BorderStroke(
                1.2.dp,
                if (isEnabled && isAvailable) AppColors.ArenaOrange.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.22f)
            )
        }

        TvSurface(
            onClick = { if (isAvailable) onToggle(!isEnabled) },
            modifier = modifier
                .height(72.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(glassBg)
                    .border(glassBorder, RoundedCornerShape(16.dp))
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
                                    isEnabled -> Color.Black.copy(alpha = 0.2f)
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



@Composable
private fun HubIconButton(
    icon: ImageVector,
    contentDescription: String,
    isLoading: Boolean = false,
    accentColor: Color = Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, label = "icon_btn_scale")

    val glassBg = if (isFocused) Color.White else Color(0xFF0F172A).copy(alpha = 0.65f)
    val glassBorder = if (isFocused) {
        BorderStroke(2.5.dp, Color(0xFFFFC107))
    } else {
        BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f))
    }

    TvSurface(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(glassBg)
                .border(glassBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = if (isFocused) Color.Black else Color(0xFFFFC107),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (isFocused) Color.Black else accentColor,
                    modifier = Modifier.size(24.dp)
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
        val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f, label = "util_row_scale")
        val glassBg = if (isFocused) Color.White else Color(0xFF0F172A).copy(alpha = 0.65f)
        val glassBorder = if (isFocused) {
            BorderStroke(2.5.dp, Color(0xFFFFC107))
        } else {
            BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f))
        }

        TvSurface(
            onClick = onClick,
            modifier = modifier
                .height(72.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(glassBg)
                    .border(glassBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = if (isFocused) Color.Black else Color(0xFFFFC107),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            icon,
                            null,
                            tint = if (isFocused) Color.Black else accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        color = if (isFocused) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
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