// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

interface KeyboardController {
    fun commitText(text: String)

    /**
     * Atomically replaces [charCount] characters immediately before the cursor with
     * [replacement], in a single InputConnection round-trip. Preferred over looping
     * [sendDelete]: one IPC instead of two per character, and the host editor cannot
     * observe a half-deleted word.
     */
    fun replaceTextBeforeCursor(
        charCount: Int,
        replacement: String,
    )

    fun sendDelete()

    fun sendEnter()

    fun handleEditorAction()

    fun switchMode(mode: AppMode)
}
