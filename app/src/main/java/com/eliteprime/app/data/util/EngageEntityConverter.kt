package com.eliteprime.app.data.util

import android.net.Uri
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.google.android.engage.common.datamodel.Image
import com.google.android.engage.video.datamodel.MovieEntity
import com.google.android.engage.video.datamodel.TvEpisodeEntity
import com.google.android.engage.video.datamodel.WatchNextType
import com.google.android.engage.common.datamodel.ContentAvailability

object EngageEntityConverter {

    fun convertToMovieEntity(item: PlaybackHistory, playbackUri: Uri): MovieEntity {
        val imageBuilder = Image.Builder()
            .setImageUri(Uri.parse(item.posterUrl ?: ""))

        return MovieEntity.Builder()
            .setName(item.name)
            .setEntityId("movie:${item.streamId}")
            .addPosterImage(imageBuilder.build())
            .setPlayBackUri(playbackUri)
            .setAvailability(ContentAvailability.AVAILABILITY_FREE_WITH_SUBSCRIPTION)
            .setDurationMillis(item.durationMs)
            .setLastEngagementTimeMillis(item.lastPlayedAt)
            .setLastPlayBackPositionTimeMillis(item.lastPositionMs)
            .setWatchNextType(WatchNextType.TYPE_CONTINUE)
            .build()
    }

    fun convertToTvEpisodeEntity(item: PlaybackHistory, playbackUri: Uri): TvEpisodeEntity {
        val imageBuilder = Image.Builder()
            .setImageUri(Uri.parse(item.posterUrl ?: ""))

        return TvEpisodeEntity.Builder()
            .setName(item.name)
            .setEntityId("episode:${item.streamId}")
            .addPosterImage(imageBuilder.build())
            .setPlayBackUri(playbackUri)
            .setAvailability(ContentAvailability.AVAILABILITY_FREE_WITH_SUBSCRIPTION)
            .setDurationMillis(item.durationMs)
            .setLastEngagementTimeMillis(item.lastPlayedAt)
            .setLastPlayBackPositionTimeMillis(item.lastPositionMs)
            .setWatchNextType(WatchNextType.TYPE_CONTINUE)
            .build()
    }
}
