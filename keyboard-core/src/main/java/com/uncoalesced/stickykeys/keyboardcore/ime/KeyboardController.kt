// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

interface KeyboardController {
    fun commitText(text: String)
    fun sendDelete()
    fun sendEnter()
    fun handleEditorAction()
    fun switchMode(mode: AppMode)
}
