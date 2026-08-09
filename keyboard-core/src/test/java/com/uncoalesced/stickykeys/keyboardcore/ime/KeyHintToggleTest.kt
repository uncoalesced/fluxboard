// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hiding the corner symbols must not change what any key types.
 *
 * The obvious implementation of this setting is to stop passing the hint down, and it is
 * wrong in a way that would not show up in a screenshot. `longPressFor` falls back to the hint
 * for keys that carry no alternates of their own -- which is every letter on the board -- so
 * withholding it would take away long-press access to the whole punctuation set. A user who
 * turned off a *visual* setting would silently lose a way of typing, and the keyboard would
 * look exactly as intended while doing it.
 *
 * So the toggle governs drawing only, and this pins the half that a rendering change cannot
 * be trusted to preserve.
 */
class KeyHintToggleTest {
    // The shipped letter plane, built the same way the keyboard builds it.
    private fun lettersWithHints() =
        KeyboardLayouts
            .letterRows(upper = false, showNumberRow = false)
            .flatten()
            .filter { it.hint != null && it.output.length == 1 && it.output.first().isLetter() }

    @Test
    fun `the shipped layout actually has corner hints to hide`() {
        // Guards the rest of this file: every assertion below is vacuous if the layout stopped
        // carrying hints, and a vacuously passing privacy-adjacent test is worse than none.
        assertTrue("expected the shipped letters to carry hints", lettersWithHints().size >= 20)
    }

    @Test
    fun `every hinted letter still long-presses to its symbol`() {
        // The behaviour the toggle must leave alone. Computed from the hint exactly as the key
        // does, with no reference to whether it is drawn -- because the drawing decision is
        // made in the composable and this is what must remain true on both sides of it.
        lettersWithHints().forEach { key ->
            val press =
                longPressFor(key.output, key.hint, key.alternates, key.alternatesDefaultIndex)
            assertTrue(
                "${key.output} should offer its hint '${key.hint}' on hold",
                press is LongPress.Alternates,
            )
            assertEquals(
                "${key.output} should type '${key.hint}' when held",
                key.hint,
                (press as LongPress.Alternates).options[press.defaultIndex],
            )
        }
    }

    @Test
    fun `the setting defaults to showing them`() {
        // A board that hides its symbols by default looks cleaner and types worse: the corner
        // hints are how the punctuation on this keyboard is discovered at all.
        assertTrue(ImePanelMetrics().showKeyHints)
    }

    @Test
    fun `hiding hints does not disturb keys that never had one`() {
        // Punctuation carries alternates from PUNCTUATION_ALTERNATES rather than from a hint,
        // so it is reached through a different branch and must be unaffected either way.
        val comma = longPressFor(",", null, null, 0)
        assertTrue(comma is LongPress.Alternates)
        val period = longPressFor(".", null, null, 0)
        assertTrue(period is LongPress.Alternates)
    }

    @Test
    fun `special keys keep their own gestures regardless of hints`() {
        assertTrue(longPressFor("SPACE", null, null, 0) is LongPress.Scrub)
        assertTrue(longPressFor("DEL", null, null, 0) is LongPress.Repeat)
    }
}
