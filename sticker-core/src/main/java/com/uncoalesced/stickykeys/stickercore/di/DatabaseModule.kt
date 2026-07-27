// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.di

import android.content.Context
import androidx.room.Room
import com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase
import com.uncoalesced.stickykeys.stickercore.data.local.dao.CategoryDao
import com.uncoalesced.stickykeys.stickercore.data.local.dao.PackDao
import com.uncoalesced.stickykeys.stickercore.data.local.dao.StickerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideStickyKeysDatabase(
        @ApplicationContext context: Context,
    ): StickyKeysDatabase =
        Room
            .databaseBuilder(
                context,
                StickyKeysDatabase::class.java,
                "stickykeys_db",
            )
            // No migrations yet for v1
            .build()

    @Provides
    fun provideStickerDao(database: StickyKeysDatabase): StickerDao = database.stickerDao()

    @Provides
    fun providePackDao(database: StickyKeysDatabase): PackDao = database.packDao()

    @Provides
    fun provideCategoryDao(database: StickyKeysDatabase): CategoryDao = database.categoryDao()
}
