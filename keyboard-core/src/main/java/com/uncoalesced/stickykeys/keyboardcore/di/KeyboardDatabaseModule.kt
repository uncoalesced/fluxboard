// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object KeyboardDatabaseModule {

    @Provides
    @Singleton
    fun provideKeyboardDatabase(@ApplicationContext context: Context): KeyboardDatabase {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `clipboard_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }

        return Room.databaseBuilder(
            context,
            KeyboardDatabase::class.java,
            "keyboard_database"
        )
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    @Provides
    fun providePersonalDictionaryDao(database: KeyboardDatabase): PersonalDictionaryDao {
        return database.personalDictionaryDao()
    }

    @Provides
    fun provideClipboardDao(database: KeyboardDatabase): ClipboardDao {
        return database.clipboardDao()
    }
}
