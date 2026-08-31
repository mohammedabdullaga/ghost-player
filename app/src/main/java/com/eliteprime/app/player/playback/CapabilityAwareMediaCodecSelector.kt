package com.eliteprime.app.player.playback

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import java.util.Locale

@OptIn(UnstableApi::class)
class CapabilityAwareMediaCodecSelector(
    private val context: Context,
    private val decoderMode: String = "AUTO"
) : MediaCodecSelector {

    private val TAG = "CodecSelector"

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        return getRankedDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder, null)
    }

    /**
     * Custom ranking logic from original app + HEVC stability fixes.
     */
    fun getRankedDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
        format: Format?
    ): List<MediaCodecInfo> {
        val allDecoders = MediaCodecUtil.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        )

        if (allDecoders.isEmpty()) return emptyList()

        val policy = when(decoderMode) {
            "HARDWARE" -> ActiveDecoderPolicy.HARDWARE_PREFERRED
            "SOFTWARE" -> ActiveDecoderPolicy.SOFTWARE_PREFERRED
            "COMPATIBILITY" -> ActiveDecoderPolicy.COMPATIBILITY
            else -> ActiveDecoderPolicy.AUTO
        }

        val result = allDecoders.toMutableList()
        
        // SORTING: Combined logic from original app and current Amlogic fixes
        result.sortWith(
            compareBy<MediaCodecInfo> { info ->
                // 1. Hardware preference
                when (policy) {
                    ActiveDecoderPolicy.AUTO -> 0
                    ActiveDecoderPolicy.HARDWARE_PREFERRED -> if (isSoftwareCodec(info.name)) 1 else 0
                    ActiveDecoderPolicy.SOFTWARE_PREFERRED,
                    ActiveDecoderPolicy.COMPATIBILITY -> if (isSoftwareCodec(info.name)) 0 else 1
                }
            }.thenByDescending { info ->
                // 2. Vendor-native decoder priority
                val name = info.name.lowercase(Locale.ROOT)
                // Amlogic vdec
                val hasAmlogic = name.contains("vdec") || (name.contains("amlogic") && (name.contains("video") || name.contains("avc") || name.contains("hevc")))
                // MediaTek (TCL, Haier, Philips, Sony Android TVs)
                val hasMediatek = name.contains("mtk") || name.contains("mediatek")
                // Samsung Exynos
                val hasExynos = name.contains("exynos") || name.contains("samsung")
                // Qualcomm
                val hasQualcomm = name.contains("qcom") || name.contains("qualcomm")
                hasAmlogic || hasMediatek || hasExynos || hasQualcomm
            }.thenByDescending { info ->
                // 3. Hardware acceleration flag
                !info.softwareOnly
            }.thenBy { info ->
                // 4. Google generic wrappers (lower priority)
                info.name.lowercase(Locale.ROOT).contains("google")
            }
        )

        val decoderName = result.firstOrNull()?.name ?: "None"
        val isHardware = result.firstOrNull()?.softwareOnly == false
        
        // Detailed logging for diagnostic
        if (format != null) {
            Log.i(TAG, "Selected Decoder: $decoderName for ${format.width}x${format.height} (HW=$isHardware, Policy=$policy)")
        }

        return result
    }

    companion object {
        fun isSoftwareCodec(name: String): Boolean {
            val normalized = name.lowercase(Locale.ROOT)
            return normalized.startsWith("omx.google.") ||
                normalized.startsWith("c2.android.") ||
                normalized.contains("ffmpeg") ||
                normalized.contains("avcodec")
        }

        fun isMediatek(): Boolean {
            return Build.HARDWARE.contains("mt", ignoreCase = true) ||
                Build.BOARD.contains("mt", ignoreCase = true) ||
                Build.HARDWARE.contains("mediatek", ignoreCase = true) ||
                Build.BOARD.contains("mediatek", ignoreCase = true)
        }

        fun isAmlogic(): Boolean {
            return Build.HARDWARE.contains("amlogic", ignoreCase = true) ||
                Build.BOARD.contains("amlogic", ignoreCase = true)
        }
    }
}
