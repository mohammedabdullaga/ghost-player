package com.ghost.tv.data.local.entity

import com.ghost.tv.data.remote.util.FlexibleStringAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Transient
import io.objectbox.relation.ToOne

@Entity
data class LiveStream(
    @Id(assignable = true) @SerializedName("stream_id") var id: Long = 0,
    @Index @SerializedName("name") var name: String = "",
    @SerializedName("stream_type") @JsonAdapter(FlexibleStringAdapter::class) var streamType: String = "",
    @SerializedName("stream_icon") var streamIcon: String? = null,
    @SerializedName("epg_channel_id") var epgChannelId: String? = null,
    @SerializedName("added") var added: String? = null,
    @Index @SerializedName("category_id") @JsonAdapter(FlexibleStringAdapter::class) var remoteCategoryId: String? = null,
    @SerializedName("custom_sid") var customSid: String? = null,
    @SerializedName("tv_archive") var tvArchive: Int = 0,
    @SerializedName("direct_source") var directSource: String? = null,
    @SerializedName("tv_archive_duration") var tvArchiveDuration: Int = 0,
    @SerializedName("thumbnail") var thumbnail: String? = null,

    @Index var isFavorite: Boolean = false,
    var isHidden: Boolean = false,
    var isLocked: Boolean = false,
    var order: Int = 0,
    var lastWatchedAt: Long = 0,

    // Non-persistent metadata
    @Transient var rating: Double = 0.0,

    // TMDB metadata (if matched)
    var tmdbId: Long = 0,
    var backdropUrl: String? = null,
    var releaseDate: String? = null,
    var isTrendingThisWeek: Boolean = false,
    var trendingRank: Int = 0
) {
    lateinit var category: ToOne<Category>
}
