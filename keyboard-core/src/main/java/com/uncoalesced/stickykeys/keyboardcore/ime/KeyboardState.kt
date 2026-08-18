// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

enum class KeyboardMode {
    LETTERS_LOWER,
    LETTERS_UPPER,
    LETTERS_CAPS_LOCK,
    SYMBOLS,
    SYMBOLS_SHIFTED,

    /**
     * The digits-only pad shown on a numeric secret -- a PIN, a passcode, a card CVV.
     *
     * Entered from the field's own input type rather than from a key, and there is no key on
     * it that leaves: a field declaring `TYPE_NUMBER_VARIATION_PASSWORD` accepts nothing but
     * digits, so an ABC key would only offer the user a way to type characters the field will
     * refuse. The way out is the host's own focus change.
     */
    PIN,
}

enum class AppMode {
    TYPING,

    /**
     * The unified sticker and emoji picker, reached in one tap from the emoji key.
     *
     * There used to be a second, sticker-only panel beside this one from Phase 16. Nothing
     * routed to it after the emoji key and the quick-access grid were both pointed here --
     * this is a superset of what it showed -- so it was retired rather than left as a mode
     * that could only be reached by editing code.
     */
    EMOJI_PICKER,

    /**
     * The same picker, opened on the stickers tab instead of on emoji.
     *
     * A mode rather than an argument to [EMOJI_PICKER] because the landing tab has to survive
     * the trip through `KeyboardController.switchMode`, and widening that signature would mean
     * editing all five implementors for a value only one of them reads. It renders the same
     * view -- this is not the retired Phase 16 sticker panel coming back.
     */
    STICKER_PICKER,
    CLIPBOARD,
    TEXT_EDIT,
}
