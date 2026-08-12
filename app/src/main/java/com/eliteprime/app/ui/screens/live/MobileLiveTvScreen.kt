package com.eliteprime.app.ui.screens.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil3.compose.AsyncImage
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.EPGProgram
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.player.PlayerViewMode
import com.eliteprime.app.ui.components.*
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private object LivePalette {
    val paneFill = Color.Black.copy(alpha = 0.65f)
    val paneBorder = Color.White.copy(alpha = 0.12f)
    val hairline = Color.White.copy(alpha = 0.1f)
    val textSecondary = Color.White.copy(alpha = 0.6f)
    val textTertiary = Color.White.copy(alpha = 0.38f)
}

@Composable
fun MobileLiveTvScreen(
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
    
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var selectedChannelForOptions by remember { mutableStateOf<LiveStream?>(null) }
    var selectedCategoryForOptions by remember { mutableStateOf<Category?>(null) }

    val channelListState = rememberLazyListState()
    val context = LocalContext.current
    val isTv = context.isTelevisionDevice()
    val channelFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val globalMode by viewModel.globalPlayerState.mode.collectAsState()
    var lastFocusedChannelId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    // Restore focus on every resume (e.g. coming back from player)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (isTv) {
            val targetId = previewChannel?.id ?: lastFocusedChannelId
            if (targetId != null) {
                scope.launch {
                    repeat(25) {
                        val index = uiState.filteredChannels.indexOfFirst { it.id == targetId }
                        if (index >= 0) {
                            if (!channelListState.layoutInfo.visibleItemsInfo.any { it.index == index }) {
                                channelListState.scrollToItem(index)
                            }
                            delay(20)
                            try {
                                channelFocusRequesters[targetId]?.requestFocus()
                                // If we're here, requestFocus was called. 
                                // Since we can't reliably check success, we stop if we tried multiple times 
                                // and the channel is actually visible.
                                if (it > 2) return@launch 
                            } catch (e: Exception) {}
                        }
                        delay(60)
                    }
                }
            }
        }
    }

    // Restore focus to the active channel or last focused instantly
    LaunchedEffect(previewChannel, uiState.selectedCategory, globalMode) {
        if (isTv && globalMode == PlayerViewMode.HIDDEN) {
            val targetId = previewChannel?.id ?: lastFocusedChannelId
            if (targetId != null) {
                val index = uiState.filteredChannels.indexOfFirst { it.id == targetId }
                if (index >= 0) {
                    if (!channelListState.layoutInfo.visibleItemsInfo.any { it.index == index }) {
                        channelListState.scrollToItem(index)
                    }
                    // Minimal delay (50ms) to ensure LazyColumn has attached the item
                    delay(50) 
                    channelFocusRequesters[targetId]?.requestFocus()
                }
            } else if (uiState.filteredChannels.isNotEmpty()) {
                // Default focus on first channel if none selected
                delay(50)
                channelFocusRequesters[uiState.filteredChannels.first().id]?.requestFocus()
            }
        }
    }

    // Reset scroll when category changes
    LaunchedEffect(uiState.selectedCategory) {
        channelListState.scrollToItem(0)
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60000)
        }
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            LiveTopNavBar(
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
                // --- Pane 1: Categories ---
                PaneSurface(modifier = Modifier.weight(0.28f)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = if (uiState.isCategoryReorderMode) "REORDERING" else "GROUPS", color = LivePalette.textSecondary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
                            
                            if (uiState.isCategoryReorderMode) {
                                TextButton(onClick = { viewModel.saveCategoryOrder() }) {
                                    Text("DONE", color = AppColors.ArenaOrange, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                            } else {
                                if (isTv) {
                                    TvSurface(
                                        onClick = { showAddGroupDialog = true },
                                        modifier = Modifier.size(28.dp),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.05f),
                                            focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.9f)
                                        ),
                                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                                        border = ClickableSurfaceDefaults.border(
                                            focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                        )
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Group", tint = AppColors.ArenaOrange, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                } else {
                                    IconButton(onClick = { showAddGroupDialog = true }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Group", tint = AppColors.ArenaOrange, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LivePalette.hairline))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            items(uiState.categories) { category ->
                                if (uiState.isCategoryReorderMode) {
                                    ReorderCategoryRow(
                                        category = category,
                                        onMoveUp = { viewModel.moveCategoryUp(category) },
                                        onMoveDown = { viewModel.moveCategoryDown(category) }
                                    )
                                } else {
                                    CategoryTabWithLongClick(
                                        category = category,
                                        isSelected = category.id == uiState.selectedCategory?.id,
                                        onClick = { viewModel.selectCategory(category) },
                                        onLongClick = { selectedCategoryForOptions = category }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Pane 2: Channel List ---
                PaneSurface(modifier = Modifier.weight(0.32f)) {
                    Column {
                        PaneHeader(title = uiState.selectedCategory?.categoryName ?: "CHANNELS")
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(uiState.filteredChannels, key = { it.id }) { channel ->
                                val focusRequester = channelFocusRequesters.getOrPut(channel.id) { FocusRequester() }
                                ChannelRowWithLongClick(
                                    channel = channel,
                                    isSelected = previewChannel?.id == channel.id,
                                    focusRequester = focusRequester,
                                    onFocusChanged = { if (it.isFocused) lastFocusedChannelId = channel.id },
                                    onClick = { 
                                        if (previewChannel?.id == channel.id) {
                                            viewModel.confirmChannel(channel)
                                            onPlayChannel(channel)
                                        } else {
                                            viewModel.previewChannel(channel)
                                        }
                                    },
                                    onLongClick = { selectedChannelForOptions = channel }
                                )
                            }
                        }
                    }
                }

                // --- Pane 3: Preview & EPG ---
                PaneSurface(modifier = Modifier.weight(0.4f)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Preview Player Area
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .padding(12.dp),
                            color = Color.Black,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, LivePalette.hairline)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (previewChannel != null) {
                                    PlayerRenderView(
                                        playerEngine = viewModel.playerEngine,
                                        globalPlayerState = viewModel.globalPlayerState,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Transparent)
                                            .clickable { 
                                                viewModel.confirmChannel(previewChannel!!)
                                                viewModel.globalPlayerState.enterFullScreen(viewModel.playerEngine)
                                                onPlayChannel(previewChannel!!) 
                                            }
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = LivePalette.textTertiary
                                    )
                                }
                            }
                        }

                        // Channel Info & EPG Area
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            if (previewChannel != null) {
                                Text(
                                    text = "LIVE NOW",
                                    color = AppColors.ArenaOrange,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = previewChannel!!.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "UPCOMING GUIDE",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                                
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LivePalette.hairline))
                                
                                val now = System.currentTimeMillis()
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (epgList.isEmpty()) {
                                        item {
                                            Text(
                                                "No upcoming programs found",
                                                color = LivePalette.textSecondary,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    } else {
                                        items(epgList) { program ->
                                            val isCurrent = now in program.startTimestamp..program.stopTimestamp
                                            EpgRow(program, isCurrent)
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "Select a channel to see guide",
                                        color = LivePalette.textTertiary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Dialogs ---
        
        if (showAddGroupDialog) {
            AlertDialog(
                onDismissRequest = { showAddGroupDialog = false },
                title = { Text("Create New Group") },
                text = {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        placeholder = { Text("Group Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addCustomGroup(newGroupName)
                        showAddGroupDialog = false
                        newGroupName = ""
                    }) { Text("CREATE", color = AppColors.ArenaOrange) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddGroupDialog = false }) { Text("CANCEL") }
                }
            )
        }

        selectedChannelForOptions?.let { channel ->
            AlertDialog(
                onDismissRequest = { selectedChannelForOptions = null },
                title = { Text(channel.name, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        ListItem(
                            headlineContent = { Text(if (channel.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                            leadingContent = { Icon(if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = AppColors.ArenaOrange) },
                            modifier = Modifier.clickable { 
                                viewModel.toggleFavorite(channel)
                                selectedChannelForOptions = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Hide Channel") },
                            leadingContent = { Icon(Icons.Default.VisibilityOff, null) },
                            modifier = Modifier.clickable { 
                                viewModel.hideChannel(channel)
                                selectedChannelForOptions = null
                            }
                        )
                        
                        val groups = uiState.categories.filter { it.id >= 1000000 }
                        if (groups.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = LivePalette.hairline)
                            Text("ADD TO GROUP", style = MaterialTheme.typography.labelSmall, color = AppColors.ArenaOrange, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            groups.forEach { group ->
                                ListItem(
                                    headlineContent = { Text(group.categoryName) },
                                    modifier = Modifier.clickable { 
                                        viewModel.addToGroup(channel, group)
                                        selectedChannelForOptions = null
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedChannelForOptions = null }) { Text("CLOSE") }
                }
            )
        }

        selectedCategoryForOptions?.let { category ->
            var canInteract by remember { mutableStateOf(false) }
            LaunchedEffect(category) {
                delay(400) // Small delay to prevent "click-through" or double-activation
                canInteract = true
            }

            AlertDialog(
                onDismissRequest = { selectedCategoryForOptions = null },
                title = { Text(category.categoryName, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        ListItem(
                            headlineContent = { Text("Add New Group") },
                            leadingContent = { Icon(Icons.Default.Add, null, tint = AppColors.ArenaOrange) },
                            modifier = Modifier.clickable(enabled = canInteract) { 
                                showAddGroupDialog = true
                                selectedCategoryForOptions = null
                            }
                        )
                        if (category.id >= 1000000) {
                            ListItem(
                                headlineContent = { Text("Delete Group", color = Color.Red) },
                                leadingContent = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                modifier = Modifier.clickable(enabled = canInteract) { 
                                    viewModel.deleteCustomGroup(category)
                                    selectedCategoryForOptions = null
                                }
                            )
                        }
                        ListItem(
                            headlineContent = { Text("Reorder Groups") },
                            leadingContent = { Icon(Icons.Default.Reorder, null) },
                            modifier = Modifier.clickable(enabled = canInteract) { 
                                viewModel.enterCategoryReorderMode()
                                selectedCategoryForOptions = null
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { if (canInteract) selectedCategoryForOptions = null }) { Text("CLOSE") }
                },
                containerColor = Color(0xFF1A1C1E),
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}

@Composable
private fun CategoryTabWithLongClick(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f),
                pressedContainerColor = AppColors.FocusBrown
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            CategoryTabContent(category, isSelected || isFocused)
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                },
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.12f) else Color.Transparent,
            shape = RoundedCornerShape(12.dp)
        ) {
            CategoryTabContent(category, isSelected)
        }
    }
}

@Composable
private fun CategoryTabContent(category: Category, isActive: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(if (isActive) AppColors.ArenaOrange else Color.Transparent))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = category.categoryName,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReorderCategoryRow(
    category: Category,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(category.categoryName, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onMoveUp, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ArrowUpward, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ArrowDownward, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ChannelRowWithLongClick(
    channel: LiveStream,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (FocusState) -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    var isFocused by remember { mutableStateOf(false) }

    if (isTv) {
        TvSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .onFocusChanged { 
                    isFocused = it.isFocused
                    onFocusChanged(it)
                }
                .focusRequester(focusRequester),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = AppColors.FocusBrown.copy(alpha = 0.8f),
                pressedContainerColor = AppColors.FocusBrown
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            ChannelRowContent(channel, isSelected || isFocused)
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                },
            color = if (isSelected) AppColors.ArenaOrange.copy(alpha = 0.1f) else Color.Transparent,
            shape = RoundedCornerShape(12.dp)
        ) {
            ChannelRowContent(channel, isSelected)
        }
    }
}

@Composable
private fun ChannelRowContent(channel: LiveStream, isActive: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.streamIcon != null) {
                AsyncImage(model = channel.streamIcon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.4f))
            }
            if (channel.isFavorite) {
                Box(modifier = Modifier.align(Alignment.TopEnd).offset(x=4.dp, y=(-4).dp).size(14.dp).background(AppColors.ArenaOrange, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, color = if (isActive) Color.White else Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("No EPG Data", color = if (isActive) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EpgRow(program: EPGProgram, isCurrent: Boolean) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startTime = remember(program.startTimestamp) { timeFormat.format(Date(program.startTimestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) Color.White.copy(alpha = 0.05f) else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(if (isCurrent) 8.dp else 0.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
            Text(
                text = startTime,
                color = if (isCurrent) AppColors.ArenaOrange else Color.White.copy(alpha = 0.6f),
                fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                fontSize = 12.sp
            )
            if (isCurrent) {
                Text("NOW", color = AppColors.ArenaOrange, fontWeight = FontWeight.Black, fontSize = 8.sp)
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = program.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (isCurrent) {
                val progress = remember(program.startTimestamp, program.stopTimestamp) {
                    val now = System.currentTimeMillis()
                    val total = program.stopTimestamp - program.startTimestamp
                    if (total > 0) (now - program.startTimestamp).toFloat() / total else 0f
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(3.dp),
                    color = AppColors.ArenaOrange,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
            }
            
            if (!program.description.isNullOrBlank()) {
                Text(
                    text = program.description!!,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    maxLines = if (isCurrent) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LiveTopNavBar(
    currentTime: String,
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiveRoundIconButton(
                icon = Icons.Default.Home, 
                contentDescription = "Home", 
                onClick = onBack
            )

            ArenaLiveLogo(textSize = 20.sp)

            Spacer(modifier = Modifier.width(8.dp))

            NavTabButton(label = "Live TV", isActive = true, onClick = {})
            NavTabButton(label = "Movies", isActive = false, onClick = onNavigateToMovies)
            NavTabButton(label = "Series", isActive = false, onClick = onNavigateToSeries)

            LiveRoundIconButton(
                icon = Icons.Default.Search, 
                contentDescription = "Search", 
                onClick = onNavigateToSearch
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(text = currentTime, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LivePalette.hairline))
    }
}

@Composable
private fun LiveRoundIconButton(
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
private fun PaneSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = LivePalette.paneFill,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, LivePalette.paneBorder)
    ) {
        content()
    }
}

@Composable
private fun PaneHeader(title: String) {
    Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        Text(text = title.uppercase(), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LivePalette.hairline))
}
