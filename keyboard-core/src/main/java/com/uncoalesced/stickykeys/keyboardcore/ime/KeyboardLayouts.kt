// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition

/**
 * The built-in QWERTY layout, as key definitions rather than bare strings.
 *
 * Strings alone could not carry the corner hints (`%` on `q`), the per-key weights or the
 * row indents, so the letter pages are defined here in full and the legacy
 * `List<List<String>>` form survives only for the symbol pages.
 */
object KeyboardLayouts {
    /** Output token for a gap that occupies width but is not a key. */
    const val SPACER = "SPACER"

    private fun key(
        output: String,
        hint: String? = null,
        weight: Float = 1f,
    ) = KeyDefinition(
        id = "key_${output.lowercase().replace(" ", "_")}",
        output = output,
        weight = weight,
        hint = hint,
    )

    // Keyed by name, not by weight: two half-width spacers derived their id from the weight
    // alone and so collided, which LayoutValidator rejects as duplicate IDs -- and which
    // would have made the layout editor select both ends of the home row at once.
    private fun spacer(
        name: String,
        weight: Float,
    ) = KeyDefinition(id = "spacer_$name", output = SPACER, weight = weight)

    /**
     * The optional always-visible digit row.
     *
     * Separate from the corner hints on purpose: the hints carry symbols (`%`, `@`, `_`), so
     * a user who wants digits without holding anything is not served by them at all. Kept as
     * its own row so the toggle is a list concatenation rather than a second layout.
     */
    val numberRow: List<KeyDefinition> =
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { key(it) }

    private val topRow =
        listOf(
            key("q", "%"),
            key("w", "^"),
            key("e", "~"),
            key("r", "|"),
            key("t", "["),
            key("y", "]"),
            key("u", "<"),
            key("i", ">"),
            key("o", "{"),
            key("p", "}"),
        )

    private val homeRow =
        listOf(spacer("home_left", 0.5f)) +
            listOf(
                key("a", "@"),
                key("s", "#"),
                key("d", "&"),
                key("f", "*"),
                key("g", "-"),
                key("h", "+"),
                key("j", "="),
                key("k", "("),
                key("l", ")"),
            ) +
            listOf(spacer("home_right", 0.5f))

    private val bottomLetterRow =
        listOf(key("SHIFT", weight = 1.5f)) +
            listOf(
                key("z", "_"),
                key("x", "£"),
                key("c", "\""),
                key("v", "'"),
                key("b", ":"),
                key("n", ";"),
                key("m", "/"),
            ) +
            listOf(key("DEL", weight = 1.5f))

    /**
     * The bottom row, per the reference.
     *
     * The comma key carries the mic as its hint rather than the other way round: the mic is
     * a placeholder for a feature that does not exist yet, and putting an unimplemented stub
     * on the tap target next to the space bar would cost a real keystroke every time it was
     * hit by accident.
     */
    private val actionRow =
        listOf(
            key("SYMBOLS", weight = 1.5f),
            key("STICKERS"),
            key(",", hint = "MIC"),
            key("SPACE", weight = 4f),
            key(".", hint = ",!?"),
            key("ENTER", weight = 1.5f),
        )

    /** Letter pages, with the digit row prepended when the user has asked for it. */
    fun letterRows(
        upper: Boolean,
        showNumberRow: Boolean,
    ): List<List<KeyDefinition>> {
        val letters =
            listOf(topRow, homeRow, bottomLetterRow).map { row ->
                row.map { keyDef ->
                    if (upper && keyDef.output.length == 1 && keyDef.output.first().isLetter()) {
                        keyDef.copy(output = keyDef.output.uppercase())
                    } else {
                        keyDef
                    }
                }
            }
        val body = letters + listOf(actionRow)
        return if (showNumberRow) listOf(numberRow) + body else body
    }

    // Symbol pages remain in the legacy string form; they carry no hints or indents.
    val symbolsPrimary =
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "%", "&", "-", "+", "(", ")"),
            listOf("SYMBOLS_SHIFT", "*", "\"", "'", ":", ";", "!", "?", "DEL"),
            listOf("ABC", "STICKERS", ",", "SPACE", ".", "ENTER"),
        )

    val symbolsShifted =
        listOf(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"),
            listOf("£", "¢", "€", "º", "^", "_", "=", "[", "]"),
            listOf("SYMBOLS_SHIFT", "™", "®", "©", "¶", "\\", "<", ">", "DEL"),
            listOf("ABC", "STICKERS", ",", "SPACE", ".", "ENTER"),
        )

    fun getLayoutForMode(mode: KeyboardMode): List<List<String>> =
        when (mode) {
            KeyboardMode.SYMBOLS_SHIFTED -> symbolsShifted
            else -> symbolsPrimary
        }
}
