package com.ghost.tv.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ghost.tv.data.local.entity.PlaybackHistory
import com.google.android.engage.common.datamodel.ContinuationCluster
import com.google.android.engage.common.datamodel.AccountProfile
import com.google.android.engage.service.PublishContinuationClusterRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class EngageClusterFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun constructContinuationClusterRequest(historyItems: List<PlaybackHistory>): PublishContinuationClusterRequest {
        val clusterBuilder = ContinuationCluster.Builder()
        
        val profile = AccountProfile.Builder()
            .setAccountId("eliteprime_user")
            .build()
            
        clusterBuilder.setAccountProfile(profile)
        
        for (item in historyItems) {
            val playbackUri = buildPlayerIntentUri(item)
            
            when (item.type) {
                "movie" -> {
                    clusterBuilder.addEntity(EngageEntityConverter.convertToMovieEntity(item, playbackUri))
                }
                "series" -> {
                    clusterBuilder.addEntity(EngageEntityConverter.convertToTvEpisodeEntity(item, playbackUri))
                }
            }
        }
        
        return PublishContinuationClusterRequest.Builder()
            .setContinuationCluster(clusterBuilder.build())
            .build()
    }

    private fun buildPlayerIntentUri(history: PlaybackHistory): Uri {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val intent = (launchIntent ?: Intent()).apply {
            action = Intent.ACTION_VIEW
            putExtra("streamId", history.streamId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
    }
}
