// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.uncoalesced.stickykeys.stickercore.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun getCategoryById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategory(category: CategoryEntity)

    @Update
    fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    fun deleteCategoryById(id: String)
}
