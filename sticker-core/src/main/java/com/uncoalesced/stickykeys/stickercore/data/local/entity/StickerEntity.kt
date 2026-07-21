// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey val id: String,
    val packId: String?,
    val categoryId: String?,
    val isFavourite: Boolean,
    val createdAt: Long,
    val mimeType: String
)
