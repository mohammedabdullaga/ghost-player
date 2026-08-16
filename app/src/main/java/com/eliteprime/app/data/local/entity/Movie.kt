package com.eliteprime.app.data.local.entity

import com.google.gson.annotations.SerializedName
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Transient
import io.objectbox.relation.ToOne

@Entity
data class Movie(
    @Id(assignable = true) @SerializedName("stream_id") var id: Long = 0,
    @Index @SerializedName("name") var name: String = "",
    @SerializedName("stream_type") var streamType: String = "",
    @SerializedName("stream_icon") var streamIcon: String? = null,
    @SerializedName("added") var added: String? = null,
    @Index @SerializedName("category_id") var remoteCategoryId: String? = null,
    @SerializedName("container_extension") var containerExtension: String? = null,
    @SerializedName("custom_sid") var customSid: String? = null,
    @SerializedName("direct_source") var directSource: String? = null,
    
    // TMDB metadata
    var tmdbId: Long = 0,
    var backdropUrl: String? = null,
    var releaseDate: String? = null,
    var isTrendingThisWeek: Boolean = false,
    var trendingRank: Int = 0,
    
    // Persistent fields (fetched on demand)
    var rating: Double = 0.0,
    var plot: String? = null,
    var cast: String? = null,
    var director: String? = null,
    var genre: String? = null,
    var duration: String? = null,

    @Index var isFavorite: Boolean = false,
    var lastWatchedAt: Long = 0,
    var watchProgress: Long = 0,
    var totalDuration: Long = 0
) {
    lateinit var category: ToOne<Category>
}
