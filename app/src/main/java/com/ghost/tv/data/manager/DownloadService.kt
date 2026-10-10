package com.ghost.tv.data.manager

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ghost.tv.MainActivity
import com.ghost.tv.R
import com.ghost.tv.domain.model.DownloadStatus
import com.ghost.tv.domain.repository.DownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadManager: DownloadManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "downloads_channel"
    private var stopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        moveToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        moveToForeground()
        observeDownloads()
        return START_STICKY
    }

    private fun moveToForeground(notification: Notification? = null) {
        val notif = notification ?: buildNotification(
            title = "Download starting…",
            progress = 0,
            speed = 0,
            isIndeterminate = true
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } catch (e: Exception) {
            Log.e("DownloadService", "Error calling startForeground: ${e.message}", e)
        }
    }

    private fun observeDownloads() {
        serviceScope.launch {
            downloadManager.observeAllDownloads().collect { downloads ->
                val active = downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
                if (active.isEmpty()) {
                    if (stopJob == null || stopJob?.isActive != true) {
                        stopJob = serviceScope.launch {
                            delay(3000)
                            val recheck = downloadManager.observeAllDownloads().firstOrNull()?.filter { 
                                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING 
                            } ?: emptyList()
                            if (recheck.isEmpty()) {
                                try {
                                    stopForeground(STOP_FOREGROUND_REMOVE)
                                } catch (e: Exception) {
                                    Log.e("DownloadService", "Error in stopForeground", e)
                                }
                                stopSelf()
                            }
                        }
                    }
                } else {
                    stopJob?.cancel()
                    stopJob = null
                    val downloading = active.find { it.status == DownloadStatus.DOWNLOADING } ?: active.first()
                    val progress = downloading.let { 
                        it.totalBytes?.let { total -> (it.bytesWritten.toFloat() / total * 100).toInt() }
                    } ?: 0
                    
                    val notification = buildNotification(
                        title = downloading.contentName,
                        progress = progress,
                        speed = downloading.downloadSpeedBps,
                        isIndeterminate = downloading.status == DownloadStatus.PENDING || downloading.totalBytes == null
                    )
                    moveToForeground(notification)
                }
            }
        }
    }

    private fun buildNotification(title: String, progress: Int, speed: Long, isIndeterminate: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val speedText = formatSpeed(speed)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Downloading • $speedText")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, isIndeterminate)
            .build()
    }

    private fun formatSpeed(bps: Long): String {
        return when {
            bps >= 1024 * 1024 -> "%.1f MB/s".format(bps / (1024f * 1024f))
            bps >= 1024 -> "%.1f KB/s".format(bps / 1024f)
            else -> "$bps B/s"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active downloads"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
