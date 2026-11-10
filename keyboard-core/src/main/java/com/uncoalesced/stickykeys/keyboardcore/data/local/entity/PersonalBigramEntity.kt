// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "personal_bigrams",
    primaryKeys = ["previousWord", "nextWord"],
    indices = [
        Index(value = ["previousWord"]),
        Index(value = ["lastUsedTimestamp"]),
    ],
)
data class PersonalBigramEntity(
    val previousWord: String,
    val nextWord: String,
    val count: Int,
    val lastUsedTimestamp: Long,
)
