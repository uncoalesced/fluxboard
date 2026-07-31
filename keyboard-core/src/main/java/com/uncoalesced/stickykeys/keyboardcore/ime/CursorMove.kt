// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

/**
 * A caret movement, expressed as intent rather than as a key code.
 *
 * The whole point of naming these is that they never become `KeyEvent`s. A `KEYCODE_DPAD_*`
 * sent through the InputConnection is a *focus navigation* event that the editor happens to
 * interpret as caret movement while it has somewhere to move; at either end of the text, or in
 * a field that does not handle arrows, the host window's focus search claims it instead and
 * moves focus out of the text field. Every case here is served by `setSelection` on the
 * InputConnection, which cannot address anything outside the editor.
 */
enum class CursorMove {
    /** One character left. */
    LEFT,

    /** One character right. */
    RIGHT,

    /** One line up, keeping the column where possible. */
    UP,

    /** One line down, keeping the column where possible. */
    DOWN,

    /** To the very start of the field. */
    DOC_START,

    /** To the very end of the field. */
    DOC_END,
}

/**
 * Where the caret should land, given what surrounds it.
 *
 * Split out of the IME as a pure function so the arithmetic is testable. It is easy to get
 * subtly wrong -- off-by-one at a line boundary, or a vertical move that silently clamps to
 * the same position and looks like a dead button -- and it is the replacement for the DPAD key
 * events that were escaping into the host app's focus tree, so it has to be right in exactly
 * the boundary cases where those events used to leak.
 *
 * [before] and [after] are the text either side of [caret], as far as was read. Returns null
 * when there is nowhere to go, so the caller can skip a redundant `setSelection` rather than
 * issuing one per step of a scrub that has run off the end.
 *
 * ponytail: line boundaries are hard newlines only. Visual (soft-wrapped) rows depend on the
 * host's own measured layout and are not knowable through an InputConnection at all.
 */
internal fun cursorTargetFor(
    before: String,
    after: String,
    caret: Int,
    move: CursorMove,
): Int? {
    val column = before.length - (before.lastIndexOf('\n') + 1)
    val toLineEnd = after.indexOf('\n').let { if (it < 0) after.length else it }

    return when (move) {
        CursorMove.LEFT -> if (before.isEmpty()) null else caret - 1
        CursorMove.RIGHT -> if (after.isEmpty()) null else caret + 1
        CursorMove.DOC_START -> if (caret == 0) null else 0
        CursorMove.DOC_END -> if (after.isEmpty()) null else caret + after.length
        CursorMove.UP -> {
            // No newline behind the caret means this is already the first line.
            val lineStart = before.length - column
            if (lineStart <= 0) {
                null
            } else {
                val previousStart = before.lastIndexOf('\n', lineStart - 2) + 1
                val previousLength = lineStart - 1 - previousStart
                caret - column - 1 - (previousLength - minOf(column, previousLength))
            }
        }
        CursorMove.DOWN -> {
            if (toLineEnd >= after.length) {
                null // no newline ahead: already on the last line
            } else {
                val nextStart = toLineEnd + 1
                val nextEnd =
                    after.indexOf('\n', nextStart).let { if (it < 0) after.length else it }
                caret + nextStart + minOf(column, nextEnd - nextStart)
            }
        }
    }
}
