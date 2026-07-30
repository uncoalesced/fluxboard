// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the haptic strength curve.
 *
 * It is specified exactly -- linear, capped at 80% of full scale -- so the risk is not that
 * it is wrong today but that someone later "improves" it into an easing curve because a
 * linear ramp feels unnatural on a slider. These assertions are the record that linear is
 * the intent.
 */
class HapticsCurveTest {
    @Test
    fun `zero means silent, not merely light`() {
        // The old scale coerced into 1..255, so the slider could not turn haptics off at all.
        assertEquals(0, amplitudeFor(0))
    }

    @Test
    fun `the three specified points are exact`() {
        assertEquals(0, amplitudeFor(0))
        // 50% of the slider is 40% power: 255 * 0.5 * 0.8
        assertEquals(102, amplitudeFor(50))
        // 100% of the slider is 80% power, the hard cap: 255 * 0.8
        assertEquals(204, amplitudeFor(100))
    }

    @Test
    fun `full slider never reaches the motor's full scale`() {
        assertTrue(
            "the 80% cap is the whole point; ${amplitudeFor(100)} must stay under $MAX_AMPLITUDE",
            amplitudeFor(100) < MAX_AMPLITUDE,
        )
        assertEquals(Math.round(MAX_AMPLITUDE * POWER_CAP), amplitudeFor(100))
    }

    @Test
    fun `the ramp is linear, not eased`() {
        // Equal slider steps must produce equal amplitude steps. An easing function would
        // make the first and last tenth differ; this is what catches that.
        val steps = (0..100 step 10).map { amplitudeFor(it) }
        val deltas = steps.zipWithNext { a, b -> b - a }
        val spread = deltas.max() - deltas.min()
        assertTrue(
            "amplitude steps should be uniform for a linear ramp, saw $deltas",
            spread <= 1,
        )
    }

    @Test
    fun `out of range input is clamped rather than extrapolated`() {
        // Values above 100 exist in storage from the old 1..255 amplitude scale.
        assertEquals(0, amplitudeFor(-20))
        assertEquals(amplitudeFor(100), amplitudeFor(255))
    }

    @Test
    fun `the curve is monotonic across the whole slider`() {
        val all = (0..100).map { amplitudeFor(it) }
        assertTrue(
            "raising the slider must never lower the power",
            all.zipWithNext().all { (a, b) -> b >= a },
        )
    }
}
