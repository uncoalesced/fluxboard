// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalBigramEntity

@Dao
interface PersonalBigramDao {
    @Query(
        "SELECT * FROM personal_bigrams WHERE previousWord = :previousWord ORDER BY count DESC LIMIT :limit",
    )
    fun getFollowers(
        previousWord: String,
        limit: Int,
    ): List<PersonalBigramEntity>

    @Query(
        "SELECT * FROM personal_bigrams ORDER BY lastUsedTimestamp DESC LIMIT :limit",
    )
    fun getAllRecent(limit: Int): List<PersonalBigramEntity>

    @Query(
        "SELECT * FROM personal_bigrams WHERE previousWord = :prev AND nextWord = :next LIMIT 1",
    )
    fun getBigram(
        prev: String,
        next: String,
    ): PersonalBigramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBigram(bigram: PersonalBigramEntity)

    @Query(
        "UPDATE personal_bigrams SET count = count + 1, lastUsedTimestamp = :timestamp WHERE previousWord = :prev AND nextWord = :next",
    )
    fun incrementBigram(
        prev: String,
        next: String,
        timestamp: Long,
    ): Int

    @Query(
        "DELETE FROM personal_bigrams WHERE previousWord = :prev AND nextWord = :next",
    )
    fun deleteBigram(
        prev: String,
        next: String,
    ): Int

    @Query("DELETE FROM personal_bigrams WHERE lastUsedTimestamp < :cutoffTimestamp")
    fun pruneOldEntries(cutoffTimestamp: Long): Int

    @Query("DELETE FROM personal_bigrams")
    fun clearAll(): Int
}
