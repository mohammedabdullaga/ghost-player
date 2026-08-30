package com.eliteprime.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.eliteprime.app.data.repository.XtreamRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Silent background VOD-only worker for TV devices.
 *
 * Constraints:
 * - Network connected
 * - Device idle (TV not in active use — screen off / launcher)
 * - Battery not low
 *
 * Runs every 6 hours with 2h flex window. Designed to be invisible to the user.
 */
@HiltWorker
class VodSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val xtreamRepository: XtreamRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "vod_silent_sync"

        fun scheduleSilentSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)       // Only sync when TV is idle
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<VodSyncWorker>(
                6, TimeUnit.HOURS,
                2, TimeUnit.HOURS                  // 2h flex window
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d("VodSyncWorker", "Silent VOD background sync scheduled (every 6h, idle-only)")
        }
    }

    override suspend fun doWork(): Result {
        Log.i("VodSyncWorker", "Starting silent VOD background sync...")
        return try {
            val account = xtreamRepository.getAccount()
            if (account == null) {
                Log.d("VodSyncWorker", "No active account, skipping VOD sync.")
                return Result.success()
            }

            var movieCount = 0
            var seriesCount = 0

            xtreamRepository.syncVodOnly(
                onMovieProgress = { movieCount = it },
                onSeriesProgress = { seriesCount = it }
            )

            Log.i("VodSyncWorker", "Silent VOD sync done — Movies: $movieCount, Series: $seriesCount")
            Result.success()
        } catch (e: Exception) {
            Log.e("VodSyncWorker", "Silent VOD sync failed: ${e.message}", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}