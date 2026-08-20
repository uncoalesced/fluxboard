// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
 *
 * [glideEscaped] is here too, because it is the other half of the same decision. Committing on
 * press made the glide-start test load-bearing in a way it had not been: whatever revokes the
 * letter now has to be *sure*, and a bare bounds check is not -- a fast tap that rolls a pixel
 * over its own edge looked exactly like a glide's first sample and took the character with it.
 */
class CommitOnPressTest {
    private val cornerHint = LongPress.Alternates(listOf("-"))

    /** A key somewhere in the middle of the board, in root coordinates. */
    private val key = Rect(left = 100f, top = 200f, right = 200f, bottom = 320f)
    private val escapePx = 20f

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

    @Test
    fun `a finger still on its own key is not a glide`() {
        assertFalse(glideEscaped(key, Offset(150f, 260f), escapePx))
        // Hard against the inside of an edge, which is where an off-centre tap lands.
        assertFalse(glideEscaped(key, Offset(101f, 201f), escapePx))
        assertFalse(glideEscaped(key, Offset(199f, 319f), escapePx))
    }

    @Test
    fun `a tap that strays just over its own edge is still a tap`() {
        // Roadmap 4J.1. This is the whole point of the dead zone: every one of these was read
        // as a glide starting, which revoked the letter already on screen and handed the press
        // to the decoder. On the way out that is a dropped character; when the decoder finds
        // something it is a short wrong word written over the text near the caret.
        assertFalse(glideEscaped(key, Offset(205f, 260f), escapePx))
        assertFalse(glideEscaped(key, Offset(95f, 260f), escapePx))
        assertFalse(glideEscaped(key, Offset(150f, 195f), escapePx))
        assertFalse(glideEscaped(key, Offset(150f, 330f), escapePx))
    }

    @Test
    fun `a finger that travels a real distance off the key is a glide`() {
        // Well inside the neighbouring key by now, in every direction. Erring the other way
        // would be worse than the bug: a glide that never starts is a feature gone missing.
        assertTrue(glideEscaped(key, Offset(230f, 260f), escapePx))
        assertTrue(glideEscaped(key, Offset(70f, 260f), escapePx))
        assertTrue(glideEscaped(key, Offset(150f, 170f), escapePx))
        assertTrue(glideEscaped(key, Offset(150f, 350f), escapePx))
    }

    @Test
    fun `the dead zone is a distance from the edge, not from either axis alone`() {
        // Diagonally past a corner, 15px out on each axis. Neither axis alone has crossed the
        // 20px line; the actual distance is ~21px, and it is the distance that decides.
        assertTrue(glideEscaped(key, Offset(215f, 335f), escapePx))
        // Same corner, 10px out on each: ~14px, still a tap.
        assertFalse(glideEscaped(key, Offset(210f, 330f), escapePx))
    }

    @Test
    fun `a zero dead zone is exactly the bounds test it replaced`() {
        // The degenerate case, asserted so the tolerance can be tuned to nothing without the
        // function quietly starting to answer true for a finger that never moved.
        assertFalse(glideEscaped(key, Offset(150f, 260f), escapePx = 0f))
        assertTrue(glideEscaped(key, Offset(201f, 260f), escapePx = 0f))
    }
}
