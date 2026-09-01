package com.eliteprime.app.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.compositionLocalOf

enum class RenderTier {
    STANDARD,
    REDUCED,
    LITE
}

object DeviceRenderProfile {
    private const val TAG = "DeviceRenderProfile"
    private var tier: RenderTier = RenderTier.STANDARD
    private var isLiteMode: Boolean = false

    fun initialize(context: Context, liteMode: Boolean = false) {
        // Lite mode only applies to TV devices — mobile never enters Lite mode.
        // SharedPreferences from a TV install can leak liteMode=true to mobile.
        val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        isLiteMode = isTv && liteMode
        if (isLiteMode) {
            tier = RenderTier.LITE
            Log.i(TAG, "Device Profile: Tier=LITE (User configured Lite Mode)")
            return
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamGb = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()

        // Threshold: REDUCED if RAM <= 2.1GB OR Cores <= 4
        // The Xiaomi MiTV-AFKR0 has 2GB and 4 cores (Amlogic T962H or similar)
        tier = if (totalRamGb <= 2.1 || cores <= 4) {
            RenderTier.REDUCED
        } else {
            RenderTier.STANDARD
        }

        Log.i(TAG, "Device Profile: RAM=${"%.2f".format(totalRamGb)}GB, Cores=$cores -> Tier=$tier")
    }

    fun setLiteMode(enabled: Boolean) {
        isLiteMode = enabled
        if (enabled) {
            tier = RenderTier.LITE
        } else if (tier == RenderTier.LITE) {
            tier = RenderTier.REDUCED
        }
    }

    fun getTier(): RenderTier = tier
    fun isLiteMode(): Boolean = tier == RenderTier.LITE || isLiteMode
}

val LocalRenderTier = compositionLocalOf { RenderTier.STANDARD }
