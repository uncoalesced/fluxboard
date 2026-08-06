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
        alternates: List<String>? = null,
        alternatesDefaultIndex: Int = 0,
    ) = KeyDefinition(
        id = "key_${output.lowercase().replace(" ", "_")}",
        output = output,
        weight = weight,
        hint = hint,
        alternates = alternates,
        alternatesDefaultIndex = alternatesDefaultIndex,
    )

    // Keyed by name, not by weight: two half-width spacers derived their id from the weight
    // alone and so collided, which LayoutValidator rejects as duplicate IDs -- and which
    // would have made the layout editor select both ends of the home row at once.
    private fun spacer(
        name: String,
        weight: Float,
    ) = KeyDefinition(id = "spacer_$name", output = SPACER, weight = weight)

    /**
     * Each digit paired with the symbol shift produces from it, in row order.
     *
     * One table, used three ways: the corner hint on the unshifted key, the key itself while
     * shift is armed, and the first entry of the digit's long-press strip. They have to agree
     * -- a hint that advertises one character while the hold produces another is the exact
     * thing `KeyGestures` warns about -- so they come from here rather than from three lists
     * that would drift.
     */
    val digitShiftPairs: List<Pair<String, String>> =
        listOf(
            "1" to "!",
            "2" to "@",
            "3" to "#",
            "4" to "$",
            "5" to "%",
            "6" to "^",
            "7" to "&",
            "8" to "*",
            "9" to "(",
            "0" to ")",
        )

    /**
     * The optional always-visible digit row.
     *
     * Separate from the letter keys' corner hints on purpose: those carry symbols (`%`, `@`,
     * `_`), so a user who wants digits without holding anything is not served by them at all.
     * Kept as its own row so the toggle is a list concatenation rather than a second layout.
     *
     * The hint shows the shifted symbol, which is what arming shift will produce. It is
     * **not** what a hold commits: the hold strip is superscripts and fractions, and the
     * shifted symbol was removed from it because the same character is one shift-tap away and
     * the cell was pushing the strip's actual content along the row.
     *
     * The strip is attached to each key rather than looked up by output, so the digits on the
     * symbols page -- which type the same characters -- do not inherit it.
     */
    val numberRow: List<KeyDefinition> =
        digitShiftPairs.map { (digit, shifted) ->
            key(digit, hint = shifted, alternates = DIGIT_ALTERNATES[digit])
        }

    /**
     * The digit row while shift is armed.
     *
     * Substituted for [numberRow] rather than transformed in place, so the shifted symbols are
     * real keys with their own ids rather than a rendering trick. One-shot shift is consumed by
     * pressing one of them exactly as it is by any other non-special key, with no extra code.
     */
    val shiftedNumberRow: List<KeyDefinition> =
        digitShiftPairs.map { (_, shifted) -> key(shifted) }

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
            // No mic hint. It advertised voice input that this key never produced --
            // longPressFor consults PUNCTUATION_ALTERNATES before the hint, and "," is in
            // that map, so holding it has always given ". ? !" and never the mic. Removing
            // the glyph leaves the behaviour untouched and stops the key promising something
            // it does not do; the key picks up the small corner dot that marks a hold instead.
            key(","),
            key("SPACE", weight = 4f),
            key(".", hint = ",!?"),
            key("ENTER", weight = 1.5f),
        )

    /**
     * The digits-only pad for a numeric secret.
     *
     * Deliberately bare. No fraction strips, no corner hints, no letters, no symbols page --
     * on a PIN every one of those is either input the field will reject or a route to
     * something that should not be near a secret. The blank cell keeps `0` centred under `8`
     * so the pad reads like every other PIN pad rather than shifting the digits left.
     *
     * No `alternates` anywhere on it, which matters: these keys type the same characters as the
     * number row, and an output-keyed table would have given a PIN pad the fraction strips.
     */
    val pinRows: List<List<KeyDefinition>> =
        listOf(
            listOf("1", "2", "3").map { key(it) },
            listOf("4", "5", "6").map { key(it) },
            listOf("7", "8", "9").map { key(it) },
            listOf(spacer("pin_blank", 1f), key("0"), key("DEL")),
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

    /**
     * The action row shared by both symbol pages.
     *
     * One deliberate deviation from the reference: it draws no emoji key here, and this keeps
     * one. Following the reference would mean a user on the symbols page has to return to the
     * letters before they can reach emoji at all, which works against the direct-emoji-access
     * goal the backlog is built around. Confirmed as a deviation rather than an oversight.
     */
    private val symbolActionRow =
        listOf(
            key("ABC", weight = 1.5f),
            key("STICKERS"),
            key(","),
            key("SPACE", weight = 4f),
            key("."),
            key("ENTER", weight = 1.5f),
        )

    /**
     * Symbols page 1, from the supplied reference.
     *
     * Three things here are the reference's choices rather than carried over from what this
     * page used to hold, and each is a deliberate difference worth knowing about:
     *
     * - `/` is present, on row 3. It previously existed nowhere on either symbol page and was
     *   reachable only by holding the `m` key, which is why it read as missing entirely.
     * - `$` is *not* here; the reference puts `£` in that slot and reaches `$` through the
     *   shifted number row instead.
     * - the emoji key is absent from this page, where the letters page keeps it.
     *
     * No corner hints: the reference draws none on this page.
     */
    val symbolsPrimaryRows: List<List<KeyDefinition>> =
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { key(it) },
            listOf(
                key("@"),
                key("#"),
                // The reference draws a bare currency slot here. It carries the dollar sign and
                // reaches the others by hold, rather than committing the page to one currency:
                // the alternates and their default cell live on this key alone, so nothing
                // about it touches digit 4 on the number row, which types the same character.
                key(
                    "$",
                    alternates = CURRENCY_ALTERNATES,
                    alternatesDefaultIndex = CURRENCY_DEFAULT_INDEX,
                ),
                key("&"),
                key("_"),
                key("-"),
                key("("),
                key(")"),
                key("="),
                key("%"),
            ),
            listOf(key("SYMBOLS_SHIFT", weight = 1.5f)) +
                listOf("\"", "*", "'", ":", "/", "!", "?", "+").map { key(it) } +
                listOf(key("DEL", weight = 1.5f)),
            symbolActionRow,
        )

    /**
     * Symbols page 2, carried over unchanged.
     *
     * No reference has ever been supplied for this page, so it is migrated to the key-definition
     * form verbatim rather than redesigned. `CLAUDE.md` records the standing instruction not to
     * invent a layout for the symbol pages; page 1 above is now covered by a reference and this
     * one still is not.
     */
    val symbolsShiftedRows: List<List<KeyDefinition>> =
        listOf(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}")
                .map { key(it) },
            listOf("£", "¢", "€", "º", "^", "_", "=", "[", "]").map { key(it) },
            listOf(key("SYMBOLS_SHIFT", weight = 1.5f)) +
                listOf("™", "®", "©", "¶", "\\", "<", ">").map { key(it) } +
                listOf(key("DEL", weight = 1.5f)),
            symbolActionRow,
        )

    fun symbolRowsForMode(
        mode: KeyboardMode,
        config: com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig,
    ): List<List<KeyDefinition>> =
        when (mode) {
            KeyboardMode.SYMBOLS_SHIFTED -> config.symbolShiftedRows
            else -> config.symbolRows
        }
}
