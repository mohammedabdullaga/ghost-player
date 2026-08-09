package com.eliteprime.app.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Category(
    @Id var id: Long = 0,
    @Index var categoryId: String = "",
    var categoryName: String = "",
    var parentId: Int = 0,
    var type: String = "", // live, movie, series
    
    var isVirtual: Boolean = false,
    var isPinned: Boolean = false,
    var isHidden: Boolean = false,
    var isLocked: Boolean = false,
    var order: Int = 0
)
