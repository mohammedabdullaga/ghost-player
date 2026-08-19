package com.eliteprime.app.ui.screens.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eliteprime.app.R
import com.eliteprime.app.player.PlayerTrack
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay

@Composable
fun PlayerTrackSelectionDialog(
    title: String,
    icon: ImageVector = Icons.Default.Subtitles,
    tracks: List<PlayerTrack>,
    onTrackSelected: (PlayerTrack) -> Unit,
    onDismissRequest: () -> Unit,
    showSyncController: Boolean = false,
    currentDelayMs: Long = 0,
    onAdjustDelay: (Long) -> Unit = {},
    onResetDelay: () -> Unit = {}
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val initialFocusRequester = remember { FocusRequester() }
    val syncFocusRequester = remember { FocusRequester() }
    
    val isAnyTrackSelected = remember(tracks) { tracks.any { it.isSelected && it.id != "none" } }

    LaunchedEffect(Unit) {
        if (isTv) {
            delay(200)
            try {
                if (showSyncController && isAnyTrackSelected) {
                    syncFocusRequester.requestFocus()
                } else {
                    initialFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1A1A1A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            tonalElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Icon & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(AppColors.ArenaOrange.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = AppColors.ArenaOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.generic_close),
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                if (showSyncController) {
                    SubtitleSyncController(
                        currentDelayMs = currentDelayMs,
                        isEnabled = isAnyTrackSelected,
                        onAdjustDelay = onAdjustDelay,
                        onResetDelay = onResetDelay,
                        focusRequester = syncFocusRequester
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }

                // Track Options List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(tracks) { track ->
                        val isFirst = tracks.firstOrNull() == track
                        TrackItem(
                            track = track,
                            modifier = if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
                            onClick = {
                                onTrackSelected(track)
                                onDismissRequest()
                            }
                        )
                    }
                }

                // Footer button
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = stringResource(R.string.generic_close).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleSyncController(
    currentDelayMs: Long,
    isEnabled: Boolean,
    onAdjustDelay: (Long) -> Unit,
    onResetDelay: () -> Unit,
    focusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1f else 0.4f),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label
        Text(
            text = "SUBTITLE SYNC",
            color = if (isEnabled) AppColors.ArenaOrange else Color.White.copy(alpha = 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )

        // Large Centered Counter Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.5.dp, if (currentDelayMs != 0L && isEnabled) AppColors.ArenaOrange else Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${if (currentDelayMs > 0) "+" else ""}${currentDelayMs}ms",
                    color = if (currentDelayMs == 0L || !isEnabled) Color.White else AppColors.ArenaOrange,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (isEnabled) {
                    Text(
                        text = when {
                            currentDelayMs > 0 -> "Subtitles are delayed"
                            currentDelayMs < 0 -> "Subtitles are advanced"
                            else -> "Subtitles in sync"
                        },
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Adjust Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SyncStepButton(
                label = "-250",
                enabled = isEnabled,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                onClick = { onAdjustDelay(-250) }
            )
            SyncStepButton(
                label = "-50",
                enabled = isEnabled,
                modifier = Modifier.weight(1f),
                onClick = { onAdjustDelay(-50) }
            )
            
            // Large Reset Button
            SyncStepButton(
                label = "RESET",
                enabled = isEnabled && currentDelayMs != 0L,
                modifier = Modifier.weight(1.8f),
                onClick = onResetDelay
            )
            
            SyncStepButton(
                label = "+50",
                enabled = isEnabled,
                modifier = Modifier.weight(1f),
                onClick = { onAdjustDelay(50) }
            )
            SyncStepButton(
                label = "+250",
                enabled = isEnabled,
                modifier = Modifier.weight(1f),
                onClick = { onAdjustDelay(250) }
            )
        }
    }
}

@Composable
private fun SyncStepButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> Color.White.copy(alpha = 0.2f)
            else -> Color.White.copy(alpha = 0.05f)
        },
        border = BorderStroke(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = if (isFocused) AppColors.ArenaOrange else Color.White.copy(alpha = 0.08f)
        ),
        modifier = modifier
            .height(36.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (isFocused) AppColors.ArenaOrange else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun TrackItem(
    track: PlayerTrack,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        color = when {
            isFocused -> Color.White.copy(alpha = 0.18f)
            track.isSelected -> AppColors.ArenaOrange.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.04f)
        },
        border = BorderStroke(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = when {
                isFocused -> AppColors.ArenaOrange
                track.isSelected -> AppColors.ArenaOrange.copy(alpha = 0.4f)
                else -> Color.White.copy(alpha = 0.06f)
            }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = track.label,
                color = if (track.isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.75f),
                fontWeight = if (track.isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
            if (track.isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(AppColors.ArenaOrange, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerAudioSyncDialog(
    currentDelayMs: Long,
    onAdjustDelay: (Long) -> Unit,
    onResetDelay: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) {
            delay(200)
            try {
                initialFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1A1A1A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            tonalElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(AppColors.ArenaOrange.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = AppColors.ArenaOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.player_audio_sync),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.generic_close),
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Delay Display Badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.player_audio_sync_ms, currentDelayMs),
                            color = if (currentDelayMs == 0L) Color.White else AppColors.ArenaOrange,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = when {
                                currentDelayMs > 0 -> "Audio is delayed"
                                currentDelayMs < 0 -> "Audio is advanced"
                                else -> "Audio and Video in sync"
                            },
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Adjust Controls (+ / - steps)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AudioDelayStepButton(
                        label = "-250ms",
                        modifier = Modifier.weight(1f).focusRequester(initialFocusRequester),
                        onClick = { onAdjustDelay(-250L) }
                    )
                    AudioDelayStepButton(
                        label = "-50ms",
                        modifier = Modifier.weight(1f),
                        onClick = { onAdjustDelay(-50L) }
                    )
                    AudioDelayStepButton(
                        label = "+50ms",
                        modifier = Modifier.weight(1f),
                        onClick = { onAdjustDelay(50L) }
                    )
                    AudioDelayStepButton(
                        label = "+250ms",
                        modifier = Modifier.weight(1f),
                        onClick = { onAdjustDelay(250L) }
                    )
                }

                // Actions: Reset & Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onResetDelay,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.player_audio_sync_reset).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.ArenaOrange,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.generic_done).uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioDelayStepButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = if (isFocused) AppColors.ArenaOrange else Color.White.copy(alpha = 0.08f)
        ),
        modifier = modifier
            .height(42.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isFocused) AppColors.ArenaOrange else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

