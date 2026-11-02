// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import com.uncoalesced.stickykeys.keyboardcore.ime.MODE_TRANSITION_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the motion rules: every transition is short, nothing bounces, and turning animations off
 * in system settings turns them off here too.
 */
class MotionTokensTest {
    @Test
    fun `durations pass through unchanged at the normal animator scale`() {
        assertEquals(180, motionDurationMs(180, animatorScale = 1f))
        assertEquals(180, motionDurationMs(180, animatorScale = 0.5f))
        assertEquals(180, motionDurationMs(180, animatorScale = 2f))
    }

    @Test
    fun `an animator scale of zero removes the transition entirely`() {
        assertEquals(0, motionDurationMs(180, animatorScale = 0f))
        assertEquals(0, motionDurationMs(PANEL_FADE_MS, animatorScale = 0f))
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(1f))
    }

    @Test
    fun `every transition stays at or under the ceiling`() {
        val all =
            listOf(
                PRESS_COLOR_ANIM_MS,
                PANEL_FADE_MS,
                CHEVRON_TURN_MS,
                DOCK_EXPAND_MS,
                SCREEN_FADE_MS,
                MODE_TRANSITION_MS,
            )
        all.forEach { assertTrue("$it ms exceeds $MAX_TRANSITION_MS", it in 1..MAX_TRANSITION_MS) }
        assertEquals(200, MAX_TRANSITION_MS)
    }

    @Test
    fun `the press spring does not overshoot`() {
        val spring = PressSpring as SpringSpec<Float>
        assertEquals(Spring.DampingRatioNoBouncy, spring.dampingRatio, 0f)
    }
}
