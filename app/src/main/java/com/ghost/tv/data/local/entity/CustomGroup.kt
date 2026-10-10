package com.ghost.tv.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class CustomGroup(
    @Id var id: Long = 0,
    var name: String = "",
    var groupOrder: Int = 0,
    var groupType: String = "live"
)
