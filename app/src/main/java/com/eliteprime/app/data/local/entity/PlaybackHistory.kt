package com.eliteprime.app.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class PlaybackHistory(
    @Id var id: Long = 0,
    @Index var streamId: Long = 0,
    var name: String = "",
    var type: String = "", // live, movie, series
    var lastPositionMs: Long = 0,
    var durationMs: Long = 0,
    var lastPlayedAt: Long = System.currentTimeMillis(),
    var backdropUrl: String? = null,
    var posterUrl: String? = null
)
