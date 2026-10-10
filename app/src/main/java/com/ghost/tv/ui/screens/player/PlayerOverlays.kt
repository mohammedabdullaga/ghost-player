package com.ghost.tv.ui.screens.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.tv.R
import com.ghost.tv.player.PlayerStats
import com.ghost.tv.ui.theme.AppColors

@Composable
fun DiagnosticsOverlay(
    stats: PlayerStats,
    modifier: Modifier = Modifier
) {
    PlayerOverlayPanel(modifier = modifier.width(320.dp)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.player_diagnostics_title),
                color = AppColors.ArenaOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            DiagnosticRow(stringResource(R.string.diag_resolution), "${stats.width}x${stats.height}")
            DiagnosticRow(stringResource(R.string.diag_video_codec), stats.videoCodec)
            DiagnosticRow(stringResource(R.string.diag_audio_codec), stats.audioCodec)
            DiagnosticRow(stringResource(R.string.diag_bitrate), "${stats.videoBitrate / 1000} kbps")
            DiagnosticRow(stringResource(R.string.diag_dropped_frames), stats.droppedFrames.toString())
            DiagnosticRow(stringResource(R.string.diag_buffer), "${stats.bufferedDurationMs} ms")
            
            Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
            
            DiagnosticRow(stringResource(R.string.diag_video_decoder), stats.videoDecoderName)
            DiagnosticRow(stringResource(R.string.diag_audio_decoder), stats.audioDecoderName)
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PlayerOverlayPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.8f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        tonalElevation = 12.dp
    ) {
        content()
    }
}
