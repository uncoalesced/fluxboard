// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalDictionaryDao {

    @Query("SELECT * FROM personal_dictionary WHERE word LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    fun getSuggestionsForPrefix(prefix: String, limit: Int): List<PersonalWordEntity>

    @Query("SELECT * FROM personal_dictionary WHERE word = :word LIMIT 1")
    fun getWord(word: String): PersonalWordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(word: PersonalWordEntity)
}
