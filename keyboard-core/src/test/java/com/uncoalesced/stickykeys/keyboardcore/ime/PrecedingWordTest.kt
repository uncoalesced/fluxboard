// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one word of sentence context, re-derived from the editor.
 *
 * Pure JUnit for the same reason [wordUnderCaret]'s cases are: this runs whenever something
 * other than this keyboard moved the caret, and a wrong answer there is silent -- suggestions
 * are merely ranked off the wrong sentence, with nothing visibly broken to attribute it to.
 *
 * The sentence-boundary cases are the load-bearing ones. `onSentenceStarted` drops context when
 * the user types a full stop, so this has to drop it too, or tapping the caret back into a
 * paragraph would resurrect context that typing had deliberately let go.
 */
class PrecedingWordTest {
    @Test
    fun `returns the finished word before the one being typed`() {
        assertEquals("how", precedingWord("how ar"))
        assertEquals("thank", precedingWord("thank yo"))
    }

    @Test
    fun `returns the last finished word when the caret sits after a space`() {
        assertEquals("how", precedingWord("how "))
    }

    @Test
    fun `is lower-cased to match the table's keys`() {
        assertEquals("how", precedingWord("How ar"))
        assertEquals("thank", precedingWord("THANK yo"))
    }

    @Test
    fun `returns null at the start of the text`() {
        assertNull(precedingWord(""))
        assertNull(precedingWord("how"))
        assertNull(precedingWord("   "))
    }

    @Test
    fun `returns null across a sentence boundary`() {
        // A bigram spanning a full stop is two unrelated words that happened to be adjacent.
        assertNull(precedingWord("I am done. Th"))
        assertNull(precedingWord("Really? Th"))
        assertNull(precedingWord("Stop! Th"))
        assertNull(precedingWord("line one\nTh"))
    }

    @Test
    fun `skips punctuation that is not a sentence ending`() {
        // A comma ends a word but not a sentence, so the word before it is still context.
        // wordUnderCaret is letters-and-apostrophes only, so the comma is simply not part
        // of either word.
        assertEquals("well", precedingWord("well, th"))
    }

    @Test
    fun `keeps an interior apostrophe`() {
        assertEquals("don't", precedingWord("don't th"))
    }
}
