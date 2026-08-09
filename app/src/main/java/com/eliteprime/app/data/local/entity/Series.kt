package com.eliteprime.app.data.local.entity

import com.google.gson.annotations.SerializedName
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity
data class Series(
    @Id(assignable = true) @SerializedName("series_id") var id: Long = 0,
    @SerializedName("name") var name: String = "",
    @SerializedName("stream_type") var streamType: String = "",
    @SerializedName("cover") var cover: String? = null,
    @SerializedName("plot") var plot: String? = null,
    @SerializedName("cast") var cast: String? = null,
    @SerializedName("director") var director: String? = null,
    @SerializedName("genre") var genre: String? = null,
    @SerializedName("releaseDate") var releaseDate: String? = null,
    @SerializedName("last_modified") var lastModified: String? = null,
    @SerializedName("rating") var rating: Double = 0.0,
    @SerializedName("category_id") var remoteCategoryId: String? = null,
    
    // TMDB metadata
    var tmdbId: Long = 0,
    var backdropUrl: String? = null,
    var isTrendingThisWeek: Boolean = false,
    var trendingRank: Int = 0
) {
    lateinit var category: ToOne<Category>
}
