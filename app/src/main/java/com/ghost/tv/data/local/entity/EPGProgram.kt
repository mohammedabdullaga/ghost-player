package com.ghost.tv.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class EPGProgram(
    @Id var id: Long = 0,
    @Index var epgId: String = "",
    var title: String = "",
    var description: String? = null,
    @Index var startTimestamp: Long = 0,
    @Index var stopTimestamp: Long = 0,
    var channelId: Long = 0 // Reference to LiveStream.streamId
)
