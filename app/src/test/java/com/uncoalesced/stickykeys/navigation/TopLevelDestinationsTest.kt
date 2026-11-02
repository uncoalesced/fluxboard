// Engineered by uncoalesced
package com.uncoalesced.stickykeys.navigation

import com.uncoalesced.stickykeys.keyboardcore.ime.ROUTE_KEYBOARD_SETTINGS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopLevelDestinationsTest {
    @Test
    fun `the app opens on the keyboard screen`() {
        assertEquals("keyboard", TopLevelDestinations.START)
        // The keyboard's settings shortcut sends this same route; if the two ever differed the
        // shortcut would open a screen the dock does not know about.
        assertEquals(ROUTE_KEYBOARD_SETTINGS, TopLevelDestinations.START)
    }

    @Test
    fun `there are three top-level screens and transfer is not one of them`() {
        assertEquals(listOf("stickers", "keyboard", "settings"), TopLevelDestinations.dockOrder)
    }

    @Test
    fun `the keyboard sits in the middle so either neighbour is one swipe away`() {
        assertEquals("stickers", TopLevelDestinations.swipeTarget("keyboard", -1))
        assertEquals("settings", TopLevelDestinations.swipeTarget("keyboard", 1))
    }

    @Test
    fun `a swipe does not run off either end of the dock`() {
        assertNull(TopLevelDestinations.swipeTarget("stickers", -1))
        assertNull(TopLevelDestinations.swipeTarget("settings", 1))
    }

    @Test
    fun `a detail screen is never swiped away from`() {
        assertNull(TopLevelDestinations.swipeTarget("crop/abc", 1))
        assertNull(TopLevelDestinations.swipeTarget("transfer", -1))
        assertNull(TopLevelDestinations.swipeTarget(null, 1))
    }
}
