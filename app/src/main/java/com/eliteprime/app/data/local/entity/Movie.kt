package com.eliteprime.app.data.local.entity

import com.google.gson.annotations.SerializedName
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity
data class Movie(
    @Id(assignable = true) @SerializedName("stream_id") var id: Long = 0,
    @SerializedName("name") var name: String = "",
    @SerializedName("stream_type") var streamType: String = "",
    @SerializedName("stream_icon") var streamIcon: String? = null,
    @SerializedName("added") var added: String? = null,
    @SerializedName("category_id") var remoteCategoryId: String? = null,
    @SerializedName("container_extension") var containerExtension: String? = null,
    @SerializedName("custom_sid") var customSid: String? = null,
    @SerializedName("direct_source") var directSource: String? = null,
    
    // TMDB metadata
    var tmdbId: Long = 0,
    var rating: Double = 0.0,
    var backdropUrl: String? = null,
    var releaseDate: String? = null,
    var isTrendingThisWeek: Boolean = false,
    var trendingRank: Int = 0,
    var plot: String? = null,
    var cast: String? = null,
    var director: String? = null,
    var genre: String? = null,
    var duration: String? = null,
    var isFavorite: Boolean = false,
    var lastWatchedAt: Long = 0
) {
    lateinit var category: ToOne<Category>
}
