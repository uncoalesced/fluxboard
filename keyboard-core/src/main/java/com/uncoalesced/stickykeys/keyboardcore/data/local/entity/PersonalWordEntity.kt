// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personal_dictionary")
data class PersonalWordEntity(
    @PrimaryKey
    val word: String,
    val frequency: Int,
    val lastUsedTimestamp: Long
)
