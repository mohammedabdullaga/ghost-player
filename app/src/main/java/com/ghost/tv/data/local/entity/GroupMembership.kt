package com.ghost.tv.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class GroupMembership(
    @Id var id: Long = 0,
    @Index var groupId: Long = 0,
    @Index var streamId: Long = 0,
    var order: Int = 0
)
