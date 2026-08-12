// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

/**
 * How many UTF-16 chars a single backspace should remove, given up to the last two
 * characters immediately before the cursor.
 *
 * Most emoji are outside the Basic Multilingual Plane and are stored as a UTF-16
 * surrogate pair. Deleting a flat 1 char would strand the leading half of the pair
 * instead of removing the whole character the user sees and pressed backspace to
 * remove. [before] is expected to be `ic.getTextBeforeCursor(2, 0)` -- bounded, like
 * every other InputConnection read in this file.
 *
 * ponytail: surrogate-pair safe, not full grapheme-cluster safe -- a ZWJ sequence
 * (e.g. a family emoji) still deletes one codepoint at a time. Upgrade to
 * BreakIterator if that shows up as its own report; nothing here depends on it yet.
 */
internal fun backspaceLengthFor(before: String): Int {
    if (before.isEmpty()) return 0
    val last = before.last()
    return if (Character.isLowSurrogate(last) &&
        before.length >= 2 &&
        Character.isHighSurrogate(before[before.length - 2])
    ) {
        2
    } else {
        1
    }
}
