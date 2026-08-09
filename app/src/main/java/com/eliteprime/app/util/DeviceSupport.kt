package com.eliteprime.app.util

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

fun Context.isTelevisionDevice(): Boolean {
    val packageManager = packageManager
    if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
        return true
    }
    if (packageManager.hasSystemFeature("android.software.leanback_only")) {
        return true
    }
    if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
        return true
    }
    if (packageManager.hasSystemFeature("amazon.hardware.fire_tv")) {
        return true
    }
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
        return true
    }

    val screenWidthDp = resources.configuration.screenWidthDp
    return !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) && screenWidthDp >= 900
}

@Composable
fun rememberIsTelevisionDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isTelevisionDevice() }
}

fun Context.isLowEndDevice(): Boolean {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    if (activityManager?.isLowRamDevice == true) return true
    val memInfo = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memInfo)
    val totalRamGb = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
    if (totalRamGb < 2.5) return true
    val cores = Runtime.getRuntime().availableProcessors()
    if (cores <= 4) return true
    return false
}

@Composable
fun rememberIsLowEndDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isLowEndDevice() }
}
