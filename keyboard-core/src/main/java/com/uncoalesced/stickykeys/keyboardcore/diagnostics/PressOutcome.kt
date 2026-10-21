// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

/**
 * How one press on one key ended, as the gesture code decided it.
 *
 * Recorded per press by the debug build only, to answer "the tap did not register": a press
 * that reached a key always resolves to exactly one of these, so a touch that shows up at the
 * grid with no matching outcome never reached a key at all.
 */
enum class PressOutcome {
    /** Typed the key's own character, on press or on lift. */
    COMMITTED,

    /** Became a glide that reached the decoder. */
    GLIDE,

    /** Held long enough to open the alternates strip, and a cell was picked. */
    ALTERNATE,

    /** Backspace held: character or word repeat ran. */
    REPEAT,

    /** Space bar dragged: caret scrub, or a tap the scrub handed back. */
    SCRUB,

    /** Ended without typing anything: cancelled, released off the strip, or a glide dropped. */
    NOTHING,
}

/**
 * Which kind of key was pressed, never which key.
 *
 * Coarse on purpose. Recording the letter would turn a diagnostic file into a log of what
 * was typed, which this project's diagnostics never contain.
 */
enum class KeyKind {
    LETTER,
    SPACE,
    DELETE,
    OTHER,
}

/** The [KeyKind] of a key, from the output string the layout gives it. */
fun keyKindOf(keyOutput: String): KeyKind =
    when {
        keyOutput == "SPACE" -> KeyKind.SPACE
        keyOutput == "DEL" -> KeyKind.DELETE
        keyOutput.length == 1 && keyOutput[0].isLetter() -> KeyKind.LETTER
        else -> KeyKind.OTHER
    }
