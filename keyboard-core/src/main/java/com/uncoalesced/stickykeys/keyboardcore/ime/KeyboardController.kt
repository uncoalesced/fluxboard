// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

interface KeyboardController {
    fun commitText(text: String)

    /**
     * Up to [maxChars] characters immediately before the cursor, as the editor actually has
     * them. Empty when there is no input connection.
     *
     * The keyboard keeps its own running copy of the word being typed, because asking the host
     * for it on every keystroke would be a blocking IPC per key. That copy is correct only
     * while this keyboard is the only thing editing the field -- the moment the user taps into
     * the middle of a word, pastes, or moves the caret, it describes text that is no longer
     * there. Anything that *deletes* on the strength of it (the suggestion strip replaces
     * `word.length` characters) will then delete the wrong span of the user's text, which is
     * worse than simply suggesting the wrong word.
     *
     * So this exists for the two cases the running copy cannot serve: re-deriving the word
     * after an edit this keyboard did not make, and sizing a replacement at the instant it is
     * applied. It must never be called per keystroke -- [maxChars] is bounded for the same
     * reason `moveCursor` bounds its scan, since an unbounded read pulls the whole field
     * across a binder transaction.
     */
    fun textBeforeCursor(maxChars: Int): String

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
