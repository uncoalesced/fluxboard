// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The window arithmetic behind [LatencyTracker], which is the only part of it worth testing.
 *
 * The tracker itself is clock glue: two `SystemClock.uptimeMillis()` reads and a subtraction,
 * neither of which a JVM test can observe without mocking the clock it exists to read. What
 * can be wrong here is the ring buffer -- an eviction that forgets to adjust the running sum
 * reports a rising average forever, and a percentile off by one index is invisible in every
 * output that shows it.
 */
class LatencySamplesTest {
    @Test
    fun `nothing recorded reports null, not zero`() {
        val samples = LatencySamples()
        // Zero would read on the stats screen as "instant", which is the opposite claim.
        assertNull(samples.averageMs())
        assertNull(samples.p95Ms())
        assertEquals(emptyList<Long>(), samples.outliers())
    }

    @Test
    fun `p95 is the 95th percentile of the retained window`() {
        val samples = LatencySamples()
        // Deliberately out of order: the window is a ring, not a sorted structure.
        (1L..100L).shuffled().forEach { samples.add(it) }
        assertEquals(95L, samples.p95Ms())
        assertEquals(50L, samples.averageMs())
    }

    @Test
    fun `a single sample is its own percentile`() {
        val samples = LatencySamples()
        samples.add(7)
        assertEquals(7L, samples.p95Ms())
        assertEquals(7L, samples.averageMs())
    }

    @Test
    fun `the window evicts and the running sum follows it`() {
        val samples = LatencySamples()
        // 300 samples into a 200-wide window: 1..100 must be gone from both the buffer and
        // the sum. A sum that is not decremented on eviction still averages 150 here.
        (1L..300L).forEach { samples.add(it) }
        assertEquals(200L, samples.averageMs())
        assertEquals(290L, samples.p95Ms())
    }

    @Test
    fun `implausible durations are ignored rather than averaged in`() {
        val samples = LatencySamples()
        samples.add(-1)
        samples.add(LatencySamples.MAX_PLAUSIBLE_MS + 1)
        assertNull("a hold or a stale press is not a latency sample", samples.averageMs())

        samples.add(LatencySamples.MAX_PLAUSIBLE_MS)
        assertEquals(LatencySamples.MAX_PLAUSIBLE_MS, samples.averageMs())
    }

    @Test
    fun `the outlier threshold is inclusive and quiet below it`() {
        val samples = LatencySamples()
        samples.add(LatencySamples.OUTLIER_THRESHOLD_MS - 1)
        assertEquals(emptyList<Long>(), samples.outliers())

        samples.add(LatencySamples.OUTLIER_THRESHOLD_MS)
        assertEquals(listOf(LatencySamples.OUTLIER_THRESHOLD_MS), samples.outliers())
    }

    @Test
    fun `outliers keep the most recent spikes, oldest first`() {
        val samples = LatencySamples()
        val first = LatencySamples.OUTLIER_THRESHOLD_MS
        val count = LatencySamples.MAX_OUTLIERS + 10
        (0 until count).forEach { samples.add(first + it) }

        val expected = (count - LatencySamples.MAX_OUTLIERS until count).map { first + it }
        assertEquals(expected, samples.outliers())
    }

    @Test
    fun `clear empties both windows`() {
        val samples = LatencySamples()
        samples.add(200)
        samples.clear()
        assertNull(samples.averageMs())
        assertEquals(emptyList<Long>(), samples.outliers())
    }
}
