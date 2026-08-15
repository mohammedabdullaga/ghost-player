package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val selectionOrange = Color(0xFFFF8C00)
}

private const val CATEGORIES_PANE_WEIGHT = 0.30f
private const val CHANNELS_PANE_WEIGHT = 0.35f
private const val PREVIEW_PANE_WEIGHT = 0.35f

@Composable
fun TvLiveTvScreen(
    onBack: () -> Unit,
    onPlayChannel: (LiveStream) -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val previewChannel by viewModel.previewChannel.collectAsState()
    val epgList by viewModel.currentEpg.collectAsState()

    var hoveredCategory by remember { mutableStateOf<Category?>(null) }
    val scope = rememberCoroutineScope()
    val channelListState = rememberLazyListState()

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

    // Landing Focus
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            delay(200)
            val targetId = uiState.selectedCategory?.id ?: -1L
            categoryFocusRequesters[targetId]?.requestFocus() ?: firstCategoryRequester.requestFocus()
        }
    }

    // Auto-preview the first channel once the list loads, if nothing is
    // already selected.
    LaunchedEffect(uiState.filteredChannels) {
        if (previewChannel == null && uiState.filteredChannels.isNotEmpty()) {
            viewModel.previewChannel(uiState.filteredChannels.first())
        }
    }

    AppBackground(isTransparent = true) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            TvSurface(
                modifier = Modifier.fillMaxSize(),
                onClick = {},
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvLivePalette.paneFill,
                    focusedContainerColor = TvLivePalette.paneFill
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, TvLivePalette.paneBorder)),
                    focusedBorder = Border(BorderStroke(1.dp, TvLivePalette.paneBorder))
                )
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // --- SECTION 1: Categories ---
                    Column(modifier = Modifier.weight(CATEGORIES_PANE_WEIGHT).fillMaxHeight()) {
                        Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                            Text(text = stringResource(R.string.generic_groups).uppercase(), color = TvLivePalette.textSecondary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                        }
                        HorizontalDivider(color = TvLivePalette.hairline)

                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                            itemsIndexed(uiState.categories) { index, category ->
                                val requester = if (index == 0) firstCategoryRequester else categoryFocusRequesters.getOrPut(category.id) { FocusRequester() }

                                TvCategoryTab(
                                    category = category,
                                    isSelected = category.id == uiState.selectedCategory?.id,
                                    modifier = Modifier.focusRequester(requester),
                                    onFocused = { hoveredCategory = category },
                                    onClick = {
                                        viewModel.selectCategory(category)
                                        scope.launch { channelListState.scrollToItem(0) }
                                    }
                                )
                            }
                        }
                    }

                    TvSectionDivider()

                    // --- SECTION 2: Channels ---
                    Column(modifier = Modifier.weight(CHANNELS_PANE_WEIGHT).fillMaxHeight()) {
                        Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                            Text(text = (uiState.selectedCategory?.categoryName ?: "").uppercase(), color = TvLivePalette.textSecondary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                        }
                        HorizontalDivider(color = TvLivePalette.hairline)

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

                    TvSectionDivider()

                    // --- SECTION 3: Preview & EPG ---
                    Column(modifier = Modifier.weight(PREVIEW_PANE_WEIGHT).fillMaxHeight().padding(16.dp)) {
                        // Preview Hole
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .onGloballyPositioned { coords ->
                                    viewModel.globalPlayerState.updatePreviewBounds(coords.boundsInRoot())
                                }
                                .border(1.dp, TvLivePalette.paneBorder, RoundedCornerShape(12.dp))
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // EPG / Info
                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (previewChannel != null) {
                                Text(text = previewChannel!!.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = TvLivePalette.hairline)

                                if (epgList.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.live_no_epg_data), color = TvLivePalette.textTertiary, fontSize = 13.sp)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(epgList) { program ->
                                            TvEpgRow(program)
                                        }
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
private fun RowScope.TvSectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight(0.88f)
            .width(1.5.dp)
            .align(Alignment.CenterVertically)
            .background(TvLivePalette.selectionOrange.copy(alpha = 0.45f))
    )
}

@Composable
private fun TvCategoryTab(
    category: Category,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 2.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvLivePalette.selectionOrange.copy(alpha = 0.25f) else Color.Transparent,
            focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(3.dp).height(18.dp).background(if (isSelected || isFocused) TvLivePalette.selectionOrange else Color.Transparent, CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = category.categoryName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
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
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp).onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
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
                Text(text = channel.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(text = program.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
