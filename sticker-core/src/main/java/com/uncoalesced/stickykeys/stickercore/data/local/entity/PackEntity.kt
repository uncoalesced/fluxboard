// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String,
    val createdAt: Long
)
