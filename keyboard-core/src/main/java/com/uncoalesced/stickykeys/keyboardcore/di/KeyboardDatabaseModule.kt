// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalBigramDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PersonalLearningEngine
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
    fun provideKeyboardDatabase(
        @ApplicationContext context: Context,
    ): KeyboardDatabase {
        val migration1To2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `clipboard_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)",
                    )
                }
            }

        val migration2To3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `personal_bigrams` (
                            `previousWord` TEXT NOT NULL,
                            `nextWord` TEXT NOT NULL,
                            `count` INTEGER NOT NULL,
                            `lastUsedTimestamp` INTEGER NOT NULL,
                            PRIMARY KEY(`previousWord`, `nextWord`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_personal_bigrams_previousWord` ON `personal_bigrams` (`previousWord`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_personal_bigrams_lastUsedTimestamp` ON `personal_bigrams` (`lastUsedTimestamp`)",
                    )
                }
            }

        return Room
            .databaseBuilder(
                context,
                KeyboardDatabase::class.java,
                "keyboard_database",
            ).addMigrations(migration1To2, migration2To3)
            .build()
    }

    @Provides
    fun providePersonalDictionaryDao(database: KeyboardDatabase): PersonalDictionaryDao =
        database.personalDictionaryDao()

    @Provides
    fun providePersonalBigramDao(database: KeyboardDatabase): PersonalBigramDao =
        database.personalBigramDao()

    @Provides
    fun provideClipboardDao(database: KeyboardDatabase): ClipboardDao = database.clipboardDao()

    @Provides
    @Singleton
    fun providePersonalLearningEngine(
        personalDao: PersonalDictionaryDao,
        personalBigramDao: PersonalBigramDao,
        keyboardPreferences: KeyboardPreferences,
    ): PersonalLearningEngine =
        PersonalLearningEngine(
            personalDao = personalDao,
            personalBigramDao = personalBigramDao,
            keyboardPreferences = keyboardPreferences,
        )
}
