package com.eliteprime.app.util

import java.util.concurrent.TimeUnit

fun formatPositionMs(positionMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(positionMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(positionMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(positionMs) % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
