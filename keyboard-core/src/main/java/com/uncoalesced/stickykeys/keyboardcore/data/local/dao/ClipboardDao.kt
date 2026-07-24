// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.ClipboardEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ClipboardEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: ClipboardEntryEntity): Long

    @Query("DELETE FROM clipboard_entries WHERE id = :id")
    fun deleteById(id: Long): Int

    @Query("DELETE FROM clipboard_entries")
    fun deleteAll(): Int
}
