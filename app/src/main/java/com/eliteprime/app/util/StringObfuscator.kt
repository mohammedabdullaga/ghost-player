package com.eliteprime.app.util

/**
 * Simple XOR-based string obfuscation to hide endpoints from static analysis.
 */
object StringObfuscator {
    // Hidden key used for XORing. Changed from generic to project-specific.
    private val KEY = "ArenaLiveSecureKey2026".toByteArray()

    /**
     * Decodes an obfuscated byte array into its original string.
     */
    fun decode(data: ByteArray): String {
        val result = ByteArray(data.size)
        for (i in data.indices) {
            // Masking with 0xFF to avoid potential sign extension issues during XOR
            val dataByte = data[i].toInt() and 0xFF
            val keyByte = KEY[i % KEY.size].toInt() and 0xFF
            result[i] = (dataByte xor keyByte).toByte()
        }
        return String(result, Charsets.UTF_8)
    }
}
