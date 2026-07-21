// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.uncoalesced.stickykeys.stickercore.data.local.dao.CategoryDao
import com.uncoalesced.stickykeys.stickercore.data.local.dao.PackDao
import com.uncoalesced.stickykeys.stickercore.data.local.dao.StickerDao
import com.uncoalesced.stickykeys.stickercore.data.local.entity.CategoryEntity
import com.uncoalesced.stickykeys.stickercore.data.local.entity.PackEntity
import com.uncoalesced.stickykeys.stickercore.data.local.entity.StickerEntity

@Database(
    entities = [StickerEntity::class, PackEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class StickyKeysDatabase : RoomDatabase() {
    abstract fun stickerDao(): StickerDao
    abstract fun packDao(): PackDao
    abstract fun categoryDao(): CategoryDao
}
