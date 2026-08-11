package com.eliteprime.app.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class TrendingCache(
    @Id var id: Long = 0,
    var type: String = "", // "movie" or "tv"
    var tmdbId: Int = 0,
    var title: String = "",
    var overview: String = "",
    var posterUrl: String? = null,
    var backdropUrl: String? = null,
    var releaseDate: String? = null,
    var voteAverage: Double = 0.0,
    var genreIdsJson: String = "",
    var genreNamesJson: String = "",
    var lastUpdated: Long = 0
)
