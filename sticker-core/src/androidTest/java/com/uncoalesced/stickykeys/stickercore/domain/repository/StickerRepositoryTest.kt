// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.domain.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase
import com.uncoalesced.stickykeys.stickercore.data.repository.StickerRepositoryImpl
import com.uncoalesced.stickykeys.stickercore.domain.model.Category
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 4 DoD: insert/query/delete a sticker end-to-end (DB row + file on disk),
 * and favourites/categories queryable independently of packs.
 */
@RunWith(AndroidJUnit4::class)
class StickerRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: StickyKeysDatabase
    private lateinit var fileManager: StickerFileManager
    private lateinit var repository: StickerRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StickyKeysDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fileManager = StickerFileManager(context)
        repository = StickerRepositoryImpl(
            database.stickerDao(),
            database.packDao(),
            database.categoryDao(),
            fileManager
        )
    }

    @After
    fun teardown() {
        database.close()
        // Remove any files the real StickerFileManager wrote during the test.
        File(context.filesDir, "stickers").deleteRecursively()
        File(context.filesDir, "stickers_thumbnails").deleteRecursively()
    }

    private fun newSticker(
        id: String,
        categoryId: String? = null,
        isFavourite: Boolean = false
    ): Sticker {
        return Sticker(
            id = id,
            packId = null,
            categoryId = categoryId,
            isFavourite = isFavourite,
            createdAt = System.currentTimeMillis(),
            mimeType = "image/webp",
            file = fileManager.getStickerFile(id),
            thumbnailFile = fileManager.getThumbnailFile(id)
        )
    }

    @Test
    fun insertQueryDeleteSticker_endToEnd() = runBlocking {
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val thumbBytes = byteArrayOf(5, 6)

        repository.saveSticker(newSticker("test-1"), imageBytes, thumbBytes)

        // DB row is queryable
        val all = repository.getAllStickers().first()
        assertEquals(1, all.size)
        assertEquals("test-1", all[0].id)

        // Bytes actually landed on disk
        assertTrue(all[0].file.exists())
        assertTrue(all[0].thumbnailFile.exists())
        assertEquals(imageBytes.toList(), all[0].file.readBytes().toList())

        // Delete removes both the row and the files
        repository.deleteSticker("test-1")
        assertTrue(repository.getAllStickers().first().isEmpty())
        assertFalse(fileManager.getStickerFile("test-1").exists())
        assertFalse(fileManager.getThumbnailFile("test-1").exists())
    }

    @Test
    fun favouritesQueryableIndependently() = runBlocking {
        repository.saveSticker(newSticker("fav-1", isFavourite = true), byteArrayOf(1), byteArrayOf(1))
        repository.saveSticker(newSticker("plain-1"), byteArrayOf(2), byteArrayOf(2))

        val favourites = repository.getFavouriteStickers().first()
        assertEquals(listOf("fav-1"), favourites.map { it.id })

        // Toggling updates the favourites query without touching the other sticker
        repository.toggleFavourite("plain-1")
        val favouritesAfter = repository.getFavouriteStickers().first()
        assertEquals(setOf("fav-1", "plain-1"), favouritesAfter.map { it.id }.toSet())
    }

    @Test
    fun categoriesQueryableIndependentlyOfPacks() = runBlocking {
        val category = Category(id = "cat-1", name = "Memes", sortOrder = 0)
        repository.saveCategory(category)

        repository.saveSticker(newSticker("in-cat", categoryId = "cat-1"), byteArrayOf(1), byteArrayOf(1))
        repository.saveSticker(newSticker("no-cat"), byteArrayOf(2), byteArrayOf(2))

        val inCategory = repository.getStickersByCategory("cat-1").first()
        assertEquals(listOf("in-cat"), inCategory.map { it.id })

        // Neither sticker belongs to any pack; category query worked regardless
        assertEquals(1, repository.getAllCategories().first().size)
    }
}
