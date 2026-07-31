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

    /**
     * Moves the caret, or extends the selection to the new position when [extend] is set.
     *
     * Deliberately **not** `sendKeyEvent(KEYCODE_DPAD_*)`, which is what this replaces. A
     * DPAD key event is a focus-navigation event first and a caret movement second: the host
     * editor consumes it only while there is text to move through, and the moment there is
     * not -- caret already at the end, or a field that does not handle arrows -- it falls
     * through to the host window's focus search and moves focus to some other view entirely.
     * That is how a space-bar scrub could end with the text field no longer focused and an
     * unrelated part of the host app lit up, and it is not fixable by scoping the gesture:
     * the event leaves this process by design.
     *
     * Every movement here goes through the InputConnection instead, which cannot address
     * anything outside the editor it belongs to.
     */
    fun moveCursor(
        move: CursorMove,
        extend: Boolean = false,
    )

    /**
     * Performs a host-side edit action such as `android.R.id.selectAll`, `copy` or `paste`.
     *
     * Delegated to the editor rather than reimplemented through the clipboard so that the
     * host app's own behaviour applies -- undo stacks, rich text, and fields that override
     * paste all keep working.
     */
    fun performEditAction(actionId: Int)

    /**
     * Opens the system input-method picker.
     *
     * Deliberately the picker rather than `switchToNextInputMethod`: silently cycling leaves
     * the user on whatever keyboard happened to be next with no indication of what changed,
     * and no way back if the next one is one they cannot read.
     */
    fun showInputMethodPicker()
}
