// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomTrajectoryAlignerTest {

    // Simple QWERTY-like centroid map for testing (coordinates in key width units)
    private val qwertyCentroids: Map<Char, Pair<Float, Float>> = mapOf(
        'q' to Pair(0.5f, 0.5f), 'w' to Pair(1.5f, 0.5f), 'e' to Pair(2.5f, 0.5f),
        'r' to Pair(3.5f, 0.5f), 't' to Pair(4.5f, 0.5f), 'y' to Pair(5.5f, 0.5f),
        'u' to Pair(6.5f, 0.5f), 'i' to Pair(7.5f, 0.5f), 'o' to Pair(8.5f, 0.5f), 'p' to Pair(9.5f, 0.5f),
        'a' to Pair(0.8f, 1.5f), 's' to Pair(1.8f, 1.5f), 'd' to Pair(2.8f, 1.5f),
        'f' to Pair(3.8f, 1.5f), 'g' to Pair(4.8f, 1.5f), 'h' to Pair(5.8f, 1.5f),
        'j' to Pair(6.8f, 1.5f), 'k' to Pair(7.8f, 1.5f), 'l' to Pair(8.8f, 1.5f),
        'z' to Pair(1.2f, 2.5f), 'x' to Pair(2.2f, 2.5f), 'c' to Pair(3.2f, 2.5f),
        'v' to Pair(4.2f, 2.5f), 'b' to Pair(5.2f, 2.5f), 'n' to Pair(6.2f, 2.5f), 'm' to Pair(7.2f, 2.5f)
    )

    private val profile = BloomSpatialProfile()
    private val aligner = BloomTrajectoryAligner()

    private fun centroidLookup(c: Char): Pair<Float, Float>? = qwertyCentroids[c.lowercaseChar()]

    @Test
    fun `exact matching stroke produces near-zero spatial cost`() {
        // Swipe "to": (4.5, 0.5) -> (8.5, 0.5)
        val rawX = floatArrayOf(4.5f, 5.5f, 6.5f, 7.5f, 8.5f)
        val rawY = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val trajectory = buildBloomTrajectory(rawX, rawY, rawX.size, 48)

        val score = aligner.scoreCandidate(trajectory, "to", ::centroidLookup, profile)
        assertNotNull("Exact match must produce score", score)
        assertTrue("Exact line should have low cost", (score ?: 999f) < 2.5f)
    }

    @Test
    fun `corner cutting swipe for tea matches tea with low cost and rejects zoo`() {
        // Fast swipe for "tea": Start T(4.5, 0.5), cuts corner near E(2.5, 0.5), ends A(0.8, 1.5)
        val rawX = floatArrayOf(4.5f, 3.6f, 2.7f, 1.8f, 0.8f)
        val rawY = floatArrayOf(0.5f, 0.6f, 0.7f, 1.1f, 1.5f)
        val trajectory = buildBloomTrajectory(rawX, rawY, rawX.size, 48)

        val teaScore = aligner.scoreCandidate(trajectory, "tea", ::centroidLookup, profile)
        assertNotNull("Corner-cut stroke must successfully match tea", teaScore)

        val zooScore = aligner.scoreCandidate(trajectory, "zoo", ::centroidLookup, profile)
        assertNull("Geometrically incompatible word must be null", zooScore)
    }

    @Test
    fun `stroke with corner at O matches ton much better than straight line`() {
        val rawX = floatArrayOf(4.5f, 6.5f, 8.5f, 7.3f, 6.2f)
        val rawY = floatArrayOf(0.5f, 0.5f, 0.5f, 1.5f, 2.5f)
        val trajectory = buildBloomTrajectory(rawX, rawY, rawX.size, 48)

        val tonScore = aligner.scoreCandidate(trajectory, "ton", ::centroidLookup, profile)
        assertNotNull(tonScore)

        val tinScore = aligner.scoreCandidate(trajectory, "tin", ::centroidLookup, profile)
        assertTrue("ton must outrank tin because of unexplained corner at O", (tonScore ?: 999f) < (tinScore ?: 999f))
    }

    @Test
    fun `doubled letter too matches same trajectory as to without penalty`() {
        val rawX = floatArrayOf(4.5f, 5.5f, 6.5f, 7.5f, 8.5f)
        val rawY = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val trajectory = buildBloomTrajectory(rawX, rawY, rawX.size, 48)

        val toScore = aligner.scoreCandidate(trajectory, "to", ::centroidLookup, profile)
        val tooScore = aligner.scoreCandidate(trajectory, "too", ::centroidLookup, profile)

        assertNotNull(toScore)
        assertNotNull(tooScore)
        assertEquals(toScore!!.toDouble(), tooScore!!.toDouble(), 0.15)
    }
}
