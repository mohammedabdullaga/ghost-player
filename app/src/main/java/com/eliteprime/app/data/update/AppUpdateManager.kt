package com.eliteprime.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.eliteprime.app.domain.model.AppUpdateConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateState {
    object Idle : UpdateState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    object VerifyingChecksum : UpdateState()
    object PermissionRequired : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val TAG = "AppUpdateManager"
    private fun ts(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var pendingApkFile: File? = null

    fun resetState() {
        _updateState.value = UpdateState.Idle
        pendingApkFile = null
    }

    suspend fun downloadAndInstall(updateConfig: AppUpdateConfig) = withContext(Dispatchers.IO) {
        val apkUrl = updateConfig.resolvedApkUrl
        if (apkUrl.isBlank()) {
            _updateState.value = UpdateState.Error("Invalid APK download URL")
            return@withContext
        }

        val apkFile = File(context.cacheDir, "update.apk")
        if (apkFile.exists()) {
            apkFile.delete()
        }

        try {
            Log.i(TAG, "[${ts()}] Starting APK download from: $apkUrl")
            _updateState.value = UpdateState.Downloading(0f, 0, 0)

            val request = Request.Builder()
                .url(apkUrl)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "Download failed with HTTP code: ${response.code}"
                Log.e(TAG, errorMsg)
                _updateState.value = UpdateState.Error(errorMsg)
                return@withContext
            }

            val body = response.body ?: run {
                val errorMsg = "Response body was null"
                Log.e(TAG, errorMsg)
                _updateState.value = UpdateState.Error(errorMsg)
                return@withContext
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { inputStream ->
                FileOutputStream(apkFile).use { outputStream ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
                        _updateState.value = UpdateState.Downloading(progress, downloadedBytes, totalBytes)
                    }
                }
            }

            Log.i(TAG, "[${ts()}] Download complete ($downloadedBytes bytes). Starting SHA-256 checksum verification...")
            _updateState.value = UpdateState.VerifyingChecksum

            // TASK 2: SHA-256 Checksum Verification
            val expectedSha256 = updateConfig.sha256?.trim()
            if (!expectedSha256.isNullOrBlank()) {
                val computedHash = calculateSha256(apkFile)
                Log.i(TAG, "[${ts()}] SHA-256 Verification -> Expected: $expectedSha256 | Computed: $computedHash")

                if (!computedHash.equals(expectedSha256, ignoreCase = true)) {
                    Log.e(TAG, "[${ts()}] SHA-256 Checksum MISMATCH! Expected: $expectedSha256, Computed: $computedHash")
                    if (apkFile.exists()) {
                        apkFile.delete()
                        Log.w(TAG, "[${ts()}] Deleted corrupted/tampered update file: ${apkFile.absolutePath}")
                    }
                    _updateState.value = UpdateState.Error("Download verification failed: SHA-256 checksum mismatch. Please retry.")
                    return@withContext
                }
                Log.i(TAG, "[${ts()}] SHA-256 Checksum MATCHED successfully!")
            } else {
                Log.w(TAG, "[${ts()}] No SHA-256 checksum provided in update metadata. Skipping verification.")
            }

            pendingApkFile = apkFile
            proceedToInstall(apkFile)

        } catch (e: Exception) {
            Log.e(TAG, "[${ts()}] Error downloading update: ${e.message}", e)
            if (apkFile.exists()) {
                apkFile.delete()
            }
            _updateState.value = UpdateState.Error("Download error: ${e.message}")
        }
    }

    // TASK 1: Unknown-Sources Permission Check
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            Log.d(TAG, "[${ts()}] canRequestPackageInstalls() = $canInstall")
            canInstall
        } else {
            true
        }
    }

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.i(TAG, "[${ts()}] Launching Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES for ${context.packageName}")
            context.startActivity(intent)
        }
    }

    fun proceedToInstall(apkFile: File? = pendingApkFile) {
        val file = apkFile ?: run {
            Log.e(TAG, "[${ts()}] No APK file pending installation")
            return
        }

        if (!canRequestPackageInstalls()) {
            Log.w(TAG, "[${ts()}] Permission required: canRequestPackageInstalls() is false")
            _updateState.value = UpdateState.PermissionRequired
            return
        }

        Log.i(TAG, "[${ts()}] Permission granted. Preparing FileProvider URI and system install intent.")
        _updateState.value = UpdateState.ReadyToInstall(file)
        triggerPackageInstaller(file)
    }

    fun recheckPermissionOnResume() {
        val file = pendingApkFile ?: return
        if (canRequestPackageInstalls() && _updateState.value is UpdateState.PermissionRequired) {
            Log.i(TAG, "[${ts()}] Permission now granted after returning from settings. Proceeding to install.")
            proceedToInstall(file)
        }
    }

    private fun triggerPackageInstaller(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            Log.i(TAG, "[${ts()}] Generated FileProvider URI: $apkUri for file: ${apkFile.absolutePath}")
            Log.i(TAG, "[${ts()}] Firing Intent.ACTION_VIEW for package installation...")
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            })

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            Log.i(TAG, "Firing Intent.ACTION_VIEW for package installation...")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            _updateState.value = UpdateState.Error("Failed to launch package installer: ${e.message}")
        }
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
}
