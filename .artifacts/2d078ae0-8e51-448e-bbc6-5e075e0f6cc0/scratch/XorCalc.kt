fun main() {
    val key = "ArenaLiveSecureKey2026"
    val strings = mapOf(
        "CONFIG" to "https://api.arenaliveapp.top/api/config",
        "TMDB" to "https://api.themoviedb.org/3/",
        "UA" to "ArenaLive/1.0 (Android; Media3; OkHttp)",
        "FALLBACK_HOST" to "cdn.sharkbh.online",
        "SS_PASS" to "Elitba7rainVpnService",
        "SS_METHOD" to "chacha20-ietf-poly1305",
        "PROXY_AUTH" to "Proxy-Authorization: Basic ZWxpdHByaW1lOkVlYmgxMjM0"
    )

    strings.forEach { (name, s) ->
        val res = s.mapIndexed { i, c ->
            val b = c.code xor key[i % key.length].code
            "0x${b.toString(16).uppercase().padStart(2, '0')}.toByte()"
        }.joinToString(", ")
        println("$name:")
        println("byteArrayOf($res)")
        println()
    }
}
