// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import kotlinx.coroutines.launch

// What a key press does, kept apart from TypingKeyboardView so the composable file
// holds only drawing and gesture wiring. Nothing here is composable or remembered, so
// moving it has no effect on recomposition; KeyboardRecompositionTest still guards that.

internal fun handleKeyPress(
    keyLabel: String,
    controller: KeyboardController,
    currentMode: KeyboardMode,
    viewModel: TypingViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    setMode: (KeyboardMode) -> Unit,
) {
    if (keyLabel == KeyboardLayouts.SPACER) return
    viewModel.performKeyPressHaptic()
    when (keyLabel) {
        "SHIFT" -> {
            // Timing decides, not position in a cycle. A second tap soon after the first
            // latches caps lock; a later one turns shift back off. See nextShiftMode.
            setMode(nextShiftMode(currentMode, viewModel.consumeShiftTapGap()))
        }
        "SYMBOLS_SHIFT" -> {
            setMode(
                if (currentMode == KeyboardMode.SYMBOLS) {
                    KeyboardMode.SYMBOLS_SHIFTED
                } else {
                    KeyboardMode.SYMBOLS
                },
            )
        }
        "SYMBOLS" -> setMode(KeyboardMode.SYMBOLS)
        "ABC" -> setMode(KeyboardMode.LETTERS_LOWER)
        // One tap from either key straight into the unified picker: no menu, no intermediate
        // panel. They differ only in which tab the picker opens on.
        "EMOJI" -> controller.switchMode(AppMode.EMOJI_PICKER)
        "STICKERS" -> controller.switchMode(AppMode.STICKER_PICKER)
        "CLIPBOARD" -> controller.switchMode(AppMode.CLIPBOARD)
        "DEL" -> {
            // Read before the delete lands, the same shape and for the same reason the SPACE
            // branch below reads for doubleSpaceReplacement: onDelete has to know whether the
            // caret is landing on a sentence boundary, and the local currentWord mirror cannot
            // answer that -- it is always empty straight after punctuation, which is not the
            // same fact (roadmap 4G.7).
            //
            // One bounded read per backspace. The repeat loop in `keyGestures` calls this as
            // fast as every 22ms at full speed, so it is worth naming that `sendDelete` below
            // already performs its own `getTextBeforeCursor` on every one of those same steps
            // to size a surrogate pair -- this is a second bounded read on a path that already
            // pays one, not a new class of blocking call on the keystroke path.
            //
            // A backspace straight after a glide takes the whole word back, because one
            // gesture put it there. Anything else goes through sendDelete, which sizes
            // itself against the real text so a surrogate pair never loses half of
            // itself -- deleteBefore(1) would.
            val charCount = viewModel.onDelete(controller.textBeforeCursor(WORD_CONTEXT_CHARS))
            if (charCount > 1) controller.deleteBefore(charCount) else controller.sendDelete()
        }
        "ENTER" -> {
            // Whether Enter may finish a word depends on what Enter *is* in this field, and
            // the two cases are opposites rather than shades.
            //
            // Submit-style Enter must not learn. It deliberately does not autocorrect -- there
            // is no safe moment, since before needs a blocking lookup and after is too late in
            // a field that has already sent -- so learning anyway writes an unchecked word to
            // disk. Measured on device: two sends of "teh" put it in the dictionary at
            // frequency 2, after which autocorrect stops fixing it permanently and the
            // suggestion strip starts offering it. In a send-on-enter chat app that is the
            // ordinary typing path, so the damage is routine rather than exotic.
            //
            // Newline Enter has no send and therefore no race, so the word is genuinely
            // finished and may be learned.
            //
            // The correction is *evaluated* and never *applied*. The return key does not
            // rewrite what the user typed, in any field -- that part is settled -- but running
            // the lookup anyway is what stops Enter teaching the dictionary a misspelling. The
            // dictionary learns what the word should have been; the text on screen keeps what
            // the user actually pressed, and they can see it and fix it themselves.
            //
            // No generation token here, unlike the space and punctuation paths. Those guard a
            // *rewrite* against text having moved underneath it. Nothing is rewritten here, and
            // a word the user finished stays a word they finished however much they type next.
            if (viewModel.enterEndsAWord()) {
                val typedWord = currentWordText(controller)
                controller.sendEnter()
                viewModel.onSentenceStarted()
                if (typedWord.isNotBlank()) {
                    coroutineScope.launch {
                        val corrected = viewModel.getAutoCorrectionFor(typedWord)
                        viewModel.onWordAccepted(corrected ?: typedWord)
                    }
                }
                setMode(
                    if (viewModel.shouldAutoCapitalize.value) {
                        KeyboardMode.LETTERS_UPPER
                    } else {
                        KeyboardMode.LETTERS_LOWER
                    },
                )
                return
            }
            viewModel.onWordAbandoned()
            controller.sendEnter()
            // Enter both finishes what was typed and starts something new -- a sent message,
            // or a fresh line. Latched shift and caps lock used to survive that, so the next
            // message began in whatever case the last one ended in, and a caps lock set for
            // one word stayed on across everything after it. Symbol pages are dropped for the
            // same reason: nobody starts a new message on the symbols page on purpose.
            viewModel.onSentenceStarted()
            // Read the flag rather than leaving this to the auto-capitalize effect: that
            // effect only re-runs when its key *changes*, so if the flag was already true
            // nothing would fire and the board would sit in lower case at a sentence start.
            setMode(
                if (viewModel.shouldAutoCapitalize.value) {
                    KeyboardMode.LETTERS_UPPER
                } else {
                    KeyboardMode.LETTERS_LOWER
                },
            )
        }
        "SPACE" -> {
            // Read from the editor, not from the running buffer, and read before the space is
            // committed so the word is still the last thing before the caret. A field that
            // filtered the keystrokes out reports nothing here, which is exactly right: it
            // has no word to correct and none to learn.
            val typedWord = currentWordText(controller)

            // Two quick spaces become a full stop. Decided from the text rather than from the
            // taps alone: the pure decision reads what is actually before the caret, so it
            // cannot produce ".. " after a sentence that already ended, or a stray period at
            // the start of a field. Handled before the ordinary commit because it *replaces*
            // the space already there rather than adding to it.
            if (viewModel.consumeDoubleSpace()) {
                val replacement =
                    doubleSpaceReplacement(controller.textBeforeCursor(WORD_CONTEXT_CHARS))
                if (replacement != null) {
                    controller.replaceTextBeforeCursor(1, replacement)
                    viewModel.onSentenceStarted()
                    setMode(
                        if (viewModel.shouldAutoCapitalize.value) {
                            KeyboardMode.LETTERS_UPPER
                        } else {
                            KeyboardMode.LETTERS_LOWER
                        },
                    )
                    return
                }
            }

            // Commit the space FIRST, synchronously, so key order can never invert.
            // Waiting on the autocorrect lookup here used to let a following letter
            // commit before the space ("a b" arriving as "ab ").
            controller.commitText(" ")
            val token = viewModel.onSpacePressed()

            // A space ends a word, and the next word is almost never more symbols. Leaving the
            // board on the symbols page meant the following word was typed on the wrong plane
            // and the user had to notice and press ABC. Enter already reset the page for the
            // same reason; space did not, which made the two inconsistent as well as wrong.
            if (currentMode == KeyboardMode.SYMBOLS ||
                currentMode == KeyboardMode.SYMBOLS_SHIFTED
            ) {
                setMode(
                    if (viewModel.shouldAutoCapitalize.value) {
                        KeyboardMode.LETTERS_UPPER
                    } else {
                        KeyboardMode.LETTERS_LOWER
                    },
                )
            }

            coroutineScope.launch {
                val corrected = viewModel.getAutoCorrectionFor(typedWord)
                // Only rewrite if nothing else touched the text meanwhile -- otherwise
                // the replace would eat characters the user typed during the lookup.
                if (corrected != null && viewModel.isCurrent(token)) {
                    // Replace "<typedWord> " with "<corrected> " in one round-trip.
                    controller.replaceTextBeforeCursor(typedWord.length + 1, "$corrected ")
                    viewModel.onAutoCorrected(typedWord, corrected)
                } else {
                    viewModel.onWordAccepted(typedWord)
                }
            }
        }
        else -> {
            val isLetter = keyLabel.length == 1 && keyLabel.first().isLetter()
            if (isLetter) {
                viewModel.onKeyPressed(keyLabel)
                controller.commitText(keyLabel)
            } else {
                // Editor-derived, same as the space bar and for the same reason.
                val typedWord = currentWordText(controller)
                // Committed first and synchronously, for the same reason the space bar is:
                // waiting on the correction lookup here would let a following key land before
                // this one and invert what the user typed.
                controller.commitText(keyLabel)
                val token = viewModel.onSymbolCommitted(keyLabel)

                if (typedWord.isNotBlank() && endsAWord(keyLabel)) {
                    // Autocorrect used to fire on the space bar and nowhere else, so anyone
                    // who ends sentences with punctuation -- which is everyone -- saw it work
                    // on some words and not others with no discernible pattern.
                    coroutineScope.launch {
                        val corrected = viewModel.getAutoCorrectionFor(typedWord)
                        if (corrected != null && viewModel.isCurrent(token)) {
                            // Replace "<typed><punctuation>" in one round-trip, so the
                            // punctuation the user just saw appear never flickers.
                            controller.replaceTextBeforeCursor(
                                typedWord.length + keyLabel.length,
                                corrected + keyLabel,
                            )
                            viewModel.onAutoCorrected(typedWord, corrected)
                        } else {
                            viewModel.onWordAccepted(typedWord)
                        }
                    }
                } else {
                    viewModel.onWordAccepted(typedWord)
                }
            }

            // One-shot shift is consumed by any key -- unless the key that consumed it also
            // re-armed it, which a sentence-ending mark does. See modeAfterPrintableKey.
            modeAfterPrintableKey(currentMode, viewModel.shouldAutoCapitalize.value)
                ?.let(setMode)
        }
    }
}

/**
 * Whether committing [keyLabel] means the word before it is finished.
 *
 * Only marks that genuinely close a word. The apostrophe is excluded because it sits *inside*
 * words ("don't"), and the hyphen because a hyphenated compound is still being typed --
 * correcting on either would fire halfway through a word the user had not finished.
 */
internal fun endsAWord(keyLabel: String): Boolean =
    keyLabel.length == 1 && keyLabel[0] in WORD_TERMINATORS

/** Punctuation after which the preceding word is complete and worth checking. */
private const val WORD_TERMINATORS = ".,!?;:"

/**
 * The word under the caret, as the *editor* has it.
 *
 * The single source for anything that must be true of the text rather than of this keyboard's
 * running buffer. Two things depend on that distinction, and both were bugs before they did:
 *
 *  - **Sizing a destructive edit.** The suggestion strip deleted `currentWord.length`
 *    characters of whatever happened to be there, which is the wrong span after a caret tap
 *    or a paste.
 *  - **Learning.** A field with an input filter accepts none of what was typed and the buffer
 *    records all of it, so `qwxzj` typed into a phone field was learned from an empty field.
 *
 * One editor read per word boundary, never per keystroke -- the thing this codebase forbids
 * everywhere else.
 */
internal fun currentWordText(controller: KeyboardController): String =
    wordUnderCaret(controller.textBeforeCursor(WORD_CONTEXT_CHARS))

/**
 * How far back a word-wise delete looks for its boundary.
 *
 * Generous enough for any single word plus the whitespace before it, and bounded because this
 * is a blocking read into the host process. A word longer than this is deleted as far as the
 * window reaches, which is the same thing a longer look would do one step later.
 */
internal const val MAX_WORD_DELETE_LOOKBEHIND = 64

/** How many characters the word under the caret occupies, read from the editor. */
internal fun currentWordSpan(controller: KeyboardController): Int =
    currentWordText(controller).length
