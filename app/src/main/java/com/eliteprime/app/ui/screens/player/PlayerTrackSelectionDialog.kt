package com.eliteprime.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eliteprime.app.player.PlayerTrack
import com.eliteprime.app.ui.theme.AppColors

@Composable
fun PlayerTrackSelectionDialog(
    title: String,
    tracks: List<PlayerTrack>,
    onTrackSelected: (PlayerTrack) -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF12151B),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(tracks) { track ->
                        TrackItem(
                            track = track,
                            onClick = { 
                                onTrackSelected(track)
                                onDismissRequest()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("CLOSE", color = AppColors.ArenaOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: PlayerTrack,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (track.isSelected) AppColors.ArenaOrange.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = track.label,
                color = if (track.isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
            if (track.isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = AppColors.ArenaOrange,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
