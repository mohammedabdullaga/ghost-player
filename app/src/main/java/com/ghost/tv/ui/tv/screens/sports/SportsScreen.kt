package com.ghost.tv.ui.tv.screens.sports

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.ghost.tv.R
import com.ghost.tv.data.local.entity.LiveStream
import com.ghost.tv.data.model.EventItem
import com.ghost.tv.data.model.ShelfItem
import com.ghost.tv.ui.tv.components.TodMatchCard
import kotlinx.coroutines.delay

@Composable
fun SportsScreen(
    onBack: () -> Unit,
    onPlayChannel: (LiveStream) -> Unit,
    viewModel: SportsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Focus Requesters & Last Focused Item Tracking for TV D-Pad Focus Restoration
    val firstItemRequester = remember { FocusRequester() }
    val backButtonRequester = remember { FocusRequester() }
    val itemFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler {
        onBack()
    }

    // Auto-restore focus to last played card or first card upon screen load / returning from playback
    LaunchedEffect(uiState.isLoading, uiState.feed) {
        if (!uiState.isLoading && !uiState.feed?.shelves.isNullOrEmpty()) {
            delay(150)
            val targetId = lastFocusedItemId
            val requester = if (targetId != null) {
                itemFocusRequesters[targetId] ?: firstItemRequester
            } else {
                firstItemRequester
            }
            try {
                requester.requestFocus()
            } catch (e: Exception) {
                Log.d("SportsScreen", "Failed to focus target $targetId, defaulting to first item", e)
                try { firstItemRequester.requestFocus() } catch (_: Exception) {}
            }
        } else if (!uiState.isLoading && uiState.feed?.shelves.isNullOrEmpty()) {
            delay(100)
            try { backButtonRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    fun handleEventClick(event: EventItem, itemKey: String) {
        lastFocusedItemId = itemKey
        val channel = viewModel.findChannelForEvent(event)
        if (channel != null) {
            onPlayChannel(channel)
        } else {
            Toast.makeText(context, R.string.channel_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    // Pure Black Canvas (Color(0xFF000000))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    TvSurface(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(backButtonRequester)
                            .onFocusChanged { isBackFocused = it.isFocused },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isBackFocused) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.nav_sports),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TvSurface(
                    onClick = { viewModel.loadSportsFeed() },
                    modifier = Modifier.size(40.dp),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White
                    )
                ) {
                    var isRefreshFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { isRefreshFocused = it.isFocused },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isRefreshFocused) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Main Content Area
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFFC107))
                }
            } else if (!uiState.error.isNullOrBlank() && uiState.feed?.shelves.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Error loading sports feed",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            } else {
                val shelves = uiState.feed?.shelves.orEmpty().sortedBy { it.sortOrder ?: Int.MAX_VALUE }

                if (shelves.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.channel_not_found),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        itemsIndexed(shelves) { shelfIndex, shelf ->
                            SportsShelfRow(
                                shelf = shelf,
                                shelfIndex = shelfIndex,
                                firstItemRequester = firstItemRequester,
                                itemFocusRequesters = itemFocusRequesters,
                                onEventFocused = { key -> lastFocusedItemId = key },
                                onEventClick = { event, key -> handleEventClick(event, key) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsShelfRow(
    shelf: ShelfItem,
    shelfIndex: Int,
    firstItemRequester: FocusRequester,
    itemFocusRequesters: MutableMap<String, FocusRequester>,
    onEventFocused: (String) -> Unit,
    onEventClick: (EventItem, String) -> Unit
) {
    val items = shelf.items.orEmpty()
    if (items.isEmpty()) return

    val title = shelf.displayTitle?.ifBlank { null } ?: shelf.title ?: ""

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp)
        ) {
            itemsIndexed(items) { itemIndex, event ->
                val itemKey = event.id?.ifBlank { null } ?: "${shelf.id}_${itemIndex}"
                val isFirstOverall = (shelfIndex == 0 && itemIndex == 0)
                val requester = if (isFirstOverall) {
                    firstItemRequester
                } else {
                    itemFocusRequesters.getOrPut(itemKey) { FocusRequester() }
                }

                TodMatchCard(
                    event = event,
                    modifier = Modifier.focusRequester(requester),
                    onFocusChanged = { focused ->
                        if (focused) {
                            onEventFocused(itemKey)
                        }
                    },
                    onClick = { onEventClick(event, itemKey) }
                )
            }
        }
    }
}
