// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Two quick spaces become a full stop -- but only where one belongs.
 *
 * The plausible version of this feature watches for two taps inside a time window and inserts
 * a period without reading the text at all. It demonstrates perfectly, and then produces ".. "
 * after a sentence that already ended, ". " at the start of an empty field, and a stray period
 * after a line break. None of those show up until someone is writing something real, which is
 * exactly why the decision is a pure function with the negative cases pinned rather than a
 * timer and an insert.
 */
class DoubleSpaceTest {
    @Test
    fun `a space after a word becomes a full stop`() {
        assertEquals(". ", doubleSpaceReplacement("hello "))
        assertEquals(". ", doubleSpaceReplacement("a sentence like this "))
    }

    @Test
    fun `a space after a digit counts too`() {
        // "meet me at 5 " is a sentence ending as much as any other.
        assertEquals(". ", doubleSpaceReplacement("meet me at 5 "))
    }

    @Test
    fun `a closing bracket or quote still ends a sentence`() {
        assertEquals(". ", doubleSpaceReplacement("(like this) "))
        assertEquals(". ", doubleSpaceReplacement("he said \"no\" "))
    }

    @Test
    fun `nothing happens at the start of an empty field`() {
        // The naive version puts a full stop as the first character of the message.
        assertNull(doubleSpaceReplacement(""))
        assertNull(doubleSpaceReplacement(" "))
    }

    @Test
    fun `nothing happens after a sentence that already ended`() {
        // The ".. " case. This is the one a user notices immediately and cannot undo cleanly.
        assertNull(doubleSpaceReplacement("done. "))
        assertNull(doubleSpaceReplacement("really? "))
        assertNull(doubleSpaceReplacement("stop! "))
    }

    @Test
    fun `nothing happens after a line break`() {
        assertNull(doubleSpaceReplacement("first line\n"))
    }

    @Test
    fun `nothing happens when the preceding space is not actually there`() {
        // Time is not enough on its own. Two taps can be close together while something else
        // was committed between them, and that something is not ours to overwrite.
        assertNull(doubleSpaceReplacement("hello"))
    }

    @Test
    fun `nothing happens after other punctuation`() {
        // A comma or a colon is mid-sentence. Turning "one, " into "one. " would rewrite the
        // user's grammar rather than complete it.
        assertNull(doubleSpaceReplacement("one, "))
        assertNull(doubleSpaceReplacement("as follows: "))
        assertNull(doubleSpaceReplacement("a dash - "))
    }
}
