package com.ghost.tv.data.remote

import java.net.URLDecoder
import java.net.URLEncoder

object XtreamUrlFactory {
    private const val UTF_8 = "UTF-8"

    fun encode(value: String): String {
        return URLEncoder.encode(value, UTF_8).replace("+", "%20")
    }

    fun decode(value: String): String {
        return URLDecoder.decode(value, UTF_8)
    }

    fun buildPlaybackUrl(
        serverUrl: String,
        username: String,
        password: String,
        kind: String, // live, movie, series
        streamId: Long,
        extension: String? = null
    ): String {
        val ext = extension ?: when (kind) {
            "live" -> "ts"
            else -> "mp4"
        }
        return serverUrl.trimEnd('/') + "/$kind/" +
            encode(username) + "/" +
            encode(password) + "/" +
            streamId.toString() + "." + ext
    }
}
