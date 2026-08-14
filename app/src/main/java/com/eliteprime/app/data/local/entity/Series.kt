package com.eliteprime.app.data.local.entity

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
    @SerializedName("stream_type") var streamType: String = "",
    @SerializedName("cover") var cover: String? = null,
    @SerializedName("last_modified") var lastModified: String? = null,
    @Index @SerializedName("category_id") var remoteCategoryId: String? = null,
    
    // Non-persistent metadata (fetched on demand)
    @Transient var plot: String? = null,
    @Transient var cast: String? = null,
    @Transient var director: String? = null,
    @Transient var genre: String? = null,
    @Transient var releaseDate: String? = null,
    @Transient var rating: Double = 0.0,

    // TMDB metadata
    var tmdbId: Long = 0,
    var backdropUrl: String? = null,
    var isTrendingThisWeek: Boolean = false,
    var trendingRank: Int = 0,
    @Index var isFavorite: Boolean = false,
    var lastWatchedAt: Long = 0
) {
    lateinit var category: ToOne<Category>
}
