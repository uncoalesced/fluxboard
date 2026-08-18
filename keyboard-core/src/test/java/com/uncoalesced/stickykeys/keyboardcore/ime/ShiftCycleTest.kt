// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * One key, two meanings, decided by timing.
 *
 * The behaviour this replaces was a three-way cycle: lower, upper, caps lock, back to lower.
 * That put caps lock one tap from lower case, so it latched by accident, and escaping it took
 * a third tap -- a user who overshot could not simply press the key again to undo what they
 * had just done, they had to go the rest of the way round.
 *
 * The interesting cases are all about which side of the window a tap landed on, and that is
 * exactly what is miserable to reproduce by hand on a phone: the difference between the two
 * behaviours is a few hundred milliseconds of thumb.
 */
class ShiftCycleTest {
    private val window = CAPS_LOCK_WINDOW_MS

    @Test
    fun `the first tap arms shift`() {
        assertEquals(
            KeyboardMode.LETTERS_UPPER,
            nextShiftMode(KeyboardMode.LETTERS_LOWER, Long.MAX_VALUE),
        )
    }

    @Test
    fun `a quick second tap latches caps lock`() {
        assertEquals(
            KeyboardMode.LETTERS_CAPS_LOCK,
            nextShiftMode(KeyboardMode.LETTERS_UPPER, 200),
        )
    }

    @Test
    fun `a slow second tap turns shift back off`() {
        // By this point the user can see shift is on, so the tap is a separate decision about
        // it rather than the second half of a gesture.
        assertEquals(
            KeyboardMode.LETTERS_LOWER,
            nextShiftMode(KeyboardMode.LETTERS_UPPER, window + 1),
        )
    }

    @Test
    fun `the boundary belongs to caps lock`() {
        // A tap landing exactly on the window is the ambiguous one. It resolves to caps lock
        // because the cost of guessing wrong that way is one visible tap to undo, whereas the
        // other way silently discards a deliberate double tap.
        assertEquals(
            KeyboardMode.LETTERS_CAPS_LOCK,
            nextShiftMode(KeyboardMode.LETTERS_UPPER, window),
        )
    }

    @Test
    fun `caps lock is escapable whenever the user gets round to it`() {
        // The one case where timing must not matter at all. Anything else can strand somebody
        // in capitals because they were too slow, which is the worst outcome available here.
        listOf(0L, 200L, window, window + 1, 60_000L, Long.MAX_VALUE).forEach { gap ->
            assertEquals(
                "caps lock should release after ${gap}ms",
                KeyboardMode.LETTERS_LOWER,
                nextShiftMode(KeyboardMode.LETTERS_CAPS_LOCK, gap),
            )
        }
    }

    @Test
    fun `the first tap of a session can never read as a double`() {
        // Long.MAX_VALUE is what "there was no previous tap" looks like. Getting this wrong
        // would latch caps lock on the very first shift press in a field, which is both
        // surprising and hard to attribute to anything.
        assertEquals(
            KeyboardMode.LETTERS_UPPER,
            nextShiftMode(KeyboardMode.LETTERS_LOWER, Long.MAX_VALUE),
        )
    }

    @Test
    fun `shift from a symbols page returns to letters`() {
        listOf(
            KeyboardMode.SYMBOLS,
            KeyboardMode.SYMBOLS_SHIFTED,
            KeyboardMode.PIN,
        ).forEach { mode ->
            assertEquals(
                "shift from $mode should go to letters",
                KeyboardMode.LETTERS_LOWER,
                nextShiftMode(mode, 100),
            )
        }
    }

    @Test
    fun `a full double tap sequence reaches caps lock and back out`() {
        // The whole journey, as a user performs it. Two quick taps in, one tap out.
        var mode = KeyboardMode.LETTERS_LOWER
        mode = nextShiftMode(mode, Long.MAX_VALUE)
        assertEquals(KeyboardMode.LETTERS_UPPER, mode)
        mode = nextShiftMode(mode, 150)
        assertEquals(KeyboardMode.LETTERS_CAPS_LOCK, mode)
        mode = nextShiftMode(mode, 5_000)
        assertEquals(KeyboardMode.LETTERS_LOWER, mode)
    }

    @Test
    fun `two slow taps never reach caps lock`() {
        // The accident the old cycle made easy: tap, pause, tap, and end up latched.
        var mode = KeyboardMode.LETTERS_LOWER
        mode = nextShiftMode(mode, Long.MAX_VALUE)
        mode = nextShiftMode(mode, window * 3)
        assertEquals(KeyboardMode.LETTERS_LOWER, mode)
    }

    @Test
    fun `a letter releases one-shot shift`() {
        assertEquals(
            KeyboardMode.LETTERS_LOWER,
            modeAfterPrintableKey(KeyboardMode.LETTERS_UPPER, autoCapitalize = false),
        )
    }

    @Test
    fun `a key that re-arms auto-capitalize keeps the board in upper case`() {
        // Roadmap 4F.10. Typing the full stop of "Hello." consumes one-shot shift and re-arms
        // it in the same keystroke. Releasing unconditionally here is what left the board
        // lowercase at a sentence start: shouldAutoCapitalize was already true, so the effect
        // that would have re-armed it never re-ran.
        assertNull(modeAfterPrintableKey(KeyboardMode.LETTERS_UPPER, autoCapitalize = true))
    }

    @Test
    fun `caps lock is not consumed by typing`() {
        listOf(true, false).forEach { autoCapitalize ->
            assertNull(modeAfterPrintableKey(KeyboardMode.LETTERS_CAPS_LOCK, autoCapitalize))
        }
    }

    @Test
    fun `a board already in lower case is left alone`() {
        // Returning LETTERS_LOWER here instead of null would be a setMode on every keystroke,
        // which is a state write per letter for no change.
        assertNull(modeAfterPrintableKey(KeyboardMode.LETTERS_LOWER, autoCapitalize = false))
        assertNull(modeAfterPrintableKey(KeyboardMode.SYMBOLS, autoCapitalize = false))
    }
}
