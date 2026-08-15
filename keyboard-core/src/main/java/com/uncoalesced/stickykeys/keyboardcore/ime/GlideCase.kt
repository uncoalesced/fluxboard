// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

/**
 * Gives a decoded glide the capitalization the board was set to.
 *
 * A rendering concern, deliberately kept out of the decoder. `GlideTracker.register`
 * lowercases every key it records, and it has to: the dictionary is lowercase and a path is
 * about which keys the finger crossed, not how they happened to be drawn at the time. With
 * shift armed the keys register `H`, `E`, `L` and every glide decodes to nothing -- silently,
 * with no error to attribute it to. So the decoder never sees case, and the case a user asked
 * for has to be put back here instead, the same way `PredictionEngine.matchCase` puts it back
 * on an autocorrection.
 *
 * Caps lock is stable for the whole gesture, so it can simply be read at commit time: the only
 * keys that drive a glide are letters (`isGlideCandidate` requires a single letter, which SHIFT
 * is not), so nothing can change the shift mode between the first key and the last. There is no
 * race here to defend against, and defending against one anyway would only add a way to be
 * wrong.
 */
internal fun glideCase(
    word: String,
    mode: KeyboardMode,
    atSentenceStart: Boolean,
): String =
    when {
        word.isEmpty() -> word
        // Caps lock means every letter, including the ones a glide never showed.
        mode == KeyboardMode.LETTERS_CAPS_LOCK -> word.uppercase()
        // A shift tap arms one capital, and a sentence boundary asks for the same thing --
        // a glided word at the start of a message must capitalize exactly like a typed one,
        // or auto-capitalize appears to work for typing and not for gliding.
        mode == KeyboardMode.LETTERS_UPPER || atSentenceStart ->
            word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }
