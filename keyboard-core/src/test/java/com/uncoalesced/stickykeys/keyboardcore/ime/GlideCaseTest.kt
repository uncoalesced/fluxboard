// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A glided word gets the same capitals a typed one would.
 *
 * The decoder cannot supply this and must not be changed to try. `GlideTracker.register`
 * lowercases every key it records because the dictionary is lowercase -- when it did not, a
 * glide with shift armed registered `H`, `E`, `L` and decoded to nothing at all, silently. So
 * case is applied at the point of commit, exactly where `PredictionEngine.matchCase` applies
 * it to an autocorrection.
 */
class GlideCaseTest {
    @Test
    fun `a glide at a sentence start is capitalized`() {
        assertEquals(
            "Hello",
            glideCase("hello", KeyboardMode.LETTERS_LOWER, atSentenceStart = true),
        )
    }

    @Test
    fun `an armed shift capitalizes the first letter`() {
        assertEquals(
            "Hello",
            glideCase("hello", KeyboardMode.LETTERS_UPPER, atSentenceStart = false),
        )
    }

    @Test
    fun `caps lock uppercases the whole word`() {
        assertEquals(
            "HELLO",
            glideCase("hello", KeyboardMode.LETTERS_CAPS_LOCK, atSentenceStart = false),
        )
    }

    /** Caps lock wins over the sentence rule rather than the two fighting for the first letter. */
    @Test
    fun `caps lock wins at a sentence start too`() {
        assertEquals(
            "HELLO",
            glideCase("hello", KeyboardMode.LETTERS_CAPS_LOCK, atSentenceStart = true),
        )
    }

    /** Mid-sentence with no shift, a glide is left exactly as the decoder produced it. */
    @Test
    fun `an ordinary glide mid-sentence is untouched`() {
        assertEquals(
            "hello",
            glideCase("hello", KeyboardMode.LETTERS_LOWER, atSentenceStart = false),
        )
    }

    /** The decoder can return an empty string; it must not become an exception. */
    @Test
    fun `an empty result is left alone`() {
        assertEquals("", glideCase("", KeyboardMode.LETTERS_CAPS_LOCK, atSentenceStart = true))
    }

    /** A one-letter word has no second letter to leave alone; check it anyway. */
    @Test
    fun `a single letter capitalizes cleanly`() {
        assertEquals("A", glideCase("a", KeyboardMode.LETTERS_UPPER, atSentenceStart = false))
        assertEquals("A", glideCase("a", KeyboardMode.LETTERS_CAPS_LOCK, atSentenceStart = false))
    }

    /** Only the first letter, even for a long word: this is not title case. */
    @Test
    fun `capitalizing touches only the first letter`() {
        assertEquals(
            "Keyboard",
            glideCase("keyboard", KeyboardMode.LETTERS_UPPER, atSentenceStart = false),
        )
    }
}
