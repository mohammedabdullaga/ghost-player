package com.eliteprime.app.util

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.compositionLocalOf

enum class RenderTier {
    STANDARD,
    REDUCED
}

object DeviceRenderProfile {
    private const val TAG = "DeviceRenderProfile"
    private var tier: RenderTier = RenderTier.STANDARD

    fun initialize(context: Context) {
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

    fun getTier(): RenderTier = tier
}

val LocalRenderTier = compositionLocalOf { RenderTier.STANDARD }
