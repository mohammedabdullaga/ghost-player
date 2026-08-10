package com.eliteprime.app.data.manager

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.eliteprime.app.MainActivity
import com.eliteprime.app.R
import com.eliteprime.app.domain.model.DownloadStatus
import com.eliteprime.app.domain.repository.DownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadManager: DownloadManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "downloads_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        observeDownloads()
        return START_STICKY
    }

    private fun observeDownloads() {
        serviceScope.launch {
            downloadManager.observeAllDownloads().collect { downloads ->
                val active = downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
                if (active.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
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
                    startForeground(NOTIFICATION_ID, notification)
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
