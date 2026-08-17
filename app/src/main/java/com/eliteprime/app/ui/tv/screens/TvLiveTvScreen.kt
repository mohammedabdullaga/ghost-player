package com.eliteprime.app.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.screens.live.LiveViewModel
import com.eliteprime.app.ui.tv.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val CATEGORIES_PANE_WEIGHT = 0.30f
private const val CHANNELS_PANE_WEIGHT = 0.32f
private const val PREVIEW_PANE_WEIGHT = 0.38f

@Composable
fun TvLiveTvScreen(
    onBack: () -> Unit,
    onPlayChannel: (LiveStream) -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val previewChannel by viewModel.previewChannel.collectAsState()
    val epgList by viewModel.currentEpg.collectAsState()
    
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var selectedChannelForOptions by remember { mutableStateOf<LiveStream?>(null) }
    var selectedCategoryForOptions by remember { mutableStateOf<Category?>(null) }
    
    var lastFocusedCategoryId by remember { mutableStateOf<Long?>(null) }
    var lastFocusedChannelId by remember { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()

    val categoryFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val channelFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val firstCategoryRequester = remember { FocusRequester() }
    val firstChannelRequester = remember { FocusRequester() }
    val addGroupFocusRequester = remember { FocusRequester() }

    val isAnyPopupOpen = showAddGroupDialog || selectedChannelForOptions != null || selectedCategoryForOptions != null
    
    // COMMUNICATE WITH SHELL TO LOCK NAVBAR
    val focusManager = LocalTvFocusManager.current
    LaunchedEffect(isAnyPopupOpen) {
        focusManager.value = isAnyPopupOpen
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.value = false // RESET ON LEAVE
            if (viewModel.globalPlayerState.mode.value != PlayerViewMode.FULLSCREEN) {
                viewModel.globalPlayerState.hide()
                viewModel.playerEngine.stop()
            }
        }
    }

    BackHandler { 
        if (isAnyPopupOpen) {
            showAddGroupDialog = false
            selectedChannelForOptions = null
            selectedCategoryForOptions = null
        } else if (uiState.isCategoryReorderMode) {
            viewModel.exitCategoryReorderMode()
        } else if (uiState.isChannelReorderMode) {
            viewModel.exitChannelReorderMode()
        } else {
            onBack()
        }
    }

    // ROBUST FOCUS RESTORATION: Request focus immediately when popup closes
    LaunchedEffect(isAnyPopupOpen) {
        if (!isAnyPopupOpen) {
            try {
                if (lastFocusedChannelId != null) {
                    channelFocusRequesters[lastFocusedChannelId]?.requestFocus()
                } else if (lastFocusedCategoryId != null) {
                    categoryFocusRequesters[lastFocusedCategoryId]?.requestFocus()
                }
            } catch (e: Exception) {}
        }
    }

    AppBackground(isTransparent = true) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TvGlassPane(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // --- 1. CATEGORIES ---
                    Column(modifier = Modifier.weight(CATEGORIES_PANE_WEIGHT).fillMaxHeight()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.isCategoryReorderMode) stringResource(R.string.group_reorder) else stringResource(R.string.generic_groups), 
                                color = TvColors.TextSecondary, 
                                fontWeight = FontWeight.Black, 
                                fontSize = 11.sp, 
                                letterSpacing = 1.5.sp
                            )
                            
                            if (uiState.isCategoryReorderMode) {
                                TvSurfaceCard(
                                    onClick = { viewModel.saveCategoryOrder() },
                                    modifier = Modifier.align(Alignment.CenterEnd).height(32.dp),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = TvColors.Primary, focusedContainerColor = Color.White)
                                ) {
                                    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.generic_done), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp))
                                    }
                                }
                            } else {
                                TvSurfaceCard(
                                    onClick = { showAddGroupDialog = true },
                                    modifier = Modifier.align(Alignment.CenterEnd).size(32.dp).focusRequester(addGroupFocusRequester),
                                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), focusedContainerColor = Color.White)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add, 
                                        contentDescription = null, 
                                        tint = TvColors.Primary, 
                                        modifier = Modifier.size(18.dp).align(Alignment.Center)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = TvColors.GlassBorder)

                        LazyColumn(
                            state = categoryListState,
                            modifier = Modifier.fillMaxSize(), 
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            itemsIndexed(uiState.categories, key = { _, cat -> cat.id }) { index, category ->
                                val requester = if (index == 0) firstCategoryRequester else categoryFocusRequesters.getOrPut(category.id) { FocusRequester() }

                                if (uiState.isCategoryReorderMode) {
                                    TvReorderRow(
                                        label = category.categoryName,
                                        onMoveUp = { viewModel.moveCategoryUp(category) },
                                        onMoveDown = { viewModel.moveCategoryDown(category) }
                                    )
                                } else {
                                    TvCategoryItem(
                                        category = category,
                                        isSelected = category.id == uiState.selectedCategory?.id,
                                        modifier = Modifier.fillMaxWidth()
                                            .focusRequester(requester)
                                            .onFocusChanged { if (it.isFocused) {
                                                lastFocusedCategoryId = category.id
                                                lastFocusedChannelId = null
                                            }}
                                            .focusProperties {
                                                if (index == 0) up = addGroupFocusRequester
                                            },
                                        onClick = {
                                            if (!isAnyPopupOpen) {
                                                viewModel.selectCategory(category)
                                                scope.launch { channelListState.scrollToItem(0) }
                                            }
                                        },
                                        onLongClick = { selectedCategoryForOptions = category }
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.fillMaxHeight(0.9f).align(Alignment.CenterVertically), color = TvColors.Primary, thickness = 1.dp)

                    // --- 2. CHANNELS ---
                    Column(modifier = Modifier.weight(CHANNELS_PANE_WEIGHT).fillMaxHeight()) {
                        Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.isChannelReorderMode) stringResource(R.string.channel_reorder) else (uiState.selectedCategory?.categoryName ?: "").uppercase(), 
                                color = TvColors.TextSecondary, 
                                fontWeight = FontWeight.Black, 
                                fontSize = 11.sp, 
                                letterSpacing = 1.5.sp
                            )
                            
                            if (uiState.isChannelReorderMode) {
                                TvSurfaceCard(
                                    onClick = { viewModel.saveChannelOrder() },
                                    modifier = Modifier.align(Alignment.CenterEnd).height(32.dp),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = TvColors.Primary, focusedContainerColor = Color.White)
                                ) {
                                    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.generic_done), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = TvColors.GlassBorder)

                        LazyColumn(
                            state = channelListState, 
                            modifier = Modifier.fillMaxSize(), 
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            itemsIndexed(uiState.filteredChannels, key = { _, c -> c.id }) { index, channel ->
                                val requester = if (index == 0) firstChannelRequester else channelFocusRequesters.getOrPut(channel.id) { FocusRequester() }
                                
                                if (uiState.isChannelReorderMode) {
                                    TvReorderRow(
                                        label = channel.name,
                                        onMoveUp = { viewModel.moveChannelUp(channel) },
                                        onMoveDown = { viewModel.moveChannelDown(channel) }
                                    )
                                } else {
                                    TvChannelItem(
                                        channel = channel,
                                        isSelected = previewChannel?.id == channel.id,
                                        modifier = Modifier.fillMaxWidth()
                                            .focusRequester(requester)
                                            .onFocusChanged { if (it.isFocused) {
                                                lastFocusedChannelId = channel.id
                                            } },
                                        onClick = {
                                            if (!isAnyPopupOpen) {
                                                if (previewChannel?.id == channel.id) {
                                                    viewModel.confirmChannel(channel)
                                                    viewModel.globalPlayerState.enterFullScreen(viewModel.playerEngine)
                                                    onPlayChannel(channel)
                                                } else {
                                                    viewModel.previewChannel(channel)
                                                }
                                            }
                                        },
                                        onLongClick = { selectedChannelForOptions = channel }
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.fillMaxHeight(0.9f).align(Alignment.CenterVertically), color = TvColors.Primary, thickness = 1.dp)

                    // --- 3. PREVIEW & EPG ---
                    Column(modifier = Modifier.weight(PREVIEW_PANE_WEIGHT).fillMaxHeight().padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .onGloballyPositioned { coords ->
                                    viewModel.globalPlayerState.updatePreviewBounds(coords.boundsInRoot())
                                }
                                .border(1.dp, TvColors.GlassBorder, RoundedCornerShape(12.dp))
                        ) {
                            if (previewChannel != null) {
                                LaunchedEffect(previewChannel) {
                                    viewModel.globalPlayerState.enterPreview(viewModel.playerEngine)
                                }
                            } else {
                                Icon(Icons.Default.PlayCircle, null, modifier = Modifier.align(Alignment.Center).size(48.dp), tint = TvColors.TextTertiary)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (previewChannel != null) {
                            Text(text = previewChannel!!.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
                            TvBadge(text = stringResource(R.string.live_now), containerColor = TvColors.Primary.copy(alpha = 0.2f), contentColor = TvColors.Primary)
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = TvColors.GlassBorder)

                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(epgList) { _, program ->
                                    TvEpgItem(program)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- DIALOGS ---

        if (showAddGroupDialog) {
            val firstActionRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(300)
                try { firstActionRequester.requestFocus() } catch (e: Exception) {}
            }

            TvOverlayDialog(onDismiss = { showAddGroupDialog = false }) {
                Text(stringResource(R.string.group_create_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text(stringResource(R.string.group_name_label), color = Color.White.copy(alpha = 0.3f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(firstActionRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TvColors.Primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvSurfaceCard(
                        onClick = { 
                            viewModel.addCustomGroup(newGroupName)
                            showAddGroupDialog = false
                            newGroupName = ""
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ClickableSurfaceDefaults.colors(containerColor = TvColors.Primary, focusedContainerColor = Color.White)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.generic_create), color = Color.Black, fontWeight = FontWeight.Black)
                        }
                    }
                    TvSurfaceCard(
                        onClick = { showAddGroupDialog = false },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), focusedContainerColor = Color.White)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.generic_cancel), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        selectedChannelForOptions?.let { channel ->
            var canInteract by remember { mutableStateOf(false) }
            val firstActionRequester = remember { FocusRequester() }
            LaunchedEffect(channel) { 
                delay(400)
                canInteract = true 
                try { firstActionRequester.requestFocus() } catch (e: Exception) {}
            }

            TvOverlayDialog(onDismiss = { if (canInteract) selectedChannelForOptions = null }) {
                Text(channel.name.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                TvBadge(text = stringResource(R.string.channel_options), containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)
                Spacer(Modifier.height(24.dp))
                
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        TvDialogActionItem(
                            label = if (channel.isFavorite) stringResource(R.string.fav_remove) else stringResource(R.string.fav_add),
                            icon = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            enabled = canInteract,
                            modifier = Modifier.focusRequester(firstActionRequester)
                        ) {
                            viewModel.toggleFavorite(channel)
                            selectedChannelForOptions = null
                        }
                    }
                    item {
                        TvDialogActionItem(
                            label = stringResource(R.string.channel_reorder),
                            icon = Icons.Default.Reorder,
                            enabled = canInteract
                        ) {
                            viewModel.enterChannelReorderMode()
                            selectedChannelForOptions = null
                        }
                    }
                    item {
                        TvDialogActionItem(
                            label = stringResource(R.string.live_hide_channel),
                            icon = Icons.Default.VisibilityOff,
                            enabled = canInteract
                        ) {
                            viewModel.hideChannel(channel)
                            selectedChannelForOptions = null
                        }
                    }

                    val currentCat = uiState.selectedCategory
                    if (currentCat != null && currentCat.id >= 1000000) {
                        item {
                            TvDialogActionItem(
                                label = stringResource(R.string.channel_remove_from_group),
                                icon = Icons.Default.DeleteSweep,
                                color = Color.Red.copy(alpha = 0.8f),
                                enabled = canInteract
                            ) {
                                viewModel.removeFromGroup(channel, currentCat)
                                selectedChannelForOptions = null
                            }
                        }
                    }

                    val otherCustomGroups = uiState.categories.filter { it.id >= 1000000 && it.id != currentCat?.id }
                    if (otherCustomGroups.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.group_add_to), color = TvColors.Primary, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp, start = 12.dp))
                        }
                        itemsIndexed(otherCustomGroups) { _, group ->
                            TvDialogActionItem(label = group.categoryName, icon = Icons.Default.FolderOpen, enabled = canInteract) {
                                viewModel.addToGroup(channel, group)
                                selectedChannelForOptions = null
                            }
                        }
                    }
                }
            }
        }

        selectedCategoryForOptions?.let { category ->
            var canInteract by remember { mutableStateOf(false) }
            val firstActionRequester = remember { FocusRequester() }
            LaunchedEffect(category) { 
                delay(400)
                canInteract = true 
                try { firstActionRequester.requestFocus() } catch (e: Exception) {}
            }

            TvOverlayDialog(onDismiss = { if (canInteract) selectedCategoryForOptions = null }) {
                Text(category.categoryName.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                TvBadge(text = stringResource(R.string.group_options), containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)
                Spacer(Modifier.height(24.dp))
                
                TvDialogActionItem(
                    label = stringResource(R.string.group_reorder), 
                    icon = Icons.Default.Reorder, 
                    enabled = canInteract,
                    modifier = Modifier.focusRequester(firstActionRequester)
                ) {
                    viewModel.enterCategoryReorderMode()
                    selectedCategoryForOptions = null
                }
                if (category.id >= 1000000) {
                    TvDialogActionItem(label = stringResource(R.string.group_delete), icon = Icons.Default.Delete, color = Color.Red, enabled = canInteract) {
                        viewModel.deleteCustomGroup(category)
                        selectedCategoryForOptions = null
                    }
                }
            }
        }
    }
}

@Composable
private fun TvOverlayDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .onPreviewKeyEvent {
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    onDismiss()
                    true
                } else false
            }
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .focusProperties {
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            },
        contentAlignment = Alignment.Center
    ) {
        TvGlassPane(
            modifier = Modifier
                .width(440.dp)
                .wrapContentHeight()
                .clickable(enabled = false, onClick = {}),
            backgroundColor = Color(0xFF12151B),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
            ) {
                content()
            }
        }
    }
}

@Composable
private fun TvCategoryItem(
    category: Category,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .heightIn(min = 40.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.25f) else Color.Transparent,
            focusedContainerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(3.dp).height(20.dp).background(if (isSelected || isFocused) TvColors.Primary else Color.Transparent, CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = category.categoryName, 
                color = if (isFocused) Color.Black else Color.White, 
                style = MaterialTheme.typography.bodyMedium, 
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
private fun TvChannelItem(
    channel: LiveStream,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusProperties { 
                right = FocusRequester.Cancel 
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) TvColors.Primary.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                AsyncImage(model = channel.streamIcon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = channel.name, 
                color = if (isFocused) Color.Black else Color.White, 
                fontSize = 14.sp, 
                fontWeight = FontWeight.Bold, 
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvReorderRow(
    label: String,
    modifier: Modifier = Modifier,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    TvGlassPane(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .heightIn(min = 40.dp),
        backgroundColor = Color.White.copy(alpha = 0.05f),
        borderColor = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label, 
                color = Color.White, 
                modifier = Modifier.weight(1f), 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var isUpFocused by remember { mutableStateOf(false) }
                TvSurfaceCard(
                    onClick = onMoveUp,
                    modifier = Modifier.size(32.dp).onFocusChanged { isUpFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowUpward, null, tint = if (isUpFocused) Color.Black else TvColors.Primary, modifier = Modifier.size(16.dp))
                    }
                }
                
                var isDownFocused by remember { mutableStateOf(false) }
                TvSurfaceCard(
                    onClick = onMoveDown,
                    modifier = Modifier.size(32.dp).onFocusChanged { isDownFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowDownward, null, tint = if (isDownFocused) Color.Black else TvColors.Primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDialogActionItem(
    label: String, 
    icon: ImageVector, 
    color: Color = Color.White, 
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    TvSurfaceCard(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 16.dp), 
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = if (isFocused) Color.Black else color, modifier = Modifier.size(20.dp))
            Text(text = label, color = if (isFocused) Color.Black else color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvEpgItem(program: EPGProgram) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val time = remember(program.startTimestamp) {
             SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(program.startTimestamp))
        }
        Text(text = time, color = TvColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(text = program.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
