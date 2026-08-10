// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Caret arithmetic for the movement that replaced DPAD key events.
 *
 * The boundary cases carry the whole point of the change. An arrow key event that the editor
 * could not consume -- caret already at an end -- fell through to the host window's focus
 * search and moved focus out of the text field, which is what made a space-bar scrub exit the
 * field on release and what let scrolling in the keyboard trigger unrelated host UI. So "there
 * is nowhere to go" returning null, rather than a clamped position that still issues a
 * setSelection, is the assertion that matters most here.
 */
class CursorMoveTest {
    /** Splits a string on a `|` marking the caret, the way the InputConnection sees it. */
    private fun at(marked: String): Triple<String, String, Int> {
        val caret = marked.indexOf('|')
        val text = marked.replace("|", "")
        return Triple(text.substring(0, caret), text.substring(caret), caret)
    }

    private fun move(
        marked: String,
        direction: CursorMove,
    ): Int? {
        val (before, after, caret) = at(marked)
        return cursorTargetFor(before, after, caret, direction)
    }

    @Test
    fun `left and right step one character`() {
        assertEquals(3, move("hell|o", CursorMove.LEFT))
        assertEquals(6, move("hello| there", CursorMove.RIGHT))
    }

    @Test
    fun `there is nowhere to go past either end`() {
        // The case that used to leak a key event into the host's focus tree. Returning null
        // means no setSelection is issued at all, which is also what keeps a scrub held
        // against the end of the text from spamming the editor.
        assertNull(move("|hello", CursorMove.LEFT))
        assertNull(move("hello|", CursorMove.RIGHT))
        assertNull(move("|hello", CursorMove.DOC_START))
        assertNull(move("hello|", CursorMove.DOC_END))
    }

    @Test
    fun `document ends are absolute`() {
        assertEquals(0, move("hel|lo", CursorMove.DOC_START))
        assertEquals(5, move("hel|lo", CursorMove.DOC_END))
    }

    @Test
    fun `vertical movement keeps the column`() {
        // "abcdef\nghijkl", caret at column 3 of the second line.
        assertEquals(3, move("abcdef\nghi|jkl", CursorMove.UP))
        assertEquals(10, move("abc|def\nghijkl", CursorMove.DOWN))
    }

    @Test
    fun `vertical movement clamps to a shorter neighbouring line`() {
        // Moving up from deep in a long line onto a short one has to land at that line's end,
        // not past it -- a position past the end is where an off-by-one becomes a crash in
        // the host editor rather than a visible mistake here.
        assertEquals(2, move("ab\nlongerline|", CursorMove.UP))
        assertEquals(13, move("longerline|\nab", CursorMove.DOWN))
    }

    @Test
    fun `vertical movement stops at the first and last line`() {
        // Both of these were the arrow key that escaped: a DOWN on the last line is precisely
        // the event the editor declines and the focus search then claims.
        assertNull(move("abc|def", CursorMove.UP))
        assertNull(move("abc|def", CursorMove.DOWN))
        assertNull(move("ab|c\ndef", CursorMove.UP))
        assertNull(move("abc\nde|f", CursorMove.DOWN))
    }

    @Test
    fun `moving up onto an empty line lands on it rather than skipping it`() {
        assertEquals(4, move("abc\n\nde|f", CursorMove.UP))
    }

    @Test
    fun `a caret at a line start moves up to the previous line start`() {
        assertEquals(0, move("abc\n|def", CursorMove.UP))
    }
}
