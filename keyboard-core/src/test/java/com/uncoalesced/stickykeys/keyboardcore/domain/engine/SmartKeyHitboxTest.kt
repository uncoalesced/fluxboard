// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmartKeyHitboxTest {
    @Before
    fun setUp() {
        SmartKeyHitbox.resetCentroids()
    }

    @Test
    fun `exact center hit has zero distance to itself`() {
        val touch = TouchPoint('o', 0f, 0f)
        val distSelf = SmartKeyHitbox.euclideanDistance(touch, 'o')
        assertEquals(0f, distSelf, 0.001f)

        val cost = SmartKeyHitbox.substitutionCost(touch, 'o')
        assertEquals(0, cost)
    }

    @Test
    fun `right edge hit on O leans towards P`() {
        // 'o' is at x=8, 'p' is at x=9
        // Striking 'o' with normalizedX = +0.4f puts touch at x = 8.4
        val touchRight = TouchPoint('o', 0.4f, 0f)

        val distToO = SmartKeyHitbox.euclideanDistance(touchRight, 'o')
        val distToP = SmartKeyHitbox.euclideanDistance(touchRight, 'p')

        assertEquals(0.4f, distToO, 0.001f)
        assertEquals(0.6f, distToP, 0.001f)

        // Struck close to P border -> low substitution cost
        val costToP = SmartKeyHitbox.substitutionCost(touchRight, 'p')
        assertTrue("Cost to P should be low adjacent cost", costToP <= 2)

        // Conversely, striking left edge on O (normalizedX = -0.4f) puts touch at x = 7.6, far from P
        val touchLeft = TouchPoint('o', -0.4f, 0f)
        val distLeftToP = SmartKeyHitbox.euclideanDistance(touchLeft, 'p')
        assertEquals(1.4f, distLeftToP, 0.001f)
        val costLeftToP = SmartKeyHitbox.substitutionCost(touchLeft, 'p')
        assertEquals(5, costLeftToP)
    }

    @Test
    fun `spatial cost follows gboard sweet spot formula`() {
        // Inner sweet spot: d <= 0.25 -> 0.96 * d
        val touchSweet = TouchPoint('g', 0.10f, 0f)
        val costSweet = SmartKeyHitbox.spatialCostFloat(touchSweet, 'g', isInitialChar = false)
        val expectedSweet =
            SmartKeyHitbox.BASE_PROXIMITY_INTERIOR +
                (0.96f * 0.10f) * SmartKeyHitbox.DISTANCE_WEIGHT_LENGTH
        assertEquals(expectedSweet, costSweet, 0.001f)

        // Initial char has higher base proximity cost
        val costInitial = SmartKeyHitbox.spatialCostFloat(touchSweet, 'g', isInitialChar = true)
        val expectedInitial =
            SmartKeyHitbox.BASE_PROXIMITY_INITIAL +
                (0.96f * 0.10f) * SmartKeyHitbox.DISTANCE_WEIGHT_LENGTH
        assertEquals(expectedInitial, costInitial, 0.001f)
    }

    @Test
    fun `centroid adaptation shifts effective key center`() {
        // Repeatedly typing 'p' with an offset of -0.2f (left of center)
        repeat(20) {
            SmartKeyHitbox.adaptCentroid('p', -0.2f, 0f)
        }

        val (meanX, meanY) = SmartKeyHitbox.getCentroid('p')
        assertTrue("Centroid meanX should have adapted towards -0.2f", meanX < -0.1f)
        assertEquals(0f, meanY, 0.001f)

        // Now, if a user strikes 'p' at -0.2f, after adaptation it's calibrated as a center hit (d ~ 0)
        val touch = TouchPoint('p', -0.2f, 0f)
        val dist = SmartKeyHitbox.euclideanDistance(touch, 'p')
        assertTrue("Calibrated distance should be very close to 0", dist < 0.08f)
    }
}
