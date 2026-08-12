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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.eliteprime.app.R
import com.eliteprime.app.ui.theme.AppColors
import com.eliteprime.app.util.isTelevisionDevice
import kotlinx.coroutines.delay
import java.util.Locale

enum class PlayerContentType { MOVIE, SERIES, LIVE_TV }

@Composable
fun MobilePlayerControls(
    isControlsVisible: Boolean,
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
    onInteraction: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentType: PlayerContentType = PlayerContentType.MOVIE,
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {}
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isControlsVisible) {
        if (isTv && isControlsVisible) {
            delay(300)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

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
                                onInteraction()
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
                                onInteraction()
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
                visible = isControlsVisible,
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
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.generic_close), tint = Color.White, modifier = Modifier.size(20.dp))
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
                                    contentDescription = stringResource(R.string.player_diagnostics),
                                    onClick = onToggleDiagnostics,
                                    onInteraction = onInteraction
                                )
                                if (audioTrackCount > 0) {
                                    PlayerTopIconButton(
                                        icon = Icons.Default.Audiotrack,
                                        contentDescription = stringResource(R.string.player_audio),
                                        onClick = onOpenAudioTracks,
                                        onInteraction = onInteraction
                                    )
                                }
                                if (subtitleTrackCount > 0) {
                                    PlayerTopIconButton(
                                        icon = Icons.Default.Subtitles,
                                        contentDescription = stringResource(R.string.player_subs),
                                        onClick = onOpenSubtitleTracks,
                                        onInteraction = onInteraction
                                    )
                                }
                                PlayerAspectRatioChip(
                                    label = aspectRatioLabel, 
                                    onClick = onToggleAspectRatio,
                                    onInteraction = onInteraction
                                )
                                PlayerTopIconButton(
                                    icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = stringResource(R.string.player_mute),
                                    onClick = onToggleMute,
                                    onInteraction = onInteraction
                                )
                                PlayerTopIconButton(
                                    icon = Icons.Default.PictureInPicture,
                                    contentDescription = stringResource(R.string.player_pip),
                                    onClick = onEnterPiP,
                                    onInteraction = onInteraction
                                )
                            }
                        }
                    }

                    // Unified Bottom Control Container
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Previous Episode / Channel
                            if (contentType != PlayerContentType.MOVIE) {
                                PlayerControlButton(
                                    icon = Icons.Default.SkipPrevious,
                                    contentDescription = stringResource(R.string.generic_previous),
                                    onClick = onPreviousEpisode,
                                    onInteraction = onInteraction,
                                    enabled = hasPreviousEpisode
                                )
                            }

                            // 2. Seek Backward (VOD/Series only)
                            if (contentType != PlayerContentType.LIVE_TV) {
                                PlayerControlButton(
                                    icon = Icons.Default.Replay10,
                                    contentDescription = stringResource(R.string.player_rewind_10),
                                    onInteraction = onInteraction,
                                    onClick = onSeekBackward
                                )
                            }

                            // 3. Play/Pause (Center Anchor)
                            PlayerControlButton(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.player_play_pause),
                                onClick = onTogglePlayPause,
                                onInteraction = onInteraction,
                                containerColor = AppColors.ArenaOrange,
                                size = 44.dp,
                                modifier = Modifier.focusRequester(playPauseFocusRequester)
                            )

                            // 4. Seek Forward (VOD/Series only)
                            if (contentType != PlayerContentType.LIVE_TV) {
                                PlayerControlButton(
                                    icon = Icons.Default.Forward10,
                                    contentDescription = stringResource(R.string.player_forward_10),
                                    onInteraction = onInteraction,
                                    onClick = onSeekForward
                                )
                            }

                            // 5. Next Episode / Channel
                            if (contentType != PlayerContentType.MOVIE) {
                                PlayerControlButton(
                                    icon = Icons.Default.SkipNext,
                                    contentDescription = stringResource(R.string.generic_next),
                                    onClick = onNextEpisode,
                                    onInteraction = onInteraction,
                                    enabled = hasNextEpisode
                                )
                            }

                            // 6. Progress Area / Live Indicator
                            if (contentType != PlayerContentType.LIVE_TV && duration > 0) {
                                var sliderValue by remember(currentPosition, duration) {
                                    mutableFloatStateOf(currentPosition.toFloat() / duration.toFloat())
                                }
                                var isDragging by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = formatDuration(if (isDragging) (sliderValue * duration).toLong() else currentPosition),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(42.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    Slider(
                                        value = if (isDragging) sliderValue else (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                                        onValueChange = {
                                            isDragging = true
                                            sliderValue = it
                                            onInteraction()
                                        },
                                        onValueChangeFinished = {
                                            isDragging = false
                                            onSeekToPosition((sliderValue * duration).toLong())
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = AppColors.ArenaOrange,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )

                                    Text(
                                        text = formatDuration(duration),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(42.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else if (contentType == PlayerContentType.LIVE_TV) {
                                Spacer(modifier = Modifier.width(16.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    LiveIndicatorBar()
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
private fun PlayerControlButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    containerColor: Color = Color.White.copy(alpha = 0.08f),
    modifier: Modifier = Modifier
) {
    val isTv = LocalContext.current.isTelevisionDevice()
    if (isTv) {
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .size(size)
                .onFocusChanged { 
                    isFocused = it.isFocused 
                    if (it.isFocused) onInteraction()
                },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = containerColor,
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (isFocused) Color.Black else Color.White.copy(alpha = if (enabled) 0.9f else 0.2f),
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .size(size)
                .background(containerColor, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
                modifier = Modifier.size(size * 0.55f)
            )
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
                Text(stringResource(R.string.generic_live), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
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
private fun PlayerAspectRatioChip(label: String, onClick: () -> Unit, onInteraction: () -> Unit) {
    val isTv = LocalContext.current.isTelevisionDevice()
    if (isTv) {
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = onClick,
            modifier = Modifier.onFocusChanged { 
                isFocused = it.isFocused 
                if (it.isFocused) onInteraction()
            },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.AspectRatio, contentDescription = null, tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                Text(label, color = if (isFocused) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
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
    val isTv = LocalContext.current.isTelevisionDevice()
    val resumeButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) {
            delay(500)
            try {
                resumeButtonFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

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
                        if (isTv) {
                            TvSurface(
                                onClick = onStartOver,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = Color.White
                                ),
                                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.player_resume_start_over).uppercase(), color = Color.Unspecified, fontWeight = FontWeight.Bold)
                                }
                            }

                            TvSurface(
                                onClick = onResume,
                                modifier = Modifier.weight(1.2f).height(48.dp).focusRequester(resumeButtonFocusRequester),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = AppColors.ArenaOrange,
                                    focusedContainerColor = Color.White
                                ),
                                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.player_resume_btn).uppercase(), color = Color.Unspecified, fontWeight = FontWeight.Black)
                                }
                            }
                        } else {
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
}

@Composable
private fun PlayerTopIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, onInteraction: () -> Unit) {
    val isTv = LocalContext.current.isTelevisionDevice()
    if (isTv) {
        var isFocused by remember { mutableStateOf(false) }
        TvSurface(
            onClick = onClick,
            modifier = Modifier.size(40.dp).onFocusChanged { 
                isFocused = it.isFocused 
                if (it.isFocused) onInteraction()
            },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = contentDescription, tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(18.dp))
        }
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
