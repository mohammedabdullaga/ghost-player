package com.eliteprime.app.ui.screens.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.eliteprime.app.R
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.ArenaLiveLogo
import com.eliteprime.app.ui.components.NavTabButton
import com.eliteprime.app.ui.screens.dashboard.DashboardViewModel
import com.eliteprime.app.ui.theme.AppColors
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

@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var showExitDialog by remember { mutableStateOf(false) }

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
                    title = stringResource(R.string.nav_live_tv).uppercase(),
                    description = "Watch live channels from around the world",
                    icon = Icons.Default.LiveTv,
                    accentColor = Color(0xFFFF4500),
                    onClick = onNavigateToLiveTv,
                    modifier = Modifier.weight(1f).aspectRatio(1.5f)
                )
                HomeHeroCard(
                    title = stringResource(R.string.nav_movies).uppercase(),
                    description = "Explore thousands of movies in high quality",
                    icon = Icons.Default.Movie,
                    accentColor = Color(0xFFFFB300),
                    onClick = onNavigateToMovies,
                    modifier = Modifier.weight(1f).aspectRatio(1.5f)
                )
                HomeHeroCard(
                    title = stringResource(R.string.nav_series).uppercase(),
                    description = "Binge the best TV shows and exclusive series",
                    icon = Icons.Default.Tv,
                    accentColor = Color(0xFF00BFFF),
                    onClick = onNavigateToSeries,
                    modifier = Modifier.weight(1f).aspectRatio(1.5f)
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
            IconButton(
                onClick = { /* Already Home */ },
                modifier = Modifier.size(40.dp).background(AppColors.ArenaOrange.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = AppColors.ArenaOrange, modifier = Modifier.size(20.dp))
            }

            ArenaLiveLogo(textSize = 20.sp)

            Spacer(modifier = Modifier.width(HomeSpacing.xs))

            NavTabButton(label = stringResource(R.string.nav_live_tv), icon = Icons.Default.LiveTv, isActive = false, onClick = onNavigateToLiveTv)
            NavTabButton(label = stringResource(R.string.nav_movies), icon = Icons.Default.Movie, isActive = false, onClick = onNavigateToMovies)
            NavTabButton(label = stringResource(R.string.nav_series), icon = Icons.Default.VideoLibrary, isActive = false, onClick = onNavigateToSeries)

            HomeRoundIconButton(icon = Icons.Default.Search, contentDescription = "Search", onClick = onNavigateToSearch)
            HomeRoundIconButton(icon = Icons.Default.FileDownload, contentDescription = "Downloads", onClick = onNavigateToDownloads)
            HomeRoundIconButton(icon = Icons.Default.Settings, contentDescription = "Settings", onClick = onNavigateToSettings)

            Spacer(modifier = Modifier.weight(1f))

            Text(text = currentTime, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
    }
}

@Composable
private fun HomeRoundIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.06f), CircleShape)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
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
                    text = "EXPIRES ON",
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                val scale = if (isFocused) 1.05f else 1f
                scaleX = scale
                scaleY = scale
            },
        color = Color.Transparent,
        shape = RoundedCornerShape(HomeRadius.xl)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(HomeRadius.xl))
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused || isPressed) accentColor else Color.White.copy(alpha = 0.08f),
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
                        alpha = if (isPressed || isFocused) 1f else 0.7f
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(HomeSpacing.md)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
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
                            .background(accentColor, RoundedCornerShape(1.dp))
                    )

                    Surface(
                        onClick = onClick,
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = accentColor,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
        title = { Text("Exit App", fontWeight = FontWeight.Bold) },
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
