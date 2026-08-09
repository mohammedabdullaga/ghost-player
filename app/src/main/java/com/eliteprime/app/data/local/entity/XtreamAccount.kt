package com.eliteprime.app.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class XtreamAccount(
    @Id var id: Long = 0,
    var name: String = "",
    var serverUrl: String = "",
    var username: String = "",
    var password: String = "",
    var isActive: Boolean = false,
    var lastSyncedAt: Long = 0,
    var expiryDate: Long? = null,
    var maxConnections: Int? = null,
    var status: String? = null
)
