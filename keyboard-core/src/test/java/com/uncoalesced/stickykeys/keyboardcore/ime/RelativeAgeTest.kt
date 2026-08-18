// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/** Boundaries of the clipboard row's age label, which are the only interesting part of it. */
class RelativeAgeTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val week = 7 * day

    @Test
    fun `under a minute reads as just now`() {
        assertEquals("Just now", relativeAge(0))
        assertEquals("Just now", relativeAge(minute - 1))
    }

    @Test
    fun `each unit takes over exactly at its boundary`() {
        assertEquals("1m ago", relativeAge(minute))
        assertEquals("59m ago", relativeAge(hour - 1))
        assertEquals("1h ago", relativeAge(hour))
        assertEquals("23h ago", relativeAge(day - 1))
        assertEquals("1d ago", relativeAge(day))
        assertEquals("6d ago", relativeAge(week - 1))
        assertEquals("1w ago", relativeAge(week))
    }

    @Test
    fun `a clock moved backwards does not produce a negative age`() {
        // A device whose clock jumps back leaves rows dated in the future. "-3m ago" is worse
        // than useless, and this is the one input the caller cannot rule out.
        assertEquals("Just now", relativeAge(-hour))
    }
}
