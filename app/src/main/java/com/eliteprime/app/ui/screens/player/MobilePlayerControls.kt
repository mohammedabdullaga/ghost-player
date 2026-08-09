package com.eliteprime.app.ui.screens.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteprime.app.R
import com.eliteprime.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.util.Locale

enum class PlayerContentType { MOVIE, SERIES, LIVE_TV }

@Composable
fun MobilePlayerControls(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    aspectRatioLabel: String,
    currentPosition: Long,
    duration: Long,
    subtitleTrackCount: Int,
    audioTrackCount: Int,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToPosition: (Long) -> Unit,
    onOpenSubtitleTracks: () -> Unit,
    onOpenAudioTracks: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onEnterPiP: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    contentType: PlayerContentType = PlayerContentType.MOVIE,
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {}
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val context = LocalContext.current
        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        val activity = context as? Activity
        val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

        var volumeFraction by remember {
            mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
        }
        var brightnessFraction by remember {
            mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f)
        }
        var showVolumeIndicator by remember { mutableStateOf(false) }
        var showBrightnessIndicator by remember { mutableStateOf(false) }

        LaunchedEffect(showVolumeIndicator) {
            if (showVolumeIndicator) {
                delay(1000)
                showVolumeIndicator = false
            }
        }
        LaunchedEffect(showBrightnessIndicator) {
            if (showBrightnessIndicator) {
                delay(1000)
                showBrightnessIndicator = false
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(onSeekBackward, contentType) {
                            detectTapGestures(
                                onTap = { onToggleVisibility() },
                                onDoubleTap = { if (contentType != PlayerContentType.LIVE_TV) onSeekBackward() }
                            )
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / size.height.toFloat()
                                volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeFraction * maxVolume).toInt(), 0)
                                showVolumeIndicator = true
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(onSeekForward, contentType) {
                            detectTapGestures(
                                onTap = { onToggleVisibility() },
                                onDoubleTap = { if (contentType != PlayerContentType.LIVE_TV) onSeekForward() }
                            )
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / size.height.toFloat()
                                brightnessFraction = (brightnessFraction + delta).coerceIn(0.02f, 1f)
                                activity?.window?.let { window ->
                                    val params = window.attributes
                                    params.screenBrightness = brightnessFraction
                                    window.attributes = params
                                }
                                showBrightnessIndicator = true
                            }
                        }
                )
            }

            GestureIndicator(
                visible = showVolumeIndicator,
                icon = if (volumeFraction <= 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                value = volumeFraction,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
            )
            GestureIndicator(
                visible = showBrightnessIndicator,
                icon = Icons.Default.BrightnessMedium,
                value = brightnessFraction,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
            )

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                PlayerTopIconButton(
                                    icon = Icons.Default.BugReport,
                                    contentDescription = "Diagnostics",
                                    onClick = onToggleDiagnostics
                                )
                                if (audioTrackCount > 0) {
                                    PlayerTopIconButton(
                                        icon = Icons.Default.Audiotrack,
                                        contentDescription = stringResource(R.string.player_audio),
                                        onClick = onOpenAudioTracks
                                    )
                                }
                                if (subtitleTrackCount > 0) {
                                    PlayerTopIconButton(
                                        icon = Icons.Default.Subtitles,
                                        contentDescription = stringResource(R.string.player_subs),
                                        onClick = onOpenSubtitleTracks
                                    )
                                }
                                PlayerAspectRatioChip(label = aspectRatioLabel, onClick = onToggleAspectRatio)
                                PlayerTopIconButton(
                                    icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute",
                                    onClick = onToggleMute
                                )
                                PlayerTopIconButton(
                                    icon = Icons.Default.PictureInPicture,
                                    contentDescription = "PiP",
                                    onClick = onEnterPiP
                                )
                            }
                        }
                    }

                    if (contentType != PlayerContentType.LIVE_TV) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            if (contentType == PlayerContentType.SERIES) {
                                IconButton(
                                    onClick = onPreviousEpisode,
                                    enabled = hasPreviousEpisode,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (hasPreviousEpisode) Color.White.copy(alpha = 0.15f)
                                            else Color.White.copy(alpha = 0.05f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.SkipPrevious,
                                        contentDescription = "Previous episode",
                                        tint = if (hasPreviousEpisode) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = onTogglePlayPause,
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                color = AppColors.ArenaOrange,
                                tonalElevation = 10.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            if (contentType == PlayerContentType.SERIES) {
                                IconButton(
                                    onClick = onNextEpisode,
                                    enabled = hasNextEpisode,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (hasNextEpisode) Color.White.copy(alpha = 0.15f)
                                            else Color.White.copy(alpha = 0.05f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.SkipNext,
                                        contentDescription = "Next episode",
                                        tint = if (hasNextEpisode) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp, top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (contentType) {
                            PlayerContentType.LIVE_TV -> {
                                LiveIndicatorBar()
                            }

                            PlayerContentType.MOVIE, PlayerContentType.SERIES -> {
                                if (duration > 0) {
                                    var sliderValue by remember(currentPosition, duration) {
                                        mutableFloatStateOf(currentPosition.toFloat() / duration.toFloat())
                                    }
                                    var isDragging by remember { mutableStateOf(false) }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = formatDuration(if (isDragging) (sliderValue * duration).toLong() else currentPosition),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.width(52.dp),
                                            textAlign = TextAlign.Start
                                        )

                                        Slider(
                                            value = if (isDragging) sliderValue else (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                                            onValueChange = {
                                                isDragging = true
                                                sliderValue = it
                                            },
                                            onValueChangeFinished = {
                                                isDragging = false
                                                onSeekToPosition((sliderValue * duration).toLong())
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color.White,
                                                activeTrackColor = AppColors.ArenaOrange,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                                                activeTickColor = Color.Transparent,
                                                inactiveTickColor = Color.Transparent
                                            )
                                        )

                                        Text(
                                            text = formatDuration(duration),
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(52.dp),
                                            textAlign = TextAlign.End
                                        )
                                    }
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
private fun LiveIndicatorBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "LiveIndicator")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulsingDot"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(color = AppColors.ArenaOrange, shape = RoundedCornerShape(4.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = alpha))
                )
                Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppColors.ArenaOrange)
        )
    }
}

@Composable
private fun PlayerAspectRatioChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GestureIndicator(visible: Boolean, icon: ImageVector, value: Float, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(value.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(AppColors.ArenaOrange)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(value * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MobileResumePrompt(
    title: String,
    positionMs: Long,
    onStartOver: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF1A1A1A),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                tonalElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(AppColors.ArenaOrange.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = AppColors.ArenaOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.player_resume_title),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = title,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.ArenaOrange.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AppColors.ArenaOrange.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.player_resume_at_format, formatDuration(positionMs)),
                                color = AppColors.ArenaOrange,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStartOver,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.6f))
                                Text(
                                    text = stringResource(R.string.player_resume_start_over).uppercase(),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1.2f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ArenaOrange),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    text = stringResource(R.string.player_resume_btn).uppercase(),
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    fontSize = 12.sp
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
private fun PlayerTopIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", remainingMinutes, seconds)
    }
}
