package com.eliteprime.app.ui.screens.player

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteprime.app.R
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliteprime.app.MainActivity
import com.eliteprime.app.player.PlayerEngine
import com.eliteprime.app.player.PlayerTrack
import com.eliteprime.app.player.PlaybackState
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    playerEngine: PlayerEngine,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val title by viewModel.title.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val showControls by viewModel.showControls.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val showDiagnostics by viewModel.showDiagnostics.collectAsStateWithLifecycle()
    val stats by viewModel.playerStats.collectAsStateWithLifecycle()
    val audioDelayMs by viewModel.audioDelayMs.collectAsStateWithLifecycle()
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsStateWithLifecycle()
    val resumePrompt by viewModel.resumePrompt.collectAsStateWithLifecycle()
    val hasNextEpisode by viewModel.hasNextEpisode.collectAsStateWithLifecycle()
    val hasPreviousEpisode by viewModel.hasPreviousEpisode.collectAsStateWithLifecycle()
    val seekAccumulator by viewModel.seekAccumulator.collectAsStateWithLifecycle()
    val preventStandby by viewModel.preventStandby.collectAsStateWithLifecycle()

    var showAudioSyncDialog by remember { mutableStateOf(false) }

    val isInPip by (activity as? MainActivity)?.pictureInPictureModeFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val videoFormat by viewModel.videoFormat.collectAsStateWithLifecycle()

    val isTv = LocalContext.current.isTelevisionDevice()
    val trapFocusRequester = remember { FocusRequester() }

    LaunchedEffect(videoFormat, isInPip) {
        if (!isInPip) {
            (activity as? MainActivity)?.updatePipState(
                enabled = true,
                width = videoFormat.width,
                height = videoFormat.height
            )
        }
    }

    val audioTracks by playerEngine.availableAudioTracks.collectAsStateWithLifecycle()
    val subtitleTracks by playerEngine.availableSubtitleTracks.collectAsStateWithLifecycle()

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    // Auto-focus logic for TV
    LaunchedEffect(showControls, resumePrompt.show) {
        if (isTv && !showControls && !resumePrompt.show) {
            delay(100) // Ensure UI has settled
            try {
                trapFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    DisposableEffect(preventStandby) {
        if (preventStandby) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        viewModel.claimFullScreen()
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            (activity as? MainActivity)?.updatePipState(false, 0, 0)
            viewModel.onPlayerScreenDisposed()
        }
    }

    BackHandler {
        if (showControls) {
            viewModel.toggleControls()
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) 
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    viewModel.onUserAction()
                }
                false
            }
    ) {
        // 1. Background Key Trap (TV only, when UI is hidden)
        // This ensures D-pad Left/Right/OK works for seeking/showing menu
        if (isTv && !showControls && !resumePrompt.show) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(trapFocusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    viewModel.toggleControls()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    viewModel.accumulateSeek(-10)
                                    true
                                }
                                Key.DirectionRight -> {
                                    viewModel.accumulateSeek(10)
                                    true
                                }
                                Key.DirectionUp, Key.DirectionDown -> {
                                    viewModel.toggleControls()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )
        }

        if (!isInPip) {
            // 2. Control Layer
            MobilePlayerControls(
                isControlsVisible = showControls,
                title = title,
                isPlaying = isPlaying,
                isMuted = isMuted,
                aspectRatioLabel = aspectRatio,
                currentPosition = currentPosition,
                duration = duration,
                subtitleTrackCount = subtitleTracks.size,
                audioTrackCount = audioTracks.size,
                onClose = onBack,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onToggleMute = { viewModel.toggleMute() },
                onSeekBackward = { viewModel.seekBackward() },
                onSeekForward = { viewModel.seekForward() },
                onSeekToPosition = { viewModel.seekTo(it) },
                onOpenSubtitleTracks = { showSubtitleDialog = true },
                onOpenAudioTracks = { showAudioDialog = true },
                onOpenAudioSync = { showAudioSyncDialog = true },
                onToggleAspectRatio = { viewModel.toggleAspectRatio() },
                onEnterPiP = { viewModel.enterPip(activity) },
                onToggleVisibility = { viewModel.toggleControls() },
                onToggleDiagnostics = { viewModel.toggleDiagnostics() },
                onInteraction = { viewModel.onUserAction() },
                contentType = when (viewModel.type) {
                    "live" -> PlayerContentType.LIVE_TV
                    "series" -> PlayerContentType.SERIES
                    else -> PlayerContentType.MOVIE
                },
                hasNextEpisode = hasNextEpisode,
                hasPreviousEpisode = hasPreviousEpisode,
                onNextEpisode = { viewModel.playNextEpisode() },
                onPreviousEpisode = { viewModel.playPreviousEpisode() },
                modifier = Modifier.fillMaxSize()
            )

            // 3. Seek Indicator Overlay
            AnimatedVisibility(
                visible = seekAccumulator != 0,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (seekAccumulator > 0) Icons.Default.Forward10 else Icons.Default.Replay10,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (seekAccumulator > 0) "+${formatSeek(seekAccumulator)}" else formatSeek(seekAccumulator),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // 4. Loading / Diagnostics / Resume
            if (showDiagnostics) {
                DiagnosticsOverlay(
                    stats = stats,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 20.dp)
                )
            }

            if (playbackState == PlaybackState.BUFFERING) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }

            if (resumePrompt.show) {
                MobileResumePrompt(
                    title = resumePrompt.title,
                    positionMs = resumePrompt.positionMs,
                    onStartOver = { viewModel.dismissResumePrompt(false) },
                    onResume = { viewModel.dismissResumePrompt(true) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (showAudioDialog) {
                PlayerTrackSelectionDialog(
                    title = stringResource(R.string.player_audio).uppercase(),
                    icon = Icons.Default.Audiotrack,
                    tracks = audioTracks,
                    onTrackSelected = { viewModel.selectAudioTrack(it.id) },
                    onDismissRequest = { showAudioDialog = false }
                )
            }

            if (showAudioSyncDialog) {
                PlayerAudioSyncDialog(
                    currentDelayMs = audioDelayMs,
                    onAdjustDelay = { viewModel.adjustAudioDelay(it) },
                    onResetDelay = { viewModel.resetAudioDelay() },
                    onDismissRequest = { showAudioSyncDialog = false }
                )
            }

            if (showSubtitleDialog) {
                val subOptions = listOf(PlayerTrack(id = "none", label = stringResource(R.string.generic_done).uppercase(), isSelected = !subtitleTracks.any { it.isSelected })) + subtitleTracks
                PlayerTrackSelectionDialog(
                    title = stringResource(R.string.player_subs).uppercase(),
                    icon = Icons.Default.Subtitles,
                    tracks = subOptions,
                    onTrackSelected = { 
                        if (it.id == "none") viewModel.selectSubtitleTrack(null)
                        else viewModel.selectSubtitleTrack(it.id)
                    },
                    onDismissRequest = { showSubtitleDialog = false },
                    showSyncController = true,
                    currentDelayMs = subtitleDelayMs,
                    onAdjustDelay = { viewModel.adjustSubtitleDelay(it) },
                    onResetDelay = { viewModel.resetSubtitleDelay() }
                )
            }
        }
    }
}

private fun formatSeek(seconds: Int): String {
    val absSec = Math.abs(seconds)
    if (absSec < 60) return "${seconds}s"
    val mins = absSec / 60
    val secs = absSec % 60
    val sign = if (seconds < 0) "-" else ""
    return if (secs == 0) "$sign${mins}m" else "$sign${mins}m ${secs}s"
}
