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
     * Sends a raw editing key with optional modifiers, for the text-editing panel.
     *
     * Modifiers are what make one method enough for the whole panel: shift held across an
     * arrow extends the selection instead of moving the caret, and ctrl turns
     * `MOVE_HOME`/`MOVE_END` from line-wise into document-wise. Doing this with
     * `InputConnection.setSelection` instead would need the full text just to compute an
     * offset, and would still get soft-wrapped lines wrong.
     */
    fun sendEditingKey(
        keyCode: Int,
        shift: Boolean = false,
        ctrl: Boolean = false,
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
