// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.uncoalesced.stickykeys.stickercore.data.local.entity.StickerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {
    @Query("SELECT * FROM stickers ORDER BY createdAt DESC")
    fun getAllStickers(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE id = :id")
    fun getStickerById(id: String): StickerEntity?

    @Query("SELECT * FROM stickers WHERE packId = :packId ORDER BY createdAt DESC")
    fun getStickersByPack(packId: String): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun getStickersByCategory(categoryId: String): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE isFavourite = 1 ORDER BY createdAt DESC")
    fun getFavouriteStickers(): Flow<List<StickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSticker(sticker: StickerEntity)

    @Update
    fun updateSticker(sticker: StickerEntity)

    @Query("DELETE FROM stickers WHERE id = :id")
    fun deleteStickerById(id: String)
}
