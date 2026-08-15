package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import coil3.compose.AsyncImage
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.EPGProgram
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.player.PlayerViewMode
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.screens.live.LiveViewModel
import com.eliteprime.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private object TvLivePalette {
    val paneFill = Color.Black.copy(alpha = 0.65f)
    val paneBorder = Color.White.copy(alpha = 0.12f)
    val hairline = Color.White.copy(alpha = 0.1f)
    val textSecondary = Color.White.copy(alpha = 0.6f)
    val textTertiary = Color.White.copy(alpha = 0.38f)
}

@Composable
fun TvLiveTvScreen(
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onPlayChannel: (LiveStream) -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val previewChannel by viewModel.previewChannel.collectAsState()
    val epgList by viewModel.currentEpg.collectAsState()
    val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    
    val channelListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    val categoryFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val channelFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val firstCategoryRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        onDispose {
            if (viewModel.globalPlayerState.mode.value != PlayerViewMode.FULLSCREEN) {
                viewModel.globalPlayerState.hide()
                viewModel.playerEngine.stop()
            }
        }
    }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            delay(200)
            val targetId = uiState.selectedCategory?.id ?: -1L
            categoryFocusRequesters[targetId]?.requestFocus() ?: firstCategoryRequester.requestFocus()
        }
    }

    AppBackground(isTransparent = true) {
        Column(modifier = Modifier.fillMaxSize()) {
            TvLiveTopNavBar(
                currentTime = currentTime.value,
                onBack = onBack,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToMovies = onNavigateToMovies,
                onNavigateToSeries = onNavigateToSeries
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- PANE 1: Categories ---
                TvPaneSurface(modifier = Modifier.weight(0.28f)) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                            Text(text = stringResource(R.string.generic_groups).uppercase(), color = TvLivePalette.textSecondary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TvLivePalette.hairline))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            itemsIndexed(uiState.categories) { index, category ->
                                val requester = if (index == 0) firstCategoryRequester else categoryFocusRequesters.getOrPut(category.id) { FocusRequester() }
                                
                                TvCategoryTab(
                                    category = category,
                                    isSelected = category.id == uiState.selectedCategory?.id,
                                    modifier = Modifier.focusRequester(requester),
                                    onClick = { viewModel.selectCategory(category) }
                                )
                            }
                        }
                    }
                }

                // --- PANE 2: Channels ---
                TvPaneSurface(modifier = Modifier.weight(0.32f)) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                            Text(text = (uiState.selectedCategory?.categoryName ?: "").uppercase(), color = TvLivePalette.textSecondary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TvLivePalette.hairline))
                        
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(uiState.filteredChannels, key = { it.id }) { channel ->
                                val requester = channelFocusRequesters.getOrPut(channel.id) { FocusRequester() }
                                TvChannelRow(
                                    channel = channel,
                                    isSelected = previewChannel?.id == channel.id,
                                    modifier = Modifier.focusRequester(requester),
                                    onClick = { 
                                        if (previewChannel?.id == channel.id) {
                                            viewModel.confirmChannel(channel)
                                            viewModel.globalPlayerState.enterFullScreen(viewModel.playerEngine)
                                            onPlayChannel(channel)
                                        } else {
                                            viewModel.previewChannel(channel)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // --- PANE 3: Preview & EPG ---
                TvPaneSurface(modifier = Modifier.weight(0.4f), backgroundColor = Color.Transparent) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .padding(12.dp)
                                .onGloballyPositioned { coords ->
                                    viewModel.globalPlayerState.updatePreviewBounds(coords.boundsInRoot())
                                }
                                .border(1.dp, TvLivePalette.paneBorder, RoundedCornerShape(16.dp))
                        ) {
                            if (previewChannel != null) {
                                LaunchedEffect(previewChannel) {
                                    viewModel.globalPlayerState.enterPreview(viewModel.playerEngine)
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(48.dp), tint = TvLivePalette.textTertiary)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(TvLivePalette.paneFill, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                                .padding(16.dp)
                        ) {
                            if (previewChannel != null) {
                                Text(text = stringResource(R.string.live_now), color = AppColors.ArenaOrange, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                Text(text = previewChannel!!.name, color = Color.White, style = TvMaterialTheme.typography.titleMedium, maxLines = 1)
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TvLivePalette.hairline))
                                
                                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(epgList) { program ->
                                        TvEpgRow(program)
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.live_select_channel_prompt), color = TvLivePalette.textTertiary, fontSize = 13.sp)
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
private fun TvCategoryTab(
    category: Category,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp).onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.45f),
            focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(if (isSelected || isFocused) AppColors.ArenaOrange else Color.Transparent))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = category.categoryName, color = Color.White, fontSize = 13.sp, fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun TvChannelRow(
    channel: LiveStream,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp).onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f),
            focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                if (channel.streamIcon != null) {
                    AsyncImage(model = channel.streamIcon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(6.dp))
                } else {
                    Icon(Icons.Default.Tv, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = channel.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = stringResource(R.string.live_no_epg_data), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TvEpgRow(program: EPGProgram) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val time = remember(program.startTimestamp) {
             SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(program.startTimestamp))
        }
        Text(text = time, color = TvLivePalette.textSecondary, fontSize = 12.sp)
        Text(text = program.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TvLiveTopNavBar(
    currentTime: String,
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ArenaLiveLogo(textSize = 20.sp)
        Spacer(modifier = Modifier.width(32.dp))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            NavTabButton(label = stringResource(R.string.nav_home), isActive = false, onClick = onBack)
            NavTabButton(label = stringResource(R.string.nav_live_tv), isActive = true, onClick = {})
            NavTabButton(label = stringResource(R.string.nav_movies), isActive = false, onClick = onNavigateToMovies)
            NavTabButton(label = stringResource(R.string.nav_series), isActive = false, onClick = onNavigateToSeries)
        }
        TvRoundIconButton(icon = Icons.Default.Search, onClick = onNavigateToSearch)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = currentTime, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvRoundIconButton(icon: ImageVector, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = Modifier.size(40.dp).onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.45f), focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TvPaneSurface(
    modifier: Modifier = Modifier, 
    backgroundColor: Color = TvLivePalette.paneFill,
    content: @Composable () -> Unit
) {
    TvSurface(
        modifier = modifier.fillMaxHeight(),
        onClick = {},
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = backgroundColor
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, TvLivePalette.paneBorder)),
            focusedBorder = Border(BorderStroke(1.dp, TvLivePalette.paneBorder))
        )
    ) {
        content()
    }
}
