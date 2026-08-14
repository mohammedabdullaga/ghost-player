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
     * Custom ranking logic that considers Format (resolution/frame rate) if available.
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

        // Log all available hardware decoders for diagnostic
        Log.d(TAG, "Available decoders for $mimeType: ${allDecoders.joinToString { "${it.name}(HW=${!it.softwareOnly})" }}")

        val tierA = mutableListOf<MediaCodecInfo>() // Hardware + Explicitly Supported
        val tierB = mutableListOf<MediaCodecInfo>() // Hardware + Capability Exceeded (Fallback)
        val tierC = mutableListOf<MediaCodecInfo>() // Software

        for (info in allDecoders) {
            if (info.softwareOnly) {
                tierC.add(info)
            } else {
                // Hardware decoder. Check capabilities if format is available.
                val support = if (format != null) {
                    try {
                        info.isFormatSupported(context, format)
                    } catch (e: Exception) {
                        false
                    }
                } else true // Assume supported if resolution unknown

                if (support) {
                    tierA.add(info)
                } else {
                    tierB.add(info)
                }
            }
        }

        val result = mutableListOf<MediaCodecInfo>()
        var selectedTier = "UNKNOWN"

        when (decoderMode) {
            "HARDWARE" -> {
                if (tierA.isNotEmpty()) {
                    result.addAll(tierA)
                    selectedTier = "TIER_A (HW_OPTIMAL)"
                } else if (tierB.isNotEmpty()) {
                    result.addAll(tierB)
                    selectedTier = "TIER_B (HW_FALLBACK)"
                }
            }
            "SOFTWARE" -> {
                if (tierC.isNotEmpty()) {
                    result.addAll(tierC)
                    selectedTier = "TIER_C (SW)"
                } else if (tierA.isNotEmpty()) {
                    result.addAll(tierA)
                    selectedTier = "TIER_A (HW_AUTO_FALLBACK)"
                }
            }
            else -> { // AUTO
                if (tierA.isNotEmpty()) {
                    result.addAll(tierA)
                    selectedTier = "TIER_A (HW_OPTIMAL)"
                } else if (tierB.isNotEmpty()) {
                    result.addAll(tierB)
                    selectedTier = "TIER_B (HW_FALLBACK)"
                } else if (tierC.isNotEmpty()) {
                    result.addAll(tierC)
                    selectedTier = "TIER_C (SW)"
                }
            }
        }
        
        if (result.isEmpty() && allDecoders.isNotEmpty()) {
            result.addAll(allDecoders)
            selectedTier = "GLOBAL_FALLBACK"
        }

        // TIER RANKING: Favor native Amlogic 'vdec' over 'awesome' or 'c2' wrappers for 4K stability
        if (result.size > 1) {
            result.sortWith(compareByDescending<MediaCodecInfo> { 
                val name = it.name.lowercase()
                name.contains("vdec") || 
                (name.contains("amlogic") && (name.contains("video") || name.contains("avc") || name.contains("hevc")))
            }.thenByDescending { 
                it.name.lowercase().contains("hw") 
            }.thenBy { 
                it.name.lowercase().contains("google") 
            })
        }

        val decoderName = result.firstOrNull()?.name ?: "None"
        val isHardware = result.firstOrNull()?.softwareOnly == false
        val resolution = if (format != null) "${format.width}x${format.height}" else "Unknown"
        val bitrate = if (format != null) " | Bitrate: ${format.bitrate}" else ""
        val codecs = if (format != null) " | Codecs: ${format.codecs}" else ""
        
        Log.i(TAG, "Decoder Selection Tier: $selectedTier")
        Log.i(TAG, "  - Selected: $decoderName (HW=$isHardware)")
        Log.i(TAG, "  - MimeType: $mimeType$codecs$bitrate")
        Log.i(TAG, "  - Resolution: $resolution")
        Log.i(TAG, "  - Mode: $decoderMode")
        Log.i(TAG, "  - Secure: $requiresSecureDecoder | Tunneling: $requiresTunnelingDecoder")
        Log.i(TAG, "  - Device: ${Build.MODEL} (${Build.MANUFACTURER} / ${Build.HARDWARE})")

        return result
    }
}
