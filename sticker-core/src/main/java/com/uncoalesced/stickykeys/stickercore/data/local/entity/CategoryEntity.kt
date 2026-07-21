// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int
)
