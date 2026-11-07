// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BloomTrajectoryTest {

    @Test
    fun `uniform arc-length resampling produces exact target points with equal spacing`() {
        val rawX = floatArrayOf(0f, 1f, 2f, 5f, 10f)
        val rawY = floatArrayOf(0f, 0f, 0f, 0f, 0f)

        val trajectory = buildBloomTrajectory(rawX, rawY, count = 5, targetPoints = 48)

        assertEquals(48, trajectory.count)
        assertEquals(10f, trajectory.totalLength, 0.01f)

        // Verify start and end points match
        assertEquals(0f, trajectory.getX(0), 0.01f)
        assertEquals(0f, trajectory.getY(0), 0.01f)
        assertEquals(10f, trajectory.getX(47), 0.01f)
        assertEquals(0f, trajectory.getY(47), 0.01f)

        // Verify equal spacing along the arc
        val expectedSegmentLength = 10f / 47f
        for (i in 0 until 47) {
            val d = hypot(trajectory.getX(i + 1) - trajectory.getX(i), trajectory.getY(i + 1) - trajectory.getY(i))
            assertEquals(expectedSegmentLength.toDouble(), d.toDouble(), 0.02)
        }
    }

    @Test
    fun `L-shaped stroke detects inflection corner at 90 degree turn`() {
        val rawX = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 5f, 5f, 5f, 5f, 5f)
        val rawY = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 4f, 5f)

        val trajectory = buildBloomTrajectory(rawX, rawY, count = rawX.size, targetPoints = 48)

        assertTrue("Should detect inflection points", trajectory.inflections.isNotEmpty())

        val hasCornerNearMid = trajectory.inflections.any { idx: Int -> idx in 21..26 }
        assertTrue("Inflection must be near middle index for 90-degree corner", hasCornerNearMid)
    }

    @Test
    fun `soft Gaussian endpoint cost behaves continuously`() {
        val exactCost = gaussianEndpointCost(sampleX = 5.0f, sampleY = 5.0f, keyX = 5.0f, keyY = 5.0f)
        assertEquals(0f, exactCost, 0.001f)

        val smallDriftCost = gaussianEndpointCost(sampleX = 5.1f, sampleY = 5.0f, keyX = 5.0f, keyY = 5.0f)
        assertTrue("Small drift should have low non-zero cost", smallDriftCost in 0.01f..0.2f)

        val largeDriftCost = gaussianEndpointCost(sampleX = 6.5f, sampleY = 5.0f, keyX = 5.0f, keyY = 5.0f)
        assertTrue("Far offset must have high cost but not crash", largeDriftCost > smallDriftCost)
    }
}
