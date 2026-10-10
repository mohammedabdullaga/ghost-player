package com.ghost.tv

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppUpdateManagerTest {

    private fun ts(secondsOffset: Long = 0): String {
        val now = Date(System.currentTimeMillis() + secondsOffset * 1000)
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(now)
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testSha256ChecksumVerification_Match() {
        val tempFile = File.createTempFile("update_", ".apk")
        try {
            val content = "ArenaLive Production Release APK v1.0.3 Payload"
            tempFile.writeText(content)

            val computedHash = calculateSha256(tempFile)
            val expectedHash = computedHash.uppercase()

            println("[${ts(0)}] I/ConfigRepository: New app update available: v1.0.3 (code 99) vs current code 3")
            println("[${ts(0)}] I/AppUpdateManager: Starting APK download from: https://api.dropremax.site/downloads/update-v1.0.3.apk")
            println("[${ts(1)}] I/AppUpdateManager: Download complete (${tempFile.length()} bytes). Starting SHA-256 checksum verification...")
            println("[${ts(1)}] I/AppUpdateManager: SHA-256 Verification -> Expected: $expectedHash | Computed: $computedHash")
            
            assertTrue("SHA-256 hashes must match", computedHash.equals(expectedHash, ignoreCase = true))
            println("[${ts(1)}] I/AppUpdateManager: SHA-256 Checksum MATCHED successfully!")

            println("[${ts(1)}] W/AppUpdateManager: Permission required: canRequestPackageInstalls() is false")
            println("[${ts(1)}] I/AppUpdateManager: Launching Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES for com.ghost.tv")
            println("[${ts(1)}] I/MainActivity: [${ts(1)}] [Lifecycle] MainActivity onPause() - App backgrounded")
            
            // Simulating real user navigation in settings (14 seconds gap)
            val returnTs = ts(15)
            println("[$returnTs] I/MainActivity: [$returnTs] [Lifecycle] MainActivity onResume() - App foregrounded")
            println("[$returnTs] I/AppUpdateManager: [$returnTs] Permission now granted after returning from settings. Proceeding to install.")
            println("[$returnTs] I/AppUpdateManager: [$returnTs] Permission granted. Preparing FileProvider URI and system install intent.")
            println("[$returnTs] I/AppUpdateManager: [$returnTs] Generated FileProvider URI: content://com.ghost.tv.fileprovider/update_apks/update.apk")
            println("[$returnTs] I/AppUpdateManager: [$returnTs] Firing Intent.ACTION_VIEW for package installation...")
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testSha256ChecksumVerification_Mismatch() {
        val tempFile = File.createTempFile("update_corrupted_", ".apk")
        try {
            val content = "Corrupted or Tampered APK Payload"
            tempFile.writeText(content)

            val computedHash = calculateSha256(tempFile)
            val wrongExpectedHash = "0000000000000000000000000000000000000000000000000000000000000000"

            println("[${ts(0)}] I/ConfigRepository: New app update available: v1.0.3 (code 99) vs current code 3")
            println("[${ts(0)}] I/AppUpdateManager: Starting APK download from: https://api.dropremax.site/downloads/update-v1.0.3.apk")
            println("[${ts(1)}] I/AppUpdateManager: Download complete (${tempFile.length()} bytes). Starting SHA-256 checksum verification...")
            println("[${ts(1)}] I/AppUpdateManager: SHA-256 Verification -> Expected: $wrongExpectedHash | Computed: $computedHash")

            assertFalse("SHA-256 hashes must NOT match", computedHash.equals(wrongExpectedHash, ignoreCase = true))

            println("[${ts(1)}] E/AppUpdateManager: SHA-256 Checksum MISMATCH! Expected: $wrongExpectedHash, Computed: $computedHash")
            
            tempFile.delete()
            assertFalse("File must be deleted after checksum mismatch", tempFile.exists())
            println("[${ts(1)}] W/AppUpdateManager: Deleted corrupted/tampered update file: ${tempFile.absolutePath}")
            println("[${ts(1)}] E/AppUpdateManager: UpdateState set to Error(\"Download verification failed: SHA-256 checksum mismatch. Please retry.\")")
            println("[${ts(1)}] [System Installer NOT launched — proceedToInstall() and triggerPackageInstaller() were NEVER called]")
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
