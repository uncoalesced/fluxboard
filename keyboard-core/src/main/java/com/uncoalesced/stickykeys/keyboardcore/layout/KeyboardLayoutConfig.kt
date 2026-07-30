// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a single key on the keyboard with its output behavior,
 * display appearance, and relative sizing within a row.
 *
 * [hint] is the small secondary token drawn in the key's corner and reached by holding it --
 * `%` on `q`, `@` on `a`. It is a key-output token rather than free text, so it renders
 * through the same [KeyGlyph] table as a primary label and an icon hint (the mic over the
 * comma key) needs no special case. Keeping it on the key rather than in a lookup beside the
 * layout is what lets a custom layout move a letter without its hint going stale.
 */
data class KeyDefinition(
    val id: String,
    val output: String,
    val displayLabel: String? = null,
    val weight: Float = 1.0f,
    val hint: String? = null,
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("output", output)
        if (displayLabel != null) {
            json.put("displayLabel", displayLabel)
        }
        if (hint != null) {
            json.put("hint", hint)
        }
        json.put("weight", weight.toDouble())
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): KeyDefinition =
            KeyDefinition(
                id = json.getString("id"),
                output = json.getString("output"),
                displayLabel =
                    if (json.has(
                            "displayLabel",
                        )
                    ) {
                        json.getString("displayLabel")
                    } else {
                        null
                    },
                weight = json.optDouble("weight", 1.0).toFloat(),
                hint = if (json.has("hint")) json.getString("hint") else null,
            )
    }
}

/**
 * A full keyboard layout configuration: an ordered list of rows,
 * each containing an ordered list of key definitions.
 */
data class KeyboardLayoutConfig(
    val id: String,
    val name: String,
    val rows: List<List<KeyDefinition>>,
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        val rowsArray = JSONArray()
        for (row in rows) {
            val rowArray = JSONArray()
            for (key in row) {
                rowArray.put(key.toJson())
            }
            rowsArray.put(rowArray)
        }
        json.put("rows", rowsArray)
        return json
    }

    companion object {
        fun fromJson(jsonStr: String): KeyboardLayoutConfig {
            val json = JSONObject(jsonStr)
            val rowsArray = json.getJSONArray("rows")
            val rows = mutableListOf<List<KeyDefinition>>()
            for (i in 0 until rowsArray.length()) {
                val rowArray = rowsArray.getJSONArray(i)
                val row = mutableListOf<KeyDefinition>()
                for (j in 0 until rowArray.length()) {
                    row.add(KeyDefinition.fromJson(rowArray.getJSONObject(j)))
                }
                rows.add(row)
            }
            return KeyboardLayoutConfig(
                id = json.getString("id"),
                name = json.getString("name"),
                rows = rows,
            )
        }

        /** Convert a legacy List<List<String>> layout into a KeyboardLayoutConfig. */
        fun fromLegacyLayout(
            id: String,
            name: String,
            legacyRows: List<List<String>>,
        ): KeyboardLayoutConfig {
            val rows =
                legacyRows.map { row ->
                    row.map { label ->
                        val weight =
                            when (label) {
                                "SPACE" -> 4f
                                "ENTER", "SHIFT", "DEL", "SYMBOLS", "ABC",
                                "STICKERS", "SYMBOLS_SHIFT",
                                -> 1.5f
                                else -> 1f
                            }
                        KeyDefinition(
                            id = "key_${label.lowercase().replace(" ", "_")}",
                            output = label,
                            displayLabel = null,
                            weight = weight,
                        )
                    }
                }
            return KeyboardLayoutConfig(id = id, name = name, rows = rows)
        }
    }
}
