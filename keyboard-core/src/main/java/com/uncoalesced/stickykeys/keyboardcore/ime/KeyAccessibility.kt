// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

/**
 * Spoken labels for keys, kept separate from the glyphs drawn on them.
 *
 * The rendered label and the accessible label are different problems. "SPACE" draws as a
 * blank (`" "`), which is right visually and unusable for a screen reader -- a
 * whitespace-only label leaves the widest key on the keyboard with nothing to announce.
 * The symbol glyphs have the same defect in a quieter way: TalkBack reads "⌫", "⇧", "⏎"
 * and ":)" by Unicode name or not at all.
 *
 * So every key gets an explicit spoken label here, and the drawn glyph is hidden from the
 * semantics tree at the call site.
 */
internal fun accessibleKeyLabel(keyOutput: String): String =
    when (keyOutput) {
        "SPACE" -> "Space"
        "DEL" -> "Delete"
        "ENTER" -> "Enter"
        "SHIFT" -> "Shift"
        "SYMBOLS" -> "Symbols"
        "SYMBOLS_SHIFT" -> "More symbols"
        "ABC" -> "Letters"
        "STICKERS" -> "Stickers"
        "CLIPBOARD" -> "Clipboard"
        "\t" -> "Tab"
        else -> keyOutput
    }

/**
 * Spoken state for keys that latch, so "Shift" alone does not hide which of three
 * positions it is in. Null for keys that carry no state.
 */
internal fun accessibleKeyState(
    keyOutput: String,
    mode: KeyboardMode,
): String? =
    when (keyOutput) {
        "SHIFT" ->
            when (mode) {
                KeyboardMode.LETTERS_UPPER -> "Shift on"
                KeyboardMode.LETTERS_CAPS_LOCK -> "Caps lock on"
                else -> "Off"
            }
        "SYMBOLS_SHIFT" ->
            if (mode == KeyboardMode.SYMBOLS_SHIFTED) "Showing more symbols" else "Off"
        else -> null
    }
