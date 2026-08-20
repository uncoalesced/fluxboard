// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which keys are allowed to put their character on screen before the finger lifts.
 *
 * The gate exists because the take-back has to be exact. A letter is un-appended from the
 * word mirror and the mirror is correct again; anything routed through `onSymbolCommitted`
 * has already thrown the word away and cannot be restored, so a revoked one would leave the
 * mirror describing text that is not in the field. Everything here is that distinction, plus
 * the two gesture keys whose press has another job.
 */
class CommitOnPressTest {
    private val cornerHint = LongPress.Alternates(listOf("-"))

    @Test
    fun `a letter commits on press`() {
        assertTrue(commitsOnPress("a", cornerHint))
        assertTrue(commitsOnPress("z", cornerHint))
        // Every letter carries the corner hint as its hold behaviour, but a letter with no
        // hold at all is eligible for the same reason.
        assertTrue(commitsOnPress("q", LongPress.None))
    }

    @Test
    fun `case is not what decides it`() {
        // Shift is rendering. The key still types one reversible character either way.
        assertTrue(commitsOnPress("A", cornerHint))
    }

    @Test
    fun `digits and punctuation wait for the lift`() {
        // Not fussiness: these go through onSymbolCommitted, which clears the word mirror.
        // Revoking one would restore the character but not the word it discarded.
        assertFalse(commitsOnPress("1", LongPress.Alternates(listOf("¹"))))
        assertFalse(commitsOnPress(",", LongPress.Alternates(listOf(";"))))
        assertFalse(commitsOnPress("$", cornerHint))
    }

    @Test
    fun `the repeat and scrub keys are excluded twice over`() {
        // By their labels, and independently by their gesture -- backspace already fires on
        // press through its repeat loop, and a space bar press may still become a scrub.
        assertFalse(commitsOnPress("DEL", LongPress.Repeat))
        assertFalse(commitsOnPress("SPACE", LongPress.Scrub))
        // The two facts are independent, so a single-letter key carrying either gesture is
        // still refused rather than passing on the letter test alone.
        assertFalse(commitsOnPress("a", LongPress.Repeat))
        assertFalse(commitsOnPress("a", LongPress.Scrub))
    }

    @Test
    fun `mode keys and multi-character output are excluded`() {
        assertFalse(commitsOnPress("SHIFT", LongPress.None))
        assertFalse(commitsOnPress("ENTER", LongPress.None))
        assertFalse(commitsOnPress("ABC", LongPress.None))
        assertFalse(commitsOnPress("", LongPress.None))
        // An astral-plane character is two chars, and is not a letter this keyboard types.
        assertFalse(commitsOnPress(String(Character.toChars(0x1F600)), LongPress.None))
    }
}
