package com.eliteprime.app.data.local.entity

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany

@Entity
data class Season(
    @Id var id: Long = 0,
    var name: String = "",
    var seasonNumber: Int = 0,
    var seriesId: Long = 0
) {
    @Backlink(to = "season")
    lateinit var episodes: ToMany<Episode>
}
