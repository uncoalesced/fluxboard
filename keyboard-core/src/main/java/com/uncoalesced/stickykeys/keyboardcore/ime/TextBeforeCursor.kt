// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

// Pure reads of the text before the caret: word boundaries, sentence starts, double-space
// handling. Kept out of TypingViewModel so each can be tested without a keyboard.

/**
 * The word the caret is sitting at the end of, given the text before it.
 *
 * Pure, and separate from the ViewModel, because it is the length used to size a destructive
 * edit: the suggestion strip replaces exactly this many characters of the user's text. An
 * off-by-one here does not show up as a wrong suggestion, it shows up as a letter of the
 * previous word being eaten, so it is worth being able to assert without a keyboard.
 *
 * Letters and interior apostrophes only, matching what the keyboard's own tracker accumulates
 * (`onKeyPressed` is called for letters and nothing else). Apostrophes count because "don't"
 * and "it's" are common enough that treating them as two words would make a suggestion tap
 * replace just the "t".
 */
internal fun wordUnderCaret(textBeforeCursor: String): String =
    textBeforeCursor
        .takeLastWhile { it.isLetter() || it == '\'' }
        // A leading apostrophe is a quotation mark, not part of the word. Keeping it would
        // add one to the replacement length and swallow the quote the user typed.
        .dropWhile { it == '\'' }

/**
 * Whether the word at the end of [textBeforeCursor] opens a clause: it is the first thing in
 * the field, or the last thing before it (spaces aside) is a comma, a colon or semicolon, a
 * sentence end, an opening bracket or a line break.
 *
 * Used to tell "ill" the contraction from "ill" the adjective. "Okay, ill call" and
 * "(ill be late)" open a clause, where the adjective cannot stand; "I am ill" does not.
 */
internal fun startsClause(textBeforeCursor: String): Boolean {
    val word = wordUnderCaret(textBeforeCursor)
    val before = textBeforeCursor.dropLast(word.length).trimEnd(' ', '\t')
    return before.isEmpty() || before.last() in CLAUSE_OPENERS
}

private const val CLAUSE_OPENERS = ",;:.!?(\n"

/**
 * How many characters a word-wise backspace should remove, given the text before the caret.
 *
 * Trailing whitespace first, then the run of non-whitespace before it -- so a caret just after
 * "hello world " takes back "world " whole, and one sitting mid-word takes back the part that
 * has been typed. That is what ctrl-backspace does in every editor, and matching it is the
 * point: the gesture is new, what counts as a word should not be.
 *
 * Sized from text read at the moment of the edit rather than from `currentWord`. That mirror is
 * built by appending on each key press and is correct only while this keyboard is the sole
 * editor -- a caret tap, a paste or a host-side edit leaves it describing text that is gone, and
 * a destructive edit trusting it would delete the wrong span.
 *
 * Whitespace rather than letters decides the boundary, unlike [wordUnderCaret]. A backspace
 * swipe over "don't" or "e-mail" should take the whole thing, and over ", and" should take the
 * punctuation with it, because that is what the finger passed over.
 *
 * Pure, so the boundary walk is assertable without an InputConnection.
 */
internal fun wordDeleteLength(textBeforeCursor: String): Int {
    val trailingSpace = textBeforeCursor.takeLastWhile { it.isWhitespace() }.length
    val word =
        textBeforeCursor
            .dropLast(trailingSpace)
            .takeLastWhile { !it.isWhitespace() }
            .length
    return trailingSpace + word
}

/**
 * The finished word before the one the caret is in, or null when there is none.
 *
 * Pure and separate for the same reason [wordUnderCaret] is, though for a gentler failure: an
 * off-by-one here mis-ranks a suggestion rather than eating a character. Null is returned
 * across a sentence boundary as well as at the start of the text, because a word on the far
 * side of a full stop is not context for this one -- see the reset in `onSentenceStarted`,
 * which this has to agree with or a caret tap would resurrect context that typing had dropped.
 */
internal fun precedingWord(textBeforeCursor: String): String? {
    val beforeCurrent = textBeforeCursor.dropLast(wordUnderCaret(textBeforeCursor).length)
    val trimmed = beforeCurrent.trimEnd { it == ' ' }
    if (trimmed.isEmpty()) return null
    // Checked before any further trimming, or the terminator would be stripped as ordinary
    // punctuation and the sentence boundary would vanish along with it.
    if (trimmed.last() in SENTENCE_ENDINGS || trimmed.last() == '\n') return null
    // A comma, a bracket or a quote ends a word without ending a sentence, so the word it
    // follows is still context. wordUnderCaret stops at the first non-letter, so without this
    // "well, th" would report no previous word at all.
    val word = wordUnderCaret(trimmed.trimEnd { !it.isLetter() && it != '\'' })
    return word.ifEmpty { null }?.lowercase()
}

/**
 * Whether the caret sits where a new sentence begins.
 *
 * Same reasoning as [wordUnderCaret]: derived from the editor rather than from what the last
 * keystroke happened to set, so moving the caret into the middle of existing text does not
 * leave auto-capitalize armed.
 */
internal fun startsNewSentence(textBeforeCursor: String): Boolean {
    val trimmed = textBeforeCursor.trimEnd { it == ' ' }
    if (trimmed.isEmpty()) return true
    return trimmed.last() in SENTENCE_ENDINGS || trimmed.last() == '\n'
}

/** Marks that close a sentence, so the next letter is capitalized. */
internal const val SENTENCE_ENDINGS = ".!?"

/**
 * How close together two taps must be to count as one gesture.
 *
 * `ViewConfiguration.getDoubleTapTimeout()` is 300ms on every Android build and is what the
 * platform itself uses, so this matches every other double tap on the device. Named once here
 * because the caps-lock retap will want the same number, and two constants that mean "a double
 * tap" is exactly how they drift apart.
 */
internal const val DOUBLE_TAP_WINDOW_MS = 300L

/**
 * What a second space should replace, or null when it should just be a space.
 *
 * Pure, and that is the whole defence against the plausible-looking version of this feature.
 * The naive implementation watches for two taps inside a time window and inserts a period
 * without ever reading the text. It demonstrates perfectly and then produces ".. " after a
 * sentence that already ended, ". " at the start of an empty field, and a stray period after
 * a line break -- none of which show up until someone is actually writing something.
 *
 * [textBeforeCursor] is the text *including* the first space, as it stands when the second
 * space arrives. Returns the replacement for that single trailing space.
 */
internal fun doubleSpaceReplacement(textBeforeCursor: String): String? {
    // The first space must actually be there. If it is not, the two taps were not consecutive
    // in the text even if they were consecutive in time -- something else was committed in
    // between, and that something is not ours to overwrite.
    if (!textBeforeCursor.endsWith(" ")) return null

    val beforeSpace = textBeforeCursor.dropLast(1)
    if (beforeSpace.isEmpty()) return null

    val previous = beforeSpace.last()
    // Only ever after something a sentence can end on. A letter or a digit qualifies; a
    // closing bracket or quote does too, since "(like this) " is a real sentence ending.
    val endsAClause = previous.isLetterOrDigit() || previous in ")]}\"'"
    if (!endsAClause) return null

    return ". "
}

/**
 * How much text is read back when re-deriving the word under the caret.
 *
 * One constant, referenced by both the IME service and the typing view, because the two sides
 * must agree: the service uses it to resync the tracker and the view uses it to size a
 * replacement, and a mismatch would silently truncate long words on one path only. Small on
 * purpose -- this crosses a binder transaction, and only the current word is wanted.
 */
internal const val WORD_CONTEXT_CHARS = 48
