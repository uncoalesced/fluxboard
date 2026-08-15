// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hold backspace and drag left to delete whole words.
 *
 * Two pieces of arithmetic, both kept pure so neither needs a touchscreen to assert.
 *
 * The threshold is the one that carries risk. The repeat path has never had an activation
 * distance because it never needed one -- holding backspace is the most-used gesture on this
 * board, and a resting thumb drifts. Set it as low as the scrub's 18dp and ordinary drift
 * becomes a word delete, which regresses every plain backspace hold rather than only adding a
 * new gesture. `wordDeleteActivatedRequiresRealTravel` is what fails if anyone lowers it.
 */
class WordDeleteTest {
    /** The scrub's own threshold, for comparison. Word delete must sit above it. */
    private val scrubPx = SCRUB_ACTIVATION_DP
    private val activationPx = WORD_DELETE_ACTIVATION_DP

    @Test
    fun `the word-delete threshold is further than the scrub's`() {
        assertTrue(
            "word delete at $activationPx must exceed scrub at $scrubPx",
            WORD_DELETE_ACTIVATION_DP > SCRUB_ACTIVATION_DP,
        )
    }

    @Test
    fun wordDeleteActivatedRequiresRealTravel() {
        // A thumb resting on backspace, wobbling. Nothing here may become a word delete.
        assertFalse(wordDeleteActivated(travelX = -2f, travelY = 1f, activationPx = activationPx))
        assertFalse(wordDeleteActivated(travelX = -12f, travelY = 3f, activationPx = activationPx))
        // Just short of the line, still an ordinary repeat.
        assertFalse(
            wordDeleteActivated(travelX = -(activationPx - 1f), travelY = 0f, activationPx),
        )
        // Across it, with clear sideways intent.
        assertTrue(wordDeleteActivated(travelX = -activationPx, travelY = 0f, activationPx))
        assertTrue(wordDeleteActivated(travelX = -60f, travelY = 5f, activationPx = activationPx))
    }

    /** Backspace deletes backwards, so only a leftward drag has a meaning to give it. */
    @Test
    fun `a rightward drag never activates`() {
        assertFalse(wordDeleteActivated(travelX = 60f, travelY = 0f, activationPx = activationPx))
        assertFalse(wordDeleteActivated(travelX = 200f, travelY = 5f, activationPx = activationPx))
    }

    /** A swipe down off the keyboard travels far and must not take a sentence with it. */
    @Test
    fun `a mostly-vertical drag never activates`() {
        assertFalse(wordDeleteActivated(travelX = -40f, travelY = 90f, activationPx = activationPx))
        assertFalse(
            wordDeleteActivated(travelX = -40f, travelY = -90f, activationPx = activationPx),
        )
    }

    @Test
    fun `a word plus its trailing space goes together`() {
        assertEquals(6, wordDeleteLength("hello world "))
        assertEquals(5, wordDeleteLength("hello world"))
        assertEquals(6, wordDeleteLength("hello "))
    }

    /** Mid-word, only what has been typed of it goes. */
    @Test
    fun `a partial word deletes only itself`() {
        assertEquals(3, wordDeleteLength("hello wor".dropLast(3) + "wor"))
        assertEquals(3, wordDeleteLength("say wor"))
    }

    /** Whitespace decides the boundary, so punctuation inside a token travels with it. */
    @Test
    fun `punctuation inside a token is part of the word`() {
        assertEquals(5, wordDeleteLength("you don't"))
        assertEquals(6, wordDeleteLength("an e-mail"))
        // The comma belongs to the token before the space, so this swipe takes "and" only.
        assertEquals(3, wordDeleteLength("hello, and"))
        // "hello," is six characters, and the comma goes with the word it is attached to.
        assertEquals(6, wordDeleteLength("well hello,"))
    }

    /** A run of spaces goes with the word before it, not as a separate step. */
    @Test
    fun `a run of whitespace is consumed with its word`() {
        assertEquals(8, wordDeleteLength("hello world   "))
        assertEquals(3, wordDeleteLength("   "))
    }

    /** Nothing before the caret means nothing to delete, and no exception. */
    @Test
    fun `an empty field deletes nothing`() {
        assertEquals(0, wordDeleteLength(""))
    }

    /** A newline is whitespace: the swipe stops at the end of the previous line. */
    @Test
    fun `a newline terminates the word`() {
        assertEquals(6, wordDeleteLength("first\nsecond"))
        assertEquals(7, wordDeleteLength("first\nsecond\n"))
    }
}
