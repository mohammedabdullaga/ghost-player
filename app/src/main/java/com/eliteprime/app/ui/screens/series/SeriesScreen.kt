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
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private object VodSpacing {
    val lg = 20.dp
    val xs = 8.dp
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
            SeriesTopNavBar(
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
                            columns = GridCells.Adaptive(140.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(uiState.filteredSeries) { series ->
                                SeriesPosterCard(
                                    series = series,
                                    onClick = { onSeriesClick(series) }
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
private fun SeriesTopNavBar(
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
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.06f), CircleShape)
            ) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            ArenaLiveLogo(textSize = 20.sp)

            Spacer(modifier = Modifier.width(8.dp))

            NavTabButton(label = "Live TV", icon = Icons.Default.LiveTv, isActive = false, onClick = onNavigateToLiveTv)
            NavTabButton(label = "Movies", icon = Icons.Default.Movie, isActive = title == "MOVIES", onClick = onNavigateToMovies)
            NavTabButton(label = "Series", icon = Icons.Default.VideoLibrary, isActive = title == "SERIES", onClick = {})

            IconButton(
                onClick = onNavigateToSearch,
                modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.06f), CircleShape)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(20.dp))
            }

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
