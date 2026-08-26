package com.eliteprime.app.data.local.entity

import com.eliteprime.app.data.remote.util.FlexibleStringAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Transient
import io.objectbox.relation.ToOne

@Entity
data class Series(
    @Id(assignable = true) @SerializedName("series_id") var id: Long = 0,
    @Index @SerializedName("name") var name: String = "",
    @SerializedName("stream_type") @JsonAdapter(FlexibleStringAdapter::class) var streamType: String = "",
    @SerializedName("cover") var cover: String? = null,
    @SerializedName("last_modified") var lastModified: String? = null,
    @Index @SerializedName("category_id") @JsonAdapter(FlexibleStringAdapter::class) var remoteCategoryId: String? = null,
    
    // Persistent metadata (fetched on demand)
    @JsonAdapter(FlexibleStringAdapter::class) var plot: String? = null,
    @JsonAdapter(FlexibleStringAdapter::class) var cast: String? = null,
    @JsonAdapter(FlexibleStringAdapter::class) var director: String? = null,
    @JsonAdapter(FlexibleStringAdapter::class) var genre: String? = null,
    var releaseDate: String? = null,
    var rating: Double = 0.0,

    // TMDB metadata
    var tmdbId: Long = 0,
    var backdropUrl: String? = null,
    var isTrendingThisWeek: Boolean = false,
    var trendingRank: Int = 0,
    @Index var isFavorite: Boolean = false,
    var order: Int = 0,
    var lastWatchedAt: Long = 0
) {
    lateinit var category: ToOne<Category>
}
