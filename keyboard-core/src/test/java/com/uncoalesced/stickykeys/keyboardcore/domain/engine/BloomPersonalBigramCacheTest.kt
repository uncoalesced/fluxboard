// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BloomPersonalBigramCacheTest {
    private lateinit var cache: BloomPersonalBigramCache

    @Before
    fun setUp() {
        cache = BloomPersonalBigramCache()
    }

    @Test
    fun recordTransition_incrementsCountAndCalculatesBoost() {
        assertEquals(0f, cache.boostFor("meet", "rahul"), 0.001f)

        // First transition
        val count1 = cache.recordTransition("meet", "rahul")
        assertEquals(1, count1)
        assertEquals(1, cache.countFor("meet", "rahul"))

        // boostFor formula: min(255, 180 + 1 * 15) / 255f = 195 / 255f
        val expectedBoost1 = 195f / 255.0f
        assertEquals(expectedBoost1, cache.boostFor("meet", "rahul"), 0.001f)

        // Second transition
        val count2 = cache.recordTransition("meet", "rahul")
        assertEquals(2, count2)
        assertEquals(2, cache.countFor("meet", "rahul"))
        val expectedBoost2 = 210f / 255.0f
        assertEquals(expectedBoost2, cache.boostFor("meet", "rahul"), 0.001f)

        // Five transitions reach saturation ceiling: 180 + 5 * 15 = 255 / 255 = 1.0f
        repeat(3) { cache.recordTransition("meet", "rahul") }
        assertEquals(1.0f, cache.boostFor("meet", "rahul"), 0.001f)
    }

    @Test
    fun caseInsensitiveTransitions() {
        cache.recordTransition("Meet", "Rahul")
        assertEquals(1, cache.countFor("meet", "rahul"))
        assertEquals(1, cache.countFor("MEET", "RAHUL"))
        assertTrue(cache.boostFor("meet", "rahul") > 0f)
    }

    @Test
    fun rollbackTransition_decrementsAndPurges() {
        cache.recordTransition("go", "home")
        cache.recordTransition("go", "home")
        assertEquals(2, cache.countFor("go", "home"))

        val rem1 = cache.rollbackTransition("go", "home")
        assertEquals(1, rem1)
        assertEquals(1, cache.countFor("go", "home"))

        val rem2 = cache.rollbackTransition("go", "home")
        assertEquals(0, rem2)
        assertEquals(0, cache.countFor("go", "home"))
        assertEquals(0f, cache.boostFor("go", "home"), 0.001f)
    }

    @Test
    fun followersOf_returnsFollowersSortedByCount() {
        cache.recordTransition("order", "shawarma")
        cache.recordTransition("order", "shawarma")
        cache.recordTransition("order", "pizza")
        cache.recordTransition("order", "biryani")
        cache.recordTransition("order", "biryani")
        cache.recordTransition("order", "biryani")

        val followers = cache.followersOf("order", limit = 5)
        assertEquals(3, followers.size)
        assertEquals("biryani", followers[0].first)
        assertEquals(3, followers[0].second)
        assertEquals("shawarma", followers[1].first)
        assertEquals(2, followers[1].second)
        assertEquals("pizza", followers[2].first)
        assertEquals(1, followers[2].second)
    }

    @Test
    fun capacityPrune_limitsEntries() {
        val smallCache = BloomPersonalBigramCache(maxCapacity = 5)
        for (i in 1..5) {
            smallCache.recordTransition("prev", "next$i", increment = i)
        }
        assertEquals(5, smallCache.totalTransitionsCount())

        // 6th transition triggers pruning of lowest count
        smallCache.recordTransition("prev", "nextNovel", increment = 10)
        assertTrue(smallCache.totalTransitionsCount() <= 5)
        assertTrue(smallCache.countFor("prev", "nextNovel") > 0)
        assertEquals(0, smallCache.countFor("prev", "next1"))
    }

    @Test
    fun decayAll_decrementsAndPurges() {
        cache.recordTransition("hello", "there")
        cache.recordTransition("hello", "there")
        cache.recordTransition("good", "morning")

        cache.decayAll(1)

        assertEquals(1, cache.countFor("hello", "there"))
        assertEquals(0, cache.countFor("good", "morning"))
    }

    @Test
    fun clear_dropsAllTransitions() {
        cache.recordTransition("a", "b")
        cache.recordTransition("c", "d")
        assertEquals(2, cache.totalTransitionsCount())

        cache.clear()
        assertEquals(0, cache.totalTransitionsCount())
        assertEquals(0f, cache.boostFor("a", "b"), 0.001f)
    }

    @Test
    fun effectiveBoostFor_decaysWithFourteenDayHalfLifeAndTenPercentFloor() {
        val t0 = 1_000_000_000L
        val dayMs = 86_400_000L
        cache.recordTransitionAt("meet", "rahul", increment = 2, timestampMs = t0)
        val base = cache.boostFor("meet", "rahul")

        assertEquals(base, cache.effectiveBoostFor("meet", "rahul", nowMs = t0), 1e-5f)
        assertEquals(
            base * 0.5f,
            cache.effectiveBoostFor("meet", "rahul", nowMs = t0 + 14 * dayMs),
            1e-4f,
        )
        assertEquals(
            base * 0.25f,
            cache.effectiveBoostFor("meet", "rahul", nowMs = t0 + 28 * dayMs),
            1e-4f,
        )
        assertEquals(
            base * 0.1f,
            cache.effectiveBoostFor("meet", "rahul", nowMs = t0 + 120 * dayMs),
            1e-4f,
        )
    }

    @Test
    fun followersOf_filtersByMinCount() {
        cache.recordTransition("order", "pizza")
        cache.recordTransition("order", "shawarma")
        cache.recordTransition("order", "shawarma")

        val confirmed = cache.followersOf("order", limit = 5, minCount = 2)
        assertEquals(1, confirmed.size)
        assertEquals("shawarma", confirmed[0].first)
        assertEquals(2, confirmed[0].second)
    }
}
