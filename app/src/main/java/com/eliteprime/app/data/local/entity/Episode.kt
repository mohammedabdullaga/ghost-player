package com.eliteprime.app.data.local.entity

import com.google.gson.annotations.SerializedName
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Transient
import io.objectbox.relation.ToOne

@Entity
data class Episode(
    @Id(assignable = true) @SerializedName("id") var id: Long = 0,
    @SerializedName("title") var title: String = "",
    @SerializedName("container_extension") var containerExtension: String? = null,
    @SerializedName("season") var seasonNumber: Int = 0,
    @SerializedName("episode_num") var episodeNumber: Int = 0,
    
    // Non-persistent metadata
    @Transient var duration: String? = null,
    @Transient var plot: String? = null,

    @SerializedName("cover_big") var coverUrl: String? = null,
    var streamId: Long = 0, // Resolved stream id for playback
    var seriesId: Long = 0,
    var watchProgress: Long = 0,
    var totalDuration: Long = 0,
    var lastWatchedAt: Long = 0
) {
    lateinit var season: ToOne<Season>
}
