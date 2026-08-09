package com.eliteprime.app.domain.model

data class StreamInfo(
    val streamId: Long,
    val name: String,
    val url: String,
    val type: StreamType,
    val extension: String? = null,
    val categoryId: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null
)

enum class StreamType {
    LIVE, MOVIE, SERIES, UNKNOWN
}

enum class DecoderMode {
    AUTO, HARDWARE, SOFTWARE, COMPATIBILITY
}

enum class PlayerSurfaceMode {
    AUTO, SURFACE_VIEW, TEXTURE_VIEW
}

enum class VodHttpProtocolMode {
    COMPATIBILITY_HTTP1, AUTO
}

enum class LiveStreamFormatMode {
    AUTO, HLS, MPEG_TS
}
