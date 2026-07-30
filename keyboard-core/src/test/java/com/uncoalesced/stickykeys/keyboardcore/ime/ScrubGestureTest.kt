// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The space-bar scrub, checked where it can be checked without a touchscreen.
 *
 * The acceleration ramp and the activation threshold are the two parts that decide whether
 * the gesture is usable, and both are pure. The pointer loop itself needs real touch events
 * and is not covered here -- that is stated rather than papered over with a test that
 * asserts the loop against a hand-rolled fake and proves only that the fake matches itself.
 */
class ScrubGestureTest {
    @Test
    fun `space bar scrubs, and no other key does`() {
        assertEquals(LongPress.Scrub, longPressFor("SPACE"))
        assertTrue(longPressFor("DEL") is LongPress.Repeat)
        assertTrue(longPressFor("q", hint = "%") is LongPress.Alternates)
        assertTrue(longPressFor("SHIFT") is LongPress.None)
    }

    @Test
    fun `a short drag costs more finger travel per character than a sustained one`() {
        // This is the acceleration, stated as the property that matters: holding the drag
        // longer must make the same finger movement walk the caret further.
        val atStart = scrubStepDp(0)
        val sustained = scrubStepDp(2000)
        assertTrue(
            "step should shrink as the drag is sustained: $atStart -> $sustained",
            sustained < atStart,
        )
    }

    @Test
    fun `short corrections stay at the unaccelerated step`() {
        // Nudging the caret a couple of characters is the common case, and it should not be
        // affected by acceleration at all or precise corrections become impossible.
        assertEquals(scrubStepDp(0), scrubStepDp(200), 0.001f)
    }

    @Test
    fun `the ramp is monotonic and bounded`() {
        val samples = (0..3000 step 100).map { scrubStepDp(it.toLong()) }
        assertTrue(
            "step must never grow back",
            samples.zipWithNext().all { (a, b) -> b <= a },
        )
        assertTrue("step must stay positive", samples.all { it > 0f })
        // Past the ramp it must settle rather than shrink to nothing, or a long drag would
        // fire a caret step per pixel.
        assertEquals(scrubStepDp(5_000), scrubStepDp(60_000), 0.001f)
    }

    @Test
    fun `the activation threshold is far enough to survive a tap`() {
        // Android's own touch slop is 8dp; a thumb rolling off a tap easily crosses that.
        // The space bar is the most-pressed key on the board, so its threshold has to be
        // clearly beyond slop or ordinary typing would start dragging the caret.
        assertTrue(
            "activation ($SCRUB_ACTIVATION_DP dp) must clear typical touch slop",
            SCRUB_ACTIVATION_DP >= 16f,
        )
        // But not so far that the gesture is hard to start on a narrow phone.
        assertTrue(SCRUB_ACTIVATION_DP <= 32f)
    }

    @Test
    fun `one step of travel is never further than the activation distance`() {
        // Activation emits the first caret step itself, so the accumulator restarts from
        // zero afterwards. If a single step cost more travel than activation did, the caret
        // would move once and then appear to stall for longer than it took to start.
        assertTrue(
            "first step ${scrubStepDp(0)} should not exceed activation $SCRUB_ACTIVATION_DP",
            scrubStepDp(0) <= SCRUB_ACTIVATION_DP,
        )
    }
}
