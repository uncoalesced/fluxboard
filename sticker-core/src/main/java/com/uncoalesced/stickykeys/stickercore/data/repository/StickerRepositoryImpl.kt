// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.repository

import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.data.local.dao.CategoryDao
import com.uncoalesced.stickykeys.stickercore.data.local.dao.PackDao
import com.uncoalesced.stickykeys.stickercore.data.local.dao.StickerDao
import com.uncoalesced.stickykeys.stickercore.data.local.entity.CategoryEntity
import com.uncoalesced.stickykeys.stickercore.data.local.entity.PackEntity
import com.uncoalesced.stickykeys.stickercore.data.local.entity.StickerEntity
import com.uncoalesced.stickykeys.stickercore.domain.model.Category
import com.uncoalesced.stickykeys.stickercore.domain.model.Pack
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerRepositoryImpl @Inject constructor(
    private val stickerDao: StickerDao,
    private val packDao: PackDao,
    private val categoryDao: CategoryDao,
    private val fileManager: StickerFileManager
) : StickerRepository {

    // --- Mappers ---
    private fun StickerEntity.toDomain(): Sticker {
        return Sticker(
            id = id,
            packId = packId,
            categoryId = categoryId,
            isFavourite = isFavourite,
            createdAt = createdAt,
            mimeType = mimeType,
            file = fileManager.getStickerFile(id),
            thumbnailFile = fileManager.getThumbnailFile(id)
        )
    }

    private fun Sticker.toEntity(): StickerEntity {
        return StickerEntity(
            id = id,
            packId = packId,
            categoryId = categoryId,
            isFavourite = isFavourite,
            createdAt = createdAt,
            mimeType = mimeType
        )
    }

    private fun PackEntity.toDomain() = Pack(id, name, author, createdAt)
    private fun Pack.toEntity() = PackEntity(id, name, author, createdAt)

    private fun CategoryEntity.toDomain() = Category(id, name, sortOrder)
    private fun Category.toEntity() = CategoryEntity(id, name, sortOrder)


    // --- Stickers ---
    override fun getAllStickers(): Flow<List<Sticker>> {
        return stickerDao.getAllStickers().map { list -> list.map { it.toDomain() } }
    }

    override fun getStickersByPack(packId: String): Flow<List<Sticker>> {
        return stickerDao.getStickersByPack(packId).map { list -> list.map { it.toDomain() } }
    }

    override fun getStickersByCategory(categoryId: String): Flow<List<Sticker>> {
        return stickerDao.getStickersByCategory(categoryId).map { list -> list.map { it.toDomain() } }
    }

    override fun getFavouriteStickers(): Flow<List<Sticker>> {
        return stickerDao.getFavouriteStickers().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getStickerById(id: String): Sticker? = withContext(Dispatchers.IO) {
        return@withContext stickerDao.getStickerById(id)?.toDomain()
    }

    override suspend fun saveSticker(sticker: Sticker, bytes: ByteArray, thumbnailBytes: ByteArray) = withContext(Dispatchers.IO) {
        // Save bytes to disk first
        fileManager.saveSticker(sticker.id, bytes)
        fileManager.saveThumbnail(sticker.id, thumbnailBytes)
        // Insert into DB
        stickerDao.insertSticker(sticker.toEntity())
    }

    override suspend fun updateStickerData(sticker: Sticker, bytes: ByteArray, thumbnailBytes: ByteArray) = withContext(Dispatchers.IO) {
        fileManager.saveSticker(sticker.id, bytes)
        fileManager.saveThumbnail(sticker.id, thumbnailBytes)
        stickerDao.updateSticker(sticker.toEntity())
    }

    override suspend fun updateStickerMetadata(sticker: Sticker) = withContext(Dispatchers.IO) {
        stickerDao.updateSticker(sticker.toEntity())
    }

    override suspend fun deleteSticker(id: String) = withContext(Dispatchers.IO) {
        // Delete from disk
        fileManager.deleteFiles(id)
        // Delete from DB
        stickerDao.deleteStickerById(id)
    }

    override suspend fun toggleFavourite(id: String) = withContext(Dispatchers.IO) {
        val entity = stickerDao.getStickerById(id)
        if (entity != null) {
            stickerDao.updateSticker(entity.copy(isFavourite = !entity.isFavourite))
        }
    }


    // --- Packs ---
    override fun getAllPacks(): Flow<List<Pack>> {
        return packDao.getAllPacks().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun savePack(pack: Pack) = withContext(Dispatchers.IO) {
        packDao.insertPack(pack.toEntity())
    }

    override suspend fun deletePack(id: String) = withContext(Dispatchers.IO) {
        packDao.deletePackById(id)
    }


    // --- Categories ---
    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(category.toEntity())
    }

    override suspend fun deleteCategory(id: String) = withContext(Dispatchers.IO) {
        categoryDao.deleteCategoryById(id)
    }
}
