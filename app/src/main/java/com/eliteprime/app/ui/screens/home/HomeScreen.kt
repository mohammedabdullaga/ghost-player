package com.eliteprime.app.ui.screens.home

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.google.firebase.crashlytics.FirebaseCrashlytics
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.eliteprime.app.R
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.screens.dashboard.TrendingItem
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val HomeCardRadius = 20.dp
private val HomeCardBaseGradient = Brush.linearGradient(
    listOf(Color(0xFF1E2E40), Color(0xFF101B28), Color(0xFF080D14))
)
private val TrendingRotationInterval = 6000L

private object HomeSpacing {
    val xl = 24.dp
}

private enum class HomeFocusIndex { NAV, TRENDING, CONTINUE, DOWNLOADS }

@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToTrending: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToContinueWatching: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var showExitDialog by remember { mutableStateOf(false) }

    val isTv = context.isTelevisionDevice()
    val trendingFocusRequester = remember { FocusRequester() }
    val continueFocusRequester = remember { FocusRequester() }
    val downloadsFocusRequester = remember { FocusRequester() }
    val homeNavFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var lastFocusedIndex by rememberSaveable { mutableStateOf(HomeFocusIndex.NAV) }

    // Restore focus on initial load AND when returning from other screens
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (isTv) {
            scope.launch {
                // Intense focus "grabber" to prevent jumping to navbar
                repeat(15) {
                    try {
                        when (lastFocusedIndex) {
                            HomeFocusIndex.NAV -> homeNavFocusRequester.requestFocus()
                            HomeFocusIndex.TRENDING -> trendingFocusRequester.requestFocus()
                            HomeFocusIndex.CONTINUE -> continueFocusRequester.requestFocus()
                            HomeFocusIndex.DOWNLOADS -> downloadsFocusRequester.requestFocus()
                        }
                    } catch (e: Exception) {}
                    delay(30)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("Entering Full Home Screen (isTv=$isTv)")
        Log.e("HomeScreen", "CRASH_CHECK_LOG: Home screen visible")
        while (true) {
            currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        ExitConfirmationDialog(
            onConfirm = { activity?.finish() },
            onDismiss = { showExitDialog = false }
        )
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopNavBar(
                currentTime = currentTime.value,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToLiveTv = onNavigateToLiveTv,
                onNavigateToMovies = onNavigateToMovies,
                onNavigateToSeries = onNavigateToSeries,
                homeNavFocusRequester = homeNavFocusRequester,
                onHomeNavFocused = { lastFocusedIndex = HomeFocusIndex.NAV }
            )

            val lastWatched = uiState.continueWatching.firstOrNull()
            val continueDesc = if (lastWatched != null) {
                if (lastWatched.type == "series" || lastWatched.type == "series_episode") {
                    " — S${lastWatched.seasonNumber} · E${lastWatched.episodeNumber}, ${lastWatched.name}"
                } else lastWatched.name
            } else stringResource(R.string.home_history_empty)
            val continueProgress = lastWatched?.let { if (it.durationMs > 0) it.lastPositionMs.toFloat() / it.durationMs.toFloat() else 0f }

            val trendingItems = uiState.trendingMovies.ifEmpty { uiState.trendingSeries }

            val trendingCardHeightFraction = if (isTv) 0.85f else 0.97f

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrendingHeroCard(
                    items = trendingItems,
                    onClick = onNavigateToTrending,
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight(trendingCardHeightFraction)
                        .focusRequester(trendingFocusRequester)
                        .onFocusChanged { if (it.isFocused) lastFocusedIndex = HomeFocusIndex.TRENDING }
                )
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight(0.85f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HomeHeroCard(
                        title = stringResource(R.string.home_continue_watching),
                        description = continueDesc,
                        backgroundUrl = lastWatched?.backdropUrl ?: lastWatched?.posterUrl,
                        accentColor = Color(0xFFFF4500),
                        onClick = onNavigateToContinueWatching,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusRequester(continueFocusRequester)
                            .onFocusChanged { if (it.isFocused) lastFocusedIndex = HomeFocusIndex.CONTINUE },
                        progress = continueProgress
                    )
                    HomeHeroCard(
                        title = stringResource(R.string.home_downloads),
                        description = stringResource(R.string.home_downloads_desc),
                        backgroundUrl = uiState.latestDownloadPoster,
                        accentColor = Color(0xFF00BFFF),
                        onClick = onNavigateToDownloads,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .alpha(if (uiState.downloadsCount == 0) 0.7f else 1f)
                            .focusRequester(downloadsFocusRequester)
                            .onFocusChanged { if (it.isFocused) lastFocusedIndex = HomeFocusIndex.DOWNLOADS },
                        badgeLabel = if (uiState.completedDownloadsCount > 0) "${uiState.completedDownloadsCount} ${stringResource(R.string.home_downloads_ready)}" else null
                    )
                }
            }
        }

        uiState.providerHealth.expirationDate?.let { expiryMs ->
            val date = remember(expiryMs) { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(expiryMs)) }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(HomeSpacing.xl)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_plan_ends), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = date, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HomeTopNavBar(
    currentTime: String,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    homeNavFocusRequester: FocusRequester,
    onHomeNavFocused: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArenaLiveLogo(textSize = 22.sp)

        Spacer(modifier = Modifier.width(48.dp))

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTabButton(
                label = stringResource(R.string.nav_home), 
                isActive = true, 
                onClick = {},
                modifier = Modifier.focusRequester(homeNavFocusRequester).onFocusChanged { if (it.isFocused) onHomeNavFocused() }
            )
            NavTabButton(label = stringResource(R.string.nav_live_tv), isActive = false, onClick = onNavigateToLiveTv)
            NavTabButton(label = stringResource(R.string.nav_movies), isActive = false, onClick = onNavigateToMovies)
            NavTabButton(label = stringResource(R.string.nav_series), isActive = false, onClick = onNavigateToSeries)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeRoundIconButton(icon = Icons.Default.Search, contentDescription = "Search", onClick = onNavigateToSearch)
            HomeRoundIconButton(icon = Icons.Default.Tune, contentDescription = "Settings", onClick = onNavigateToSettings)

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = currentTime,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun HomeRoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.White,
                pressedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(2.dp, if (isActive) AppColors.ArenaOrange.copy(alpha = 0.85f) else Color.Transparent)),
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = if (isFocused) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp).background(if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (isActive) AppColors.ArenaOrange else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun HomeHeroCard(
    title: String,
    description: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundUrl: String? = null,
    progress: Float? = null,
    badgeLabel: String? = null
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val shape = remember { RoundedCornerShape(HomeCardRadius) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border.None
            )
        ) {
            HomeHeroCardContent(title, description, backgroundUrl, accentColor, isFocused, progress, badgeLabel, shape)
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier,
            color = Color.Transparent,
            shape = shape
        ) {
            HomeHeroCardContent(title, description, backgroundUrl, accentColor, isPressed, progress, badgeLabel, shape)
        }
    }
}

@Composable
private fun HomeHeroCardContent(
    title: String,
    description: String,
    backgroundUrl: String?,
    accentColor: Color,
    isActive: Boolean,
    progress: Float?,
    badgeLabel: String?,
    shape: RoundedCornerShape
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(HomeCardBaseGradient)
            .border(
                width = 1.dp,
                color = if (isActive) accentColor else Color.White.copy(alpha = 0.08f),
                shape = shape
            )
    ) {
        if (backgroundUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backgroundUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = if (isActive) 0.8f else 0.5f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = if (isActive) 0.38f else 0.22f), Color.Transparent),
                            center = Offset(size.width, size.height),
                            radius = size.width * 1.15f
                        )
                    )
                }
        )

        if (badgeLabel != null) {
            Surface(
                color = accentColor,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
            ) {
                Text(
                    text = badgeLabel,
                    color = Color(0xFF14100A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = title.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 17.sp,
                letterSpacing = (-0.1).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (progress != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(accentColor)
                    )
                }
            }
        }
    }
}

@Composable
fun TrendingHeroCard(
    items: List<TrendingItem<*>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val shape = remember { RoundedCornerShape(HomeCardRadius) }

    var index by remember(items) { mutableIntStateOf(0) }
    LaunchedEffect(items) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(TrendingRotationInterval)
            index = (index + 1) % items.size
        }
    }
    val current = items.getOrNull(index)

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border.None
            )
        ) {
            TrendingHeroCardContent(current, itemCount = items.size, currentIndex = index, isActive = isFocused, shape = shape)
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier,
            color = Color.Transparent,
            shape = shape
        ) {
            TrendingHeroCardContent(current, itemCount = items.size, currentIndex = index, isActive = isPressed, shape = shape)
        }
    }
}

@Composable
private fun TrendingHeroCardContent(
    item: TrendingItem<*>?,
    itemCount: Int,
    currentIndex: Int,
    isActive: Boolean,
    shape: RoundedCornerShape
) {
    val accentColor = Color(0xFFFF3D00)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(HomeCardBaseGradient)
            .border(
                width = 1.dp,
                color = if (isActive) accentColor else Color.White.copy(alpha = 0.08f),
                shape = shape
            )
    ) {
        Crossfade(targetState = item, animationSpec = tween(600), label = "trendingCardItem") { crossfadeItem ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (crossfadeItem != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(crossfadeItem.backdropUrl ?: crossfadeItem.posterUrl)
                            .build(),
                        contentDescription = crossfadeItem.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.88f))
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.2f), Color.Transparent),
                                    center = Offset(size.width, size.height),
                                    radius = size.width * 0.75f 
                                )
                            )
                        }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 26.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.home_trending_title),
                            color = accentColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        if (itemCount > 1) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(itemCount.coerceAtMost(8)) { dotIndex ->
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (dotIndex == currentIndex) accentColor else Color.White.copy(alpha = 0.25f)
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = crossfadeItem?.title ?: stringResource(R.string.home_trending_fallback),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (crossfadeItem != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFF9C27B0), modifier = Modifier.size(13.dp))
                            Text(
                                text = "%.1f".format(crossfadeItem.voteAverage),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            crossfadeItem.releaseDate?.take(4)?.let { year ->
                                Text(
                                    text = "· $year",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (!crossfadeItem?.overview.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.6.dp))
                        Text(
                            text = crossfadeItem!!.overview,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExitConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_logout_confirm_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.exit_confirm_msg)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.generic_yes), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.generic_no), color = Color.White.copy(alpha = 0.6f))
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color(0xFF12151B),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.7f)
    )
}
