// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BloomAdaptiveThresholdTest {
    private lateinit var threshold: BloomAdaptiveThreshold

    @Before
    fun setUp() {
        threshold = BloomAdaptiveThreshold()
    }

    @Test
    fun `initial state starts at baseline zero`() {
        assertEquals(0f, threshold.currentOffset(), 0f)
        assertEquals(Triple(0, 0, 0f), threshold.snapshot())
    }

    @Test
    fun `recordAccept lowers offset and clamps at MIN_OFFSET`() {
        threshold.recordAccept()
        assertEquals(BloomAdaptiveThreshold.ACCEPT_STEP, threshold.currentOffset(), 1e-6f)
        assertEquals(1, threshold.snapshot().first)

        repeat(50) { threshold.recordAccept() }
        assertEquals(BloomAdaptiveThreshold.MIN_OFFSET, threshold.currentOffset(), 1e-6f)
        assertEquals(51, threshold.snapshot().first)
    }

    @Test
    fun `recordRevert raises offset and clamps at MAX_OFFSET`() {
        threshold.recordRevert()
        assertEquals(BloomAdaptiveThreshold.REVERT_STEP, threshold.currentOffset(), 1e-6f)
        assertEquals(1, threshold.snapshot().second)

        repeat(20) { threshold.recordRevert() }
        assertEquals(BloomAdaptiveThreshold.MAX_OFFSET, threshold.currentOffset(), 1e-6f)
        assertEquals(21, threshold.snapshot().second)
    }

    @Test
    fun `restore clamps counts and offset and rejects non-finite offset`() {
        threshold.restore(accepts = -5, reverts = -2, offset = 0.500f)
        assertEquals(0, threshold.snapshot().first)
        assertEquals(0, threshold.snapshot().second)
        assertEquals(BloomAdaptiveThreshold.MAX_OFFSET, threshold.currentOffset(), 1e-6f)

        threshold.restore(accepts = 12, reverts = 3, offset = -0.500f)
        assertEquals(12, threshold.snapshot().first)
        assertEquals(3, threshold.snapshot().second)
        assertEquals(BloomAdaptiveThreshold.MIN_OFFSET, threshold.currentOffset(), 1e-6f)

        threshold.restore(accepts = 99, reverts = 99, offset = Float.NaN)
        assertEquals(12, threshold.snapshot().first)
        assertEquals(3, threshold.snapshot().second)
        assertEquals(BloomAdaptiveThreshold.MIN_OFFSET, threshold.currentOffset(), 1e-6f)

        threshold.restore(accepts = 99, reverts = 99, offset = Float.POSITIVE_INFINITY)
        assertEquals(BloomAdaptiveThreshold.MIN_OFFSET, threshold.currentOffset(), 1e-6f)
    }

    @Test
    fun `reset returns counters and offset to baseline`() {
        threshold.recordRevert()
        threshold.recordAccept()
        threshold.reset()

        assertEquals(Triple(0, 0, 0f), threshold.snapshot())
        assertEquals(0f, threshold.currentOffset(), 0f)
    }

    @Test
    fun `concurrent updates remain bounded and consistent`() {
        val threads = 8
        val rounds = 200
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) { idx ->
            pool.execute {
                try {
                    start.await()
                    repeat(rounds) {
                        if (idx % 2 == 0) {
                            threshold.recordAccept()
                        } else {
                            threshold.recordRevert()
                        }
                    }
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdownNow()

        val (accepts, reverts, offset) = threshold.snapshot()
        assertEquals((threads / 2) * rounds, accepts)
        assertEquals((threads / 2) * rounds, reverts)
        assertTrue(
            offset in BloomAdaptiveThreshold.MIN_OFFSET..BloomAdaptiveThreshold.MAX_OFFSET,
        )
    }
}
