// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.domain.model

import java.io.File

data class Sticker(
    val id: String,
    val packId: String?,
    val categoryId: String?,
    val isFavourite: Boolean,
    val createdAt: Long,
    val mimeType: String,
    val file: File,
    val thumbnailFile: File,
)

data class Category(
    val id: String,
    val name: String,
    val sortOrder: Int,
)
