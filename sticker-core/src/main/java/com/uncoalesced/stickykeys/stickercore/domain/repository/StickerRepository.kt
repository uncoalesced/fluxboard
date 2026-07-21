// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.domain.repository

import com.uncoalesced.stickykeys.stickercore.domain.model.Category
import com.uncoalesced.stickykeys.stickercore.domain.model.Pack
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import kotlinx.coroutines.flow.Flow
import java.io.File

interface StickerRepository {
    // Queries
    fun getAllStickers(): Flow<List<Sticker>>
    fun getStickersByPack(packId: String): Flow<List<Sticker>>
    fun getStickersByCategory(categoryId: String): Flow<List<Sticker>>
    fun getFavouriteStickers(): Flow<List<Sticker>>
    
    suspend fun getStickerById(id: String): Sticker?

    // Operations
    suspend fun saveSticker(sticker: Sticker, bytes: ByteArray, thumbnailBytes: ByteArray)
    suspend fun updateStickerData(sticker: Sticker, bytes: ByteArray, thumbnailBytes: ByteArray)
    suspend fun updateStickerMetadata(sticker: Sticker)
    suspend fun deleteSticker(id: String)
    suspend fun toggleFavourite(id: String)

    // Packs
    fun getAllPacks(): Flow<List<Pack>>
    suspend fun savePack(pack: Pack)
    suspend fun deletePack(id: String)

    // Categories
    fun getAllCategories(): Flow<List<Category>>
    suspend fun saveCategory(category: Category)
    suspend fun deleteCategory(id: String)
}
