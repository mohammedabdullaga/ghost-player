package com.eliteprime.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.data.repository.XtreamRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val xtreamRepository: XtreamRepository,
    private val preferenceRepository: PreferenceRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "catalog_daily_sync_work"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<CatalogSyncWorker>(
                12, TimeUnit.HOURS, // Runs every 12 hours (daily check)
                1, TimeUnit.HOURS    // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d("CatalogSyncWorker", "Periodic background sync scheduled (every 12 hours)")
        }
    }

    override suspend fun doWork(): Result {
        Log.i("CatalogSyncWorker", "Starting scheduled background catalog sync...")
        return try {
            val account = xtreamRepository.getAccount()
            if (account == null) {
                Log.d("CatalogSyncWorker", "No active account, skipping sync.")
                return Result.success()
            }

            // Sync categories and all streams (Live, Movies, Series)
            xtreamRepository.syncCategories()
            xtreamRepository.syncAllStreams(
                onLiveProgress = {},
                onMovieProgress = {},
                onSeriesProgress = {}
            )

            preferenceRepository.setLastSyncTimestamp(System.currentTimeMillis())
            Log.i("CatalogSyncWorker", "Background catalog sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("CatalogSyncWorker", "Background catalog sync failed: ${e.message}", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
