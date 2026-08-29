package com.eliteprime.app.ui.tv.screens

import android.app.Activity
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.Category
import com.eliteprime.app.data.local.entity.EPGProgram
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.domain.model.VideoFormat
import com.eliteprime.app.player.PlayerStats
import com.eliteprime.app.player.PlayerViewMode
import com.eliteprime.app.ui.components.AppBackground
import com.eliteprime.app.ui.components.TvExitDialog
import com.eliteprime.app.ui.screens.live.LiveViewModel
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.ui.tv.core.*
import com.eliteprime.app.util.DeviceRenderProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val CATEGORIES_PANE_WEIGHT = 0.27f
private const val CHANNELS_PANE_WEIGHT = 0.28f
private const val PREVIEW_PANE_WEIGHT = 0.48f

// Shared spacing/radius scale for this screen — keeps every row, gap,
// and corner consistent instead of scattered magic numbers.
private object LiveSpacing {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
}

private object LiveRadius {
    val row = 16.dp
    val pane = 0.dp
}

@Composable
fun TvLiveTvScreen(
    onBack: () -> Unit,
    onPlayChannel: (LiveStream) -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val previewChannel by viewModel.previewChannel.collectAsState()
    val epgList by viewModel.currentEpg.collectAsState()
    val videoFormat by viewModel.playerEngine.videoFormat.collectAsState()
    val playerStats by viewModel.playerEngine.playerStats.collectAsState()

    var showAddGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var selectedChannelForOptions by remember { mutableStateOf<LiveStream?>(null) }
    var selectedCategoryForOptions by remember { mutableStateOf<Category?>(null) }

    var lastFocusedCategoryId by remember { mutableStateOf<Long?>(null) }
    var lastFocusedChannelId by remember { mutableStateOf<Long?>(null) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(searchText) { delay(300); viewModel.setSearchQuery(searchText) }

    val scope = rememberCoroutineScope()
    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()

    val categoryFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val channelFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val firstCategoryRequester = remember { FocusRequester() }
    val firstChannelRequester = remember { FocusRequester() }
    val addGroupFocusRequester = remember { FocusRequester() }
    val fsButtonRequester = remember { FocusRequester() }
    val favButtonRequester = remember { FocusRequester() }

    val isAnyPopupOpen = showAddGroupDialog || selectedChannelForOptions != null || selectedCategoryForOptions != null
    val isLiteMode = DeviceRenderProfile.isLiteMode()
    var showExitDialog by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    if (showExitDialog) {
        TvExitDialog(
            onConfirm = { activity?.finish() },
            onDismiss = { showExitDialog = false }
        )
    }

    // COMMUNICATE WITH SHELL TO LOCK NAVBAR
    val focusManager = LocalTvFocusManager.current
    LaunchedEffect(isAnyPopupOpen) {
        focusManager.value = isAnyPopupOpen
    }

    // AUTO-PREVIEW FIRST CHANNEL IF NONE IS PREVIEWED
    LaunchedEffect(uiState.filteredChannels) {
        if (previewChannel == null && uiState.filteredChannels.isNotEmpty()) {
            val first = uiState.filteredChannels.first()
            viewModel.previewChannel(first)
        }
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        TvGlassPane(
            modifier = Modifier.fillMaxSize(),
            backgroundColor = TvColors.GlassBase,
            shape = RoundedCornerShape(LiveRadius.pane)
        ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // --- 1. CATEGORIES (tonal rail) ---
                    Column(
                        modifier = Modifier
                            .weight(CATEGORIES_PANE_WEIGHT)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
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
                                var isDoneFocused by remember { mutableStateOf(false) }
                                TvSurfaceCard(
                                    onClick = { viewModel.saveCategoryOrder() },
                                    modifier = Modifier.align(Alignment.CenterEnd).height(32.dp).onFocusChanged { isDoneFocused = it.isFocused },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = AppColors.ArenaOrange.copy(alpha = 0.25f), focusedContainerColor = Color.White),
                                    border = ClickableSurfaceDefaults.border(
                                        focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                                        border = Border(BorderStroke(2.dp, AppColors.ArenaOrange.copy(alpha = 0.85f)))
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.generic_done), color = if (isDoneFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp))
                                    }
                                }
                            } else {
                                var isAddFocused by remember { mutableStateOf(false) }
                                TvSurfaceCard(
                                    onClick = { showAddGroupDialog = true },
                                    modifier = Modifier.align(Alignment.CenterEnd).size(32.dp).focusRequester(addGroupFocusRequester).onFocusChanged { isAddFocused = it.isFocused },
                                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), focusedContainerColor = Color.White)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (isAddFocused) Color.Black else AppColors.ArenaOrange,
                                        modifier = Modifier.size(18.dp).align(Alignment.Center)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = TvColors.GlassBorder)

                        LazyColumn(
                            state = categoryListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
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
                                    LaunchedEffect(category.id) { viewModel.loadCategoryCount(category) }
                                    TvCategoryItem(
                                        category = category,
                                        count = uiState.categoryCounts[category.id],
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

                    Spacer(modifier = Modifier.width(20.dp))

                    // --- 2. CHANNELS ---
                    Column(modifier = Modifier.weight(CHANNELS_PANE_WEIGHT).fillMaxHeight()) {
                        var isSearchFocused by remember { mutableStateOf(false) }
                        var isSearchEditing by remember { mutableStateOf(false) }
                        val keyboardController = LocalSoftwareKeyboardController.current

                        LaunchedEffect(isSearchEditing) {
                            if (isSearchEditing) { delay(100); keyboardController?.show() }
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isChannelReorderMode) {
                                Text(
                                    text = stringResource(R.string.channel_reorder).uppercase(),
                                    color = TvColors.TextSecondary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.5.sp
                                )
                            } else {
                                OutlinedTextField(
                                    value = searchText,
                                    onValueChange = { searchText = it },
                                    placeholder = { Text(stringResource(R.string.nav_search) + "...", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp) },
                                    textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(44.dp)
                                        .onFocusChanged {
                                            isSearchFocused = it.isFocused
                                            if (!it.isFocused) isSearchEditing = false
                                        }
                                        .onKeyEvent { keyEvent ->
                                            if (isSearchFocused && !isSearchEditing) {
                                                if (keyEvent.type == KeyEventType.KeyUp &&
                                                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                                                    isSearchEditing = true
                                                    return@onKeyEvent true
                                                }
                                            }
                                            false
                                        },
                                    readOnly = !isSearchEditing,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        hintLocales = LocaleList(Locale("ar"), Locale("en"))
                                    ),
                                    trailingIcon = {
                                        Icon(Icons.Default.Search, null, tint = AppColors.ArenaOrange, modifier = Modifier.size(16.dp))
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AppColors.ArenaOrange,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                        cursorColor = AppColors.ArenaOrange,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            if (uiState.isChannelReorderMode) {
                                var isDoneFocused by remember { mutableStateOf(false) }
                                TvSurfaceCard(
                                    onClick = { viewModel.saveChannelOrder() },
                                    modifier = Modifier.align(Alignment.CenterEnd).height(32.dp).onFocusChanged { isDoneFocused = it.isFocused },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = AppColors.ArenaOrange.copy(alpha = 0.25f), focusedContainerColor = Color.White),
                                    border = ClickableSurfaceDefaults.border(
                                        focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                                        border = Border(BorderStroke(2.dp, AppColors.ArenaOrange.copy(alpha = 0.85f)))
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.generic_done), color = if (isDoneFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = TvColors.GlassBorder)

                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
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
                                            } }
                                            .focusProperties {
                                                right = fsButtonRequester
                                            },
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

                    Spacer(modifier = Modifier.width(2.dp))

                    // --- 3. PREVIEW & EPG (hero style) ---
                    Column(modifier = Modifier.weight(PREVIEW_PANE_WEIGHT).fillMaxHeight()) {
                        // Video fills the width; title/badge live ON the
                        // image via a bottom gradient scrim, instead of a
                        // separate text block below a bordered box.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(bottomStart = LiveRadius.row, bottomEnd = LiveRadius.row))
                                .border(1.dp, TvColors.GlassBorder, RoundedCornerShape(bottomStart = LiveRadius.row, bottomEnd = LiveRadius.row))
                                .onGloballyPositioned { coords ->
                                    viewModel.globalPlayerState.updatePreviewBounds(coords.boundsInRoot())
                                }
                        ) {
                            if (previewChannel != null) {
                                LaunchedEffect(previewChannel) {
                                    viewModel.globalPlayerState.enterPreview(viewModel.playerEngine)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomStart)
                                        .background(
                                            remember {
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                                )
                                            }
                                        )
                                        .padding(horizontal = LiveSpacing.md, vertical = LiveSpacing.sm)
                                ) {
                                    Column {
                                        Surface(
                                            color = TvColors.Primary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.live_now).uppercase(),
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = previewChannel!!.name,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Icon(Icons.Default.PlayCircle, null, modifier = Modifier.align(Alignment.Center).size(48.dp), tint = TvColors.TextTertiary)
                            }
                        }

                        if (previewChannel != null) {
                            TvStreamInfoCard(videoFormat, playerStats)
                            
                            TvPreviewActionButtons(
                                isFavorite = previewChannel?.isFavorite == true,
                                fsFocusRequester = fsButtonRequester,
                                favFocusRequester = favButtonRequester,
                                onFullScreen = {
                                    previewChannel?.let {
                                        viewModel.confirmChannel(it)
                                        viewModel.globalPlayerState.enterFullScreen(viewModel.playerEngine)
                                        onPlayChannel(it)
                                    }
                                },
                                onToggleFavorite = {
                                    previewChannel?.let { viewModel.toggleFavorite(it) }
                                }
                            )
                        }

                        if (previewChannel != null && epgList.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = LiveSpacing.lg),
                                contentPadding = PaddingValues(vertical = LiveSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(LiveSpacing.md)
                            ) {
                                itemsIndexed(epgList) { _, program ->
                                    TvEpgItem(program)
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
                    var isCreateFocused by remember { mutableStateOf(false) }
                    TvSurfaceCard(
                        onClick = {
                            viewModel.addCustomGroup(newGroupName)
                            showAddGroupDialog = false
                            newGroupName = ""
                        },
                        modifier = Modifier.weight(1f).height(52.dp).onFocusChanged { isCreateFocused = it.isFocused },
                        colors = ClickableSurfaceDefaults.colors(containerColor = AppColors.ArenaOrange.copy(alpha = 0.25f), focusedContainerColor = Color.White),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                            border = Border(BorderStroke(2.dp, AppColors.ArenaOrange.copy(alpha = 0.85f)))
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.generic_create), color = if (isCreateFocused) Color.Black else Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                    var isCancelFocused by remember { mutableStateOf(false) }
                    TvSurfaceCard(
                        onClick = { showAddGroupDialog = false },
                        modifier = Modifier.weight(1f).height(52.dp).onFocusChanged { isCancelFocused = it.isFocused },
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), focusedContainerColor = Color.White)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.generic_cancel), color = if (isCancelFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold)
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
    count: Int?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isLite = DeviceRenderProfile.isLiteMode()

    TvSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (isLite) 1.0f else 1.02f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, 
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.White.copy(alpha = 0.1f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None, 
            focusedBorder = Border(BorderStroke(1.5.dp, AppColors.ArenaOrange))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .width(3.dp)
                    .background(if (isSelected) AppColors.ArenaOrange else Color.Transparent, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = category.categoryName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 2.dp)
                )
            }
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
    val isLite = DeviceRenderProfile.isLiteMode()

    TvSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused },
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (isLite) 1.0f else 1.02f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.White.copy(alpha = 0.1f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(1.5.dp, AppColors.ArenaOrange))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .width(3.dp)
                    .background(if (isSelected) AppColors.ArenaOrange else Color.Transparent, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.streamIcon)
                        .size(Size(100, 100))
                        .precision(Precision.INEXACT)
                        .build(),
                    contentDescription = null, 
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
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
            .heightIn(min = 44.dp),
        backgroundColor = Color.White.copy(alpha = 0.05f),
        borderColor = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(LiveRadius.row)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LiveSpacing.md, vertical = LiveSpacing.xs),
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
            SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date(program.startTimestamp))
        }
        Text(text = time, color = TvColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(text = program.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvStreamInfoCard(format: VideoFormat, stats: PlayerStats) {
    Row(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TechnicalTag(
            label = "RES", 
            value = if (format.width > 0) "${format.width}x${format.height}" else "--",
            icon = Icons.Default.AspectRatio
        )
        
        Spacer(Modifier.width(10.dp))
        
        val displayFps = if (format.frameRate > 0) format.frameRate.toInt() else 0
        TechnicalTag(
            label = "FPS", 
            value = if (displayFps > 0) "$displayFps" else "--",
            icon = Icons.Default.Speed
        )

        Spacer(Modifier.width(10.dp))

        // Prefer dynamic bitrate from stats if available and high enough to be measured data
        val displayBitrate = if (stats.videoBitrate > 100_000) stats.videoBitrate else format.bitrate
        TechnicalTag(
            label = "BITRATE", 
            value = if (displayBitrate > 0) "${displayBitrate / 1000} Kb" else "LIVE",
            icon = Icons.Default.CloudDownload
        )
    }
}

@Composable
private fun TvPreviewActionButtons(
    isFavorite: Boolean,
    fsFocusRequester: FocusRequester,
    favFocusRequester: FocusRequester,
    onFullScreen: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = LiveSpacing.lg, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var isFsFocused by remember { mutableStateOf(false) }
        TvSurfaceCard(
            onClick = onFullScreen,
            modifier = Modifier
                .focusRequester(fsFocusRequester)
                .onFocusChanged { isFsFocused = it.isFocused }
                .focusProperties {
                    right = favFocusRequester
                },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Fullscreen, null, tint = if (isFsFocused) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.player_fullscreen),
                    color = if (isFsFocused) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        var isFavFocused by remember { mutableStateOf(false) }
        TvSurfaceCard(
            onClick = onToggleFavorite,
            modifier = Modifier
                .focusRequester(favFocusRequester)
                .onFocusChanged { isFavFocused = it.isFocused }
                .focusProperties {
                    left = fsFocusRequester
                },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    null,
                    tint = if (isFavFocused) Color.Black else if (isFavorite) AppColors.ArenaOrange else Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isFavorite) stringResource(R.string.fav_remove) else stringResource(R.string.fav_add),
                    color = if (isFavFocused) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TechnicalTag(label: String, value: String, icon: ImageVector) {
    Column(
        modifier = Modifier
            .widthIn(min = 85.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(TvColors.Primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvColors.Primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black
        )
    }
}