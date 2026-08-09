// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import com.uncoalesced.stickykeys.keyboardcore.ime.KeyboardLayouts
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
    /**
     * What holding this key offers, when it is more than the [hint] alone.
     *
     * Carried on the key rather than looked up from a table keyed on [output], and that is the
     * whole point. An output-keyed table cannot tell two keys apart that happen to type the
     * same character: `"1"` is the output of a key on both the number row and row 1 of the
     * symbols page, so a digit table keyed that way silently gave the symbols page a strip it
     * was never designed to have. The currency key has the same exposure -- `$` is also digit
     * 4's shifted symbol -- and would have collided the moment it was added.
     *
     * Null means "fall back to the shared behaviour", so keys that want the punctuation
     * alternates or a plain hint-derived hold are unaffected.
     */
    val alternates: List<String>? = null,
    /**
     * Which cell of [alternates] is selected when the strip opens, and therefore what a
     * release without any drag commits. Defaults to the first.
     */
    val alternatesDefaultIndex: Int = 0,
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
        if (alternates != null) {
            json.put("alternates", JSONArray(alternates))
            if (alternatesDefaultIndex != 0) {
                json.put("alternatesDefaultIndex", alternatesDefaultIndex)
            }
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
                alternates =
                    json.optJSONArray("alternates")?.let { array ->
                        List(array.length()) { array.getString(it) }
                    },
                alternatesDefaultIndex = json.optInt("alternatesDefaultIndex", 0),
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
    /**
     * Symbols page 1. Defaults to the shipped page, so a layout saved before this field
     * existed keeps working and keeps receiving improvements to the shipped pages.
     */
    val symbolRows: List<List<KeyDefinition>> = KeyboardLayouts.symbolsPrimaryRows,
    /** Symbols page 2, reached by the `SYMBOLS_SHIFT` key. Same defaulting as [symbolRows]. */
    val symbolShiftedRows: List<List<KeyDefinition>> = KeyboardLayouts.symbolsShiftedRows,
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("rows", rowsToJson(rows))
        // Written only when the user has actually changed them. Always writing would freeze
        // today's symbol pages into every custom layout, so a user who only reordered a couple
        // of letters would never see a shipped symbol-page fix again.
        if (symbolRows != KeyboardLayouts.symbolsPrimaryRows) {
            json.put("symbolRows", rowsToJson(symbolRows))
        }
        if (symbolShiftedRows != KeyboardLayouts.symbolsShiftedRows) {
            json.put("symbolShiftedRows", rowsToJson(symbolShiftedRows))
        }
        return json
    }

    /** Every page in this layout, so callers can validate or scan all of them uniformly. */
    fun pages(): List<List<List<KeyDefinition>>> = listOf(rows, symbolRows, symbolShiftedRows)

    companion object {
        private fun rowsToJson(rows: List<List<KeyDefinition>>): JSONArray {
            val rowsArray = JSONArray()
            for (row in rows) {
                val rowArray = JSONArray()
                for (key in row) {
                    rowArray.put(key.toJson())
                }
                rowsArray.put(rowArray)
            }
            return rowsArray
        }

        private fun rowsFromJson(rowsArray: JSONArray): List<List<KeyDefinition>> {
            val rows = mutableListOf<List<KeyDefinition>>()
            for (i in 0 until rowsArray.length()) {
                val rowArray = rowsArray.getJSONArray(i)
                val row = mutableListOf<KeyDefinition>()
                for (j in 0 until rowArray.length()) {
                    row.add(KeyDefinition.fromJson(rowArray.getJSONObject(j)))
                }
                rows.add(row)
            }
            return rows
        }

        fun fromJson(jsonStr: String): KeyboardLayoutConfig {
            val json = JSONObject(jsonStr)
            return KeyboardLayoutConfig(
                id = json.getString("id"),
                name = json.getString("name"),
                rows = rowsFromJson(json.getJSONArray("rows")),
                // Absent from every layout written before symbol pages were part of a layout
                // at all, including any a tester already has on disk. Those must load as the
                // shipped pages rather than as an empty symbols plane -- the same defaulting
                // KeyStyle.fromJson uses for themes predating per-key styling.
                symbolRows =
                    json.optJSONArray("symbolRows")?.let { rowsFromJson(it) }
                        ?: KeyboardLayouts.symbolsPrimaryRows,
                symbolShiftedRows =
                    json.optJSONArray("symbolShiftedRows")?.let { rowsFromJson(it) }
                        ?: KeyboardLayouts.symbolsShiftedRows,
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
