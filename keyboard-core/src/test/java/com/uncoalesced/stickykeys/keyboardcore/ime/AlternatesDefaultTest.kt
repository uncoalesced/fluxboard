// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strip must commit the cell it is showing as selected.
 *
 * Found on a device, not here, and worth recording why: the gesture loop called
 * [KeyAlternatesState.moveTo] on *every* pointer event, and a release is a pointer event. It
 * carries the finger's position, which for a plain hold-and-release has never left the key --
 * so the first thing to reach moveTo overwrote [KeyAlternatesState.show]'s defaultIndex with
 * whichever cell the key happens to sit over, and a hold could only ever commit that one.
 *
 * Invisible on all ten digits, because their default is index 0 and index 0 is the cell under
 * the finger, so the right answer came out of the wrong mechanism. The currency key is the
 * only case where the two disagree, and it was wrong there: the strip drew "$" highlighted
 * and committed "€". The TalkBack path reads `options[defaultIndex]` directly, so touch and
 * screen reader produced different characters from the same key.
 *
 * These assertions pin the state machine's half of that. The slop that stops a stationary
 * finger reaching moveTo lives in the pointer loop and is verified on device.
 */
class AlternatesDefaultTest {
    private fun state(
        options: List<String>,
        defaultIndex: Int,
    ) = KeyAlternatesState().apply {
        show(options, Rect(0f, 0f, 100f, 100f), cellWidthPx = 100f, defaultIndex = defaultIndex)
    }

    @Test
    fun `a hold with no drag commits the default cell`() {
        val s = state(CURRENCY_ALTERNATES, CURRENCY_DEFAULT_INDEX)
        assertEquals("$", s.consume())
    }

    @Test
    fun `the currency default is the key's own face`() {
        // The strip reads in a conventional order rather than starting with the key's face, so
        // the default has to be named. If this ever drifts, the key types a currency the user
        // did not press.
        assertEquals("$", CURRENCY_ALTERNATES[CURRENCY_DEFAULT_INDEX])
    }

    @Test
    fun `every digit strip defaults to its superscript`() {
        // The hint in the key's corner promises the superscript, and a TalkBack long-press
        // commits options[defaultIndex]. All three have to name the same character.
        DIGIT_ALTERNATES.forEach { (digit, options) ->
            val s = state(options, 0)
            assertEquals("digit $digit", options.first(), s.consume())
            assertEquals(
                "digit $digit should lead with a superscript, not the digit itself",
                false,
                options.first() == digit,
            )
        }
    }

    @Test
    fun `dragging past the end clamps to the last cell rather than falling off`() {
        val s = state(CURRENCY_ALTERNATES, CURRENCY_DEFAULT_INDEX)
        s.moveTo(10_000f)
        assertEquals("₹", s.consume())
    }

    @Test
    fun `dragging before the start clamps to the first cell`() {
        val s = state(CURRENCY_ALTERNATES, CURRENCY_DEFAULT_INDEX)
        s.moveTo(-10_000f)
        assertEquals("€", s.consume())
    }

    @Test
    fun `an out of range default is clamped rather than crashing`() {
        assertEquals("€", state(CURRENCY_ALTERNATES, -3).consume())
        assertEquals("₹", state(CURRENCY_ALTERNATES, 99).consume())
    }
}
