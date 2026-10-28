// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

/**
 * Which symbols page the `?123` key should open on: the one the user last used.
 *
 * The page the keyboard is showing is session-scoped and resets to letters on a new field, on
 * Enter and after a space. That stays. What did not stay was the user's *choice* of page: someone
 * who lives on the second symbols plane had to press the page key again after every reset
 * (issue #30, roadmap 4L.2). This remembers the choice for the life of the IME process and
 * nothing else. It is deliberately not written to disk: a page number is no reason to add a
 * preference, a flow and a backup entry.
 */
internal class SymbolsPageMemory {
    var page: KeyboardMode = KeyboardMode.SYMBOLS
        private set

    /** Records [mode] when it is one of the two symbol planes; every other mode is ignored. */
    fun remember(mode: KeyboardMode) {
        if (mode == KeyboardMode.SYMBOLS || mode == KeyboardMode.SYMBOLS_SHIFTED) page = mode
    }
}
