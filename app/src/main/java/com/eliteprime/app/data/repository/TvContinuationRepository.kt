package com.eliteprime.app.data.repository

import android.content.Context
import android.util.Log
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.eliteprime.app.data.util.EngageClusterFactory
import com.google.android.engage.service.AppEngagePublishClient
import com.google.android.engage.service.AppEngagePublishStatusCode
import com.google.android.engage.service.PublishStatusRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvContinuationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clusterFactory: EngageClusterFactory
) {
    private val client = AppEngagePublishClient(context)

    suspend fun pushToLauncher(historyItems: List<PlaybackHistory>) {
        try {
            val isAvailable = client.isServiceAvailable.await()
            if (!isAvailable) {
                Log.w("TvContinuation", "Engage service not available")
                return
            }

            if (historyItems.isEmpty()) {
                client.deleteContinuationCluster().await()
            } else {
                val request = clusterFactory.constructContinuationClusterRequest(historyItems)
                client.publishContinuationCluster(request).await()
            }

            updatePublishStatus(AppEngagePublishStatusCode.PUBLISHED)
        } catch (e: Exception) {
            Log.e("TvContinuation", "Failed to push to launcher", e)
            updatePublishStatus(AppEngagePublishStatusCode.NOT_PUBLISHED_SERVICE_ERROR)
        }
    }

    private fun updatePublishStatus(statusCode: Int) {
        client.updatePublishStatus(
            PublishStatusRequest.Builder()
                .setStatusCode(statusCode)
                .build()
        )
    }
}
