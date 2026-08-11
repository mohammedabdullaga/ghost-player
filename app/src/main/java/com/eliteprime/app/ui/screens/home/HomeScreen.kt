package com.eliteprime.app.ui.screens.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.eliteprime.app.R
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private object HomeSpacing {
    val xl = 24.dp
    val lg = 20.dp
    val md = 16.dp
    val sm = 12.dp
    val xs = 8.dp
}

private object HomeRadius {
    val xl = 24.dp
    val lg = 20.dp
    val md = 14.dp
}

private enum class HomeFocusIndex { CONTINUE, TRENDING, DOWNLOADS }

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
    val continueFocusRequester = remember { FocusRequester() }
    val trendingFocusRequester = remember { FocusRequester() }
    val downloadsFocusRequester = remember { FocusRequester() }
    
    var lastFocusedIndex by rememberSaveable { mutableStateOf(HomeFocusIndex.CONTINUE) }

    // Restore focus to the card we were on instantly
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(100)
            when (lastFocusedIndex) {
                HomeFocusIndex.CONTINUE -> continueFocusRequester.requestFocus()
                HomeFocusIndex.TRENDING -> trendingFocusRequester.requestFocus()
                HomeFocusIndex.DOWNLOADS -> downloadsFocusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(Unit) {
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
                onNavigateToDownloads = onNavigateToDownloads,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToLiveTv = onNavigateToLiveTv,
                onNavigateToMovies = onNavigateToMovies,
                onNavigateToSeries = onNavigateToSeries
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = HomeSpacing.xl)
                    .padding(bottom = 68.dp),
                horizontalArrangement = Arrangement.spacedBy(HomeSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeHeroCard(
                    title = "CONTINUE WATCHING",
                    description = if (uiState.continueWatching.isEmpty()) "Your history is empty" else "Pick up where you left off",
                    icon = Icons.Default.History,
                    accentColor = Color(0xFFFF4500),
                    onClick = onNavigateToContinueWatching,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.5f)
                        .focusRequester(continueFocusRequester)
                        .onFocusChanged { if (it.isFocused) lastFocusedIndex = HomeFocusIndex.CONTINUE }
                )
                HomeHeroCard(
                    title = "TRENDING THIS WEEK",
                    description = "Most popular content worldwide",
                    icon = Icons.Default.Whatshot,
                    accentColor = Color(0xFFFFB300),
                    onClick = onNavigateToTrending,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.5f)
                        .focusRequester(trendingFocusRequester)
                        .onFocusChanged { if (it.isFocused) lastFocusedIndex = HomeFocusIndex.TRENDING }
                )
                HomeHeroCard(
                    title = "DOWNLOADS LIBRARY",
                    description = "Watch your offline content anytime",
                    icon = Icons.Default.FileDownload,
                    accentColor = Color(0xFF00BFFF),
                    onClick = onNavigateToDownloads,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.5f)
                        .focusRequester(downloadsFocusRequester)
                        .onFocusChanged { if (it.isFocused) lastFocusedIndex = HomeFocusIndex.DOWNLOADS }
                )
            }
        }

        uiState.providerHealth.expirationDate?.let { expiryMs ->
            val date = remember(expiryMs) { SimpleDateFormat("dd / MM / yyyy", Locale.getDefault()).format(Date(expiryMs)) }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = HomeSpacing.md, end = HomeSpacing.xl)
            ) {
                ExpiryBadge(date = date)
            }
        }
    }
}

@Composable
private fun HomeTopNavBar(
    currentTime: String,
    onNavigateToSearch: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HomeSpacing.xl, vertical = HomeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HomeSpacing.sm)
        ) {
            HomeRoundIconButton(
                icon = Icons.Default.Home, 
                contentDescription = stringResource(R.string.nav_home), 
                onClick = {}, 
                isActive = true
            )

            ArenaLiveLogo(textSize = 20.sp)

            Spacer(modifier = Modifier.width(HomeSpacing.xs))

            NavTabButton(label = stringResource(R.string.nav_live_tv), isActive = false, onClick = onNavigateToLiveTv)
            NavTabButton(label = stringResource(R.string.nav_movies), isActive = false, onClick = onNavigateToMovies)
            NavTabButton(label = stringResource(R.string.nav_series), isActive = false, onClick = onNavigateToSeries)

            HomeRoundIconButton(icon = Icons.Default.Search, contentDescription = stringResource(R.string.nav_search), onClick = onNavigateToSearch)
            HomeRoundIconButton(icon = Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings), onClick = onNavigateToSettings)

            Spacer(modifier = Modifier.weight(1f))

            Text(text = currentTime, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
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
                containerColor = if (isActive) AppColors.FocusBrown.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    icon, 
                    contentDescription = contentDescription, 
                    tint = if (isActive || isFocused) Color.White else Color.White.copy(alpha = 0.8f), 
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp).background(if (isActive) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.35f), CircleShape)
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
private fun ExpiryBadge(date: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(HomeRadius.md),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HomeSpacing.sm, vertical = HomeSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HomeSpacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.dashboard_expires),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = date,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun HomeHeroCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(HomeRadius.xl)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = accentColor.copy(alpha = 0.8f) // DYNAMIC COLOR FOCUS
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White))
            )
        ) {
            HomeHeroCardContent(title, description, icon, accentColor, isFocused)
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier,
            color = Color.Transparent,
            shape = RoundedCornerShape(HomeRadius.xl)
        ) {
            HomeHeroCardContent(title, description, icon, accentColor, isPressed)
        }
    }
}

@Composable
private fun HomeHeroCardContent(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(HomeRadius.xl))
            .background(if (isActive) Color.Transparent else Color.Black.copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(HomeRadius.xl)
            )
    ) {
        // Vertical scrim for text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.75f))
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent),
                        radius = 260f
                    ),
                    alpha = if (isActive) 1f else 0.7f
                )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(HomeSpacing.md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color.White else accentColor,
                modifier = Modifier.align(Alignment.End).size(28.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = description,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(0.75f)
            )

            Spacer(modifier = Modifier.height(HomeSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(2.dp)
                        .background(if (isActive) Color.White else accentColor, RoundedCornerShape(1.dp))
                )

                Surface(
                    onClick = {},
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = if (isActive) Color.White else accentColor,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (isActive) AppColors.ArenaOrange else Color.White,
                            modifier = Modifier.size(16.dp)
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
        text = { Text("Are you sure you want to exit?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("YES", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("NO", color = Color.White.copy(alpha = 0.6f))
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color(0xFF12151B),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.7f)
    )
}
