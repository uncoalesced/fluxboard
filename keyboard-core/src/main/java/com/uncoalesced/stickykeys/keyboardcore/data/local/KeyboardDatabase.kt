// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalBigramDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.ClipboardEntryEntity
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalBigramEntity
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalWordEntity

@Database(
    entities = [
        PersonalWordEntity::class,
        ClipboardEntryEntity::class,
        PersonalBigramEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class KeyboardDatabase : RoomDatabase() {
    abstract fun personalDictionaryDao(): PersonalDictionaryDao

    abstract fun personalBigramDao(): PersonalBigramDao

    abstract fun clipboardDao(): ClipboardDao
}
