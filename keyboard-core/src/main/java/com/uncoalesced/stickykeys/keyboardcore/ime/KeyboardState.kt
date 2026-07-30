// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

enum class KeyboardMode {
    LETTERS_LOWER,
    LETTERS_UPPER,
    LETTERS_CAPS_LOCK,
    SYMBOLS,
    SYMBOLS_SHIFTED,
}

enum class AppMode {
    TYPING,

    /**
     * The unified sticker + emoji picker, reached in one tap from the emoji key.
     *
     * Distinct from [STICKERS], which is Phase 16's dedicated sticker-only panel. Both
     * exist while the question of retiring the older shell is open; see MainIMEView.
     */
    EMOJI_PICKER,
    STICKERS,
    CLIPBOARD,
    TEXT_EDIT,
}
