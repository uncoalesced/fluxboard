// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * What kind of input the keyboard is handling, as far as it affects safety and layout.
 *
 * Only the distinctions this keyboard actually acts on. A general numeric layout, phone fields
 * and date fields are deliberately absent: they are a separate piece of work and inventing
 * members for them here would imply behaviour that does not exist.
 *
 * [PRIVATE] is the one member that does **not** come from `inputType`. It is what the user's
 * manual privacy switch resolves to, and it lives here rather than in a flag of its own so it
 * travels down the gates that already exist. See [FieldKind.PRIVATE].
 */
enum class FieldKind {
    /** Ordinary text. Everything the keyboard normally does applies. */
    NORMAL,

    /** A numeric secret -- a PIN, a card CVV, a passcode. Digits only, and nothing is learned. */
    PIN,

    /** An alphanumeric secret. Full keyboard, but nothing is learned and nothing is suggested. */
    PASSWORD,

    /**
     * The user said this is private. Not detected -- asked for.
     *
     * Deliberately a member of this enum rather than a second boolean threaded beside it. The
     * password fix already put two gates in place, one on dictionary *writes* (via incognito)
     * and one on dictionary *reads* (via [isSensitive]), and a manual switch that only reached
     * the first would be a privacy control that half works -- the worse outcome, because it
     * still looks like it is on. Resolving to a [FieldKind] means it passes through both
     * without either gate growing a branch.
     *
     * Only ever upgraded from [NORMAL]: a PIN field stays a PIN field so it keeps its digit
     * grid, and a password field is already at least this strict.
     */
    PRIVATE,

    ;

    /**
     * Whether the contents of this field must never reach the personal dictionary, the
     * suggestion strip, the autocorrect engine or the clipboard history.
     */
    val isSensitive: Boolean get() = this == PIN || this == PASSWORD || this == PRIVATE

    /**
     * Whether the field itself holds a credential, as opposed to merely being private.
     *
     * Narrower than [isSensitive] and deliberately so. Both suppress learning, but only this
     * one describes a field whose contents the user may not be able to *read back* -- so only
     * this one suppresses auto-capitalize. Folding the two together would turn the manual
     * privacy switch into a switch that also silently stops capitalizing sentences, which is
     * a visible change to ordinary typing and nothing a privacy control should be doing.
     */
    val isCredential: Boolean get() = this == PIN || this == PASSWORD
}

/**
 * Whether the return key inserts a line break rather than submitting.
 *
 * Extracted from `StickyKeysIME.sendEnter`, which now calls it, so the keyboard cannot end up
 * believing one thing about a field when it decides what Enter *does* and another when it
 * decides what Enter *learns*. Those were two separate reads of the same `EditorInfo` and
 * keeping them in one function is the point.
 *
 * `IME_FLAG_NO_ENTER_ACTION` counts alongside multi-line: a host that suppresses the action is
 * telling the keyboard there is nothing to submit to, so the key falls back to a raw newline.
 */
fun enterInsertsNewline(
    inputType: Int,
    imeOptions: Int,
): Boolean {
    val multiLine =
        (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (
                inputType and (
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
                )
            ) != 0
    val actionSuppressed = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
    return multiLine || actionSuppressed
}

/**
 * Folds the user's manual privacy switch into what the field itself declared.
 *
 * Pure and separate from [fieldKindFor] so the classifier stays a function of `inputType`
 * alone -- the switch is not a property of the field, and letting it leak into that function
 * would make the tests for it lie about what the platform reported.
 */
fun effectiveFieldKind(
    detected: FieldKind,
    privateModeEnabled: Boolean,
): FieldKind =
    if (privateModeEnabled && detected == FieldKind.NORMAL) FieldKind.PRIVATE else detected

/**
 * Classifies a field from `EditorInfo.inputType`.
 *
 * **This is the fix for a real privacy defect, not a convenience.** Before it existed, nothing
 * in this codebase read the password variations at all -- `inputType` was consulted in exactly
 * one place, inside `sendEnter`, to choose between a newline and the field's action. Incognito
 * was driven solely by `IME_FLAG_NO_PERSONALIZED_LEARNING`, which is a flag the *host app* has
 * to set and which Android's own `TextView` does not set for password fields. AOSP's LatinIME
 * checks the input type itself for exactly this reason.
 *
 * The consequence was that an alphanumeric password typed into FluxBoard went through the
 * ordinary letter path: each character extended the tracked word, every keystroke ran a
 * dictionary lookup whose results were drawn in the suggestion strip, and the first space or
 * punctuation afterwards wrote the password into the personal dictionary on disk. For a
 * keyboard whose stated position is zero telemetry, a password persisted to a local database
 * the user cannot see is the same class of failure as sending it somewhere.
 *
 * Pure, and tested against the real constants, because these are exactly the values that fail
 * silently -- this file already carries the scar of `IME_ACTION_UNSPECIFIED` being 0 while
 * `IME_ACTION_NONE` is 1, which made Enter do nothing in most apps for two releases.
 */
fun fieldKindFor(inputType: Int): FieldKind {
    val cls = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION

    if (cls == InputType.TYPE_CLASS_NUMBER &&
        variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    ) {
        return FieldKind.PIN
    }

    if (cls == InputType.TYPE_CLASS_TEXT) {
        // VISIBLE_PASSWORD counts. The characters being legible on screen says nothing about
        // whether they should be learned, and it is what many apps use for a "show password"
        // toggle -- treating it as ordinary text would leak exactly the passwords belonging to
        // users who turned that on.
        when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            -> return FieldKind.PASSWORD
        }
    }

    return FieldKind.NORMAL
}
