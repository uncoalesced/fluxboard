// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.uncoalesced.stickykeys.stickercore.data.local.dao.CategoryDao
import com.uncoalesced.stickykeys.stickercore.data.local.dao.StickerDao
import com.uncoalesced.stickykeys.stickercore.data.local.entity.CategoryEntity
import com.uncoalesced.stickykeys.stickercore.data.local.entity.PackEntity
import com.uncoalesced.stickykeys.stickercore.data.local.entity.StickerEntity

// PackEntity has no DAO and nothing reads it: packs were replaced by categories.
// It stays in the entity list because removing it changes the schema and needs a
// migration, and there is no exported v1 schema yet to write that migration's test
// against. StickerEntity.packId is likewise always null.
@Database(
    entities = [StickerEntity::class, PackEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StickyKeysDatabase : RoomDatabase() {
    abstract fun stickerDao(): StickerDao

    abstract fun categoryDao(): CategoryDao
}
