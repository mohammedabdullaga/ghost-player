package com.eliteprime.app.domain.model

data class StreamInfo(
    val streamId: Long,
    val name: String,
    val url: String,
    val streamType: StreamType = StreamType.UNKNOWN,
    val title: String? = null,
    val userAgent: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isLive: Boolean = false,
    val fallbackUrls: List<String> = emptyList()
)

enum class StreamType {
    HLS,
    DASH,
    SMOOTH_STREAMING,
    MPEG_TS,
    PROGRESSIVE,
    RTSP,
    UNKNOWN;

    companion object {
        fun fromContainerExtension(ext: String?): StreamType {
            return when (ext?.trim()?.removePrefix(".")?.lowercase()) {
                "ts" -> MPEG_TS
                "m3u8" -> HLS
                "mpd" -> DASH
                "ism", "isml" -> SMOOTH_STREAMING
                "mp4", "mkv", "avi", "mov", "mp3", "aac", "m4a", "flv", "webm" -> PROGRESSIVE
                else -> UNKNOWN
            }
        }
    }
}

enum class DecoderMode {
    AUTO,
    HARDWARE,
    SOFTWARE,
    COMPATIBILITY
}

enum class PlayerSurfaceMode {
    AUTO,
    SURFACE_VIEW,
    TEXTURE_VIEW
}

enum class VodHttpProtocolMode {
    COMPATIBILITY_HTTP1,
    AUTO
}

enum class LiveStreamFormatMode {
    AUTO,
    HLS,
    MPEG_TS
}
