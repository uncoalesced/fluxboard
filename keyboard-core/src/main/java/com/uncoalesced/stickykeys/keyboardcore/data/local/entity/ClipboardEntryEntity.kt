// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_entries")
data class ClipboardEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val timestamp: Long
)
