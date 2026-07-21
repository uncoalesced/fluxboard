// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.uncoalesced.stickykeys.stickercore.data.local.entity.PackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PackDao {
    @Query("SELECT * FROM packs ORDER BY createdAt DESC")
    fun getAllPacks(): Flow<List<PackEntity>>

    @Query("SELECT * FROM packs WHERE id = :id")
    fun getPackById(id: String): PackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPack(pack: PackEntity)

    @Update
    fun updatePack(pack: PackEntity)

    @Query("DELETE FROM packs WHERE id = :id")
    fun deletePackById(id: String)
}
