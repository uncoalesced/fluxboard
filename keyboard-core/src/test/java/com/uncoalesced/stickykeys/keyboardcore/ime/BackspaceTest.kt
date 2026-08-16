// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind the backspace that replaced a raw KEYCODE_DEL key event.
 *
 * A zero-timestamp key event is the shape some host editors drop, which is what made a
 * single-letter correction take several presses. `deleteSurroundingText` deletes a count,
 * so the count has to be right: one for an ordinary character, two for the surrogate pair
 * an emoji is stored as, and none at all at the start of the field.
 */
class BackspaceTest {
    @Test
    fun `ordinary character is one`() {
        assertEquals(1, backspaceLengthFor("hello"))
    }

    @Test
    fun `empty field is zero`() {
        assertEquals(0, backspaceLengthFor(""))
    }

    @Test
    fun `emoji surrogate pair is two`() {
        // U+1F600 GRINNING FACE, built from its codepoint rather than pasted: this codebase
        // bans emoji characters in source, and the pair is the whole point of the assertion.
        val pair = String(Character.toChars(0x1F600))
        assertEquals(2, backspaceLengthFor("hi" + pair))
    }

    @Test
    fun `lone low surrogate with nothing before it is one`() {
        // Malformed/truncated input -- don't reach for a character that isn't there.
        assertEquals(1, backspaceLengthFor("\uDE00"))
    }
}
