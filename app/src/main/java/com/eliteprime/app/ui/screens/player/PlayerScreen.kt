package com.eliteprime.app.ui.screens.player

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliteprime.app.player.PlayerEngine
import com.eliteprime.app.player.PlaybackState
import com.eliteprime.app.player.PlayerTrack

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    playerEngine: PlayerEngine,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val showControls by viewModel.showControls.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val showDiagnostics by viewModel.showDiagnostics.collectAsStateWithLifecycle()
    val stats by viewModel.playerStats.collectAsStateWithLifecycle()
    
    val audioTracks by playerEngine.availableAudioTracks.collectAsStateWithLifecycle()
    val subtitleTracks by playerEngine.availableSubtitleTracks.collectAsStateWithLifecycle()

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler {
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Video Surface
        AndroidView(
            factory = { playerEngine.createRenderView(it) },
            modifier = Modifier.fillMaxSize()
        )

        // Modern Controls
        MobilePlayerControls(
            visible = showControls,
            title = viewModel.title,
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
            onToggleAspectRatio = { viewModel.toggleAspectRatio() },
            onEnterPiP = { },
            onToggleVisibility = { viewModel.toggleControls() },
            onToggleDiagnostics = { viewModel.toggleDiagnostics() },
            modifier = Modifier.fillMaxSize()
        )

        // Diagnostics Overlay
        if (showDiagnostics) {
            DiagnosticsOverlay(
                stats = stats,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 20.dp)
            )
        }

        // Loading Indicator
        if (playbackState == PlaybackState.BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }

    if (showAudioDialog) {
        PlayerTrackSelectionDialog(
            title = "Audio Tracks",
            tracks = audioTracks,
            onTrackSelected = { playerEngine.selectAudioTrack(it.id) },
            onDismissRequest = { showAudioDialog = false }
        )
    }

    if (showSubtitleDialog) {
        PlayerTrackSelectionDialog(
            title = "Subtitles",
            tracks = subtitleTracks,
            onTrackSelected = { playerEngine.selectSubtitleTrack(it.id) },
            onDismissRequest = { showSubtitleDialog = false }
        )
    }
}
