package com.eliteprime.app.ui.screens.series

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.eliteprime.app.util.isTelevisionDevice
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private object VodSpacing {
    val lg = 20.dp
    val xs = 8.dp
    val md = 16.dp
}

private object VodPalette {
    val paneFill = Color.Black.copy(alpha = 0.35f)
    val paneBorder = AppColors.ArenaOrange.copy(alpha = 0.14f)
    val hairline = Color.White.copy(alpha = 0.08f)
    val textSecondary = Color.White.copy(alpha = 0.6f)
}

@Composable
fun SeriesScreen(
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onSeriesClick: (Series) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            VodTopNavBar(
                currentTime = currentTime.value,
                title = "SERIES",
                onBack = onBack,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToLiveTv = onNavigateToLiveTv,
                onNavigateToMovies = onNavigateToMovies
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = VodSpacing.lg, vertical = VodSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(VodSpacing.lg)
            ) {
                // --- Pane 1: Categories ---
                VodPaneSurface(modifier = Modifier.weight(0.3f)) {
                    Column {
                        VodPaneHeader(title = "GROUPS")
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            items(uiState.categories) { category ->
                                CategoryTab(
                                    category = category,
                                    isSelected = category.id == uiState.selectedCategory?.id,
                                    onClick = { viewModel.selectCategory(category) }
                                )
                            }
                        }
                    }
                }

                // --- Pane 2: Content Grid ---
                VodPaneSurface(modifier = Modifier.weight(0.7f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        VodPaneHeader(title = uiState.selectedCategory?.categoryName ?: "SERIES")
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(VodSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(VodSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(uiState.filteredSeries) { series ->
                                SeriesPosterCard(
                                    series = series,
                                    onClick = { onSeriesClick(series) },
                                    onLongClick = { viewModel.toggleFavorite(series) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodTopNavBar(
    currentTime: String,
    title: String,
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToMovies: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VodRoundIconButton(
                icon = Icons.Default.Home, 
                contentDescription = "Home", 
                onClick = onBack
            )

            ArenaLiveLogo(textSize = 20.sp)

            Spacer(modifier = Modifier.width(8.dp))

            NavTabButton(label = "Live TV", icon = Icons.Default.LiveTv, isActive = false, onClick = onNavigateToLiveTv)
            NavTabButton(label = "Movies", icon = Icons.Default.Movie, isActive = false, onClick = onNavigateToMovies)
            NavTabButton(label = "Series", icon = Icons.Default.VideoLibrary, isActive = true, onClick = {})

            VodRoundIconButton(
                icon = Icons.Default.Search, 
                contentDescription = "Search", 
                onClick = onNavigateToSearch
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(text = currentTime, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(VodPalette.hairline))
    }
}

@Composable
private fun VodPaneSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = VodPalette.paneFill,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, VodPalette.paneBorder)
    ) {
        content()
    }
}

@Composable
private fun VodPaneHeader(title: String) {
    Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        Text(text = title.uppercase(), color = VodPalette.textSecondary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(VodPalette.hairline))
}

@Composable
private fun VodRoundIconButton(
    icon: ImageVector, 
    contentDescription: String?, 
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isActive) AppColors.ArenaOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                focusedContainerColor = AppColors.ArenaOrange
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
            modifier = Modifier.size(40.dp).background(if (isActive) AppColors.ArenaOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f),
                CircleShape
            )
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
