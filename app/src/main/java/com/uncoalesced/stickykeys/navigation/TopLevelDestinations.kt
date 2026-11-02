// Engineered by uncoalesced
package com.uncoalesced.stickykeys.navigation

import com.uncoalesced.stickykeys.keyboardcore.ime.ROUTE_KEYBOARD_SETTINGS

/**
 * The app's three top-level screens, in dock order, and the one it opens on.
 *
 * One definition read by the nav graph, the dock and the swipe gesture, so the three cannot
 * disagree about what the top level is. Transfer used to be a fourth entry and is now a section
 * of Settings; anything else in the graph is pushed on top of one of these.
 */
object TopLevelDestinations {
    const val STICKERS = "stickers"

    /** Shared with the keyboard's settings shortcut, which opens the app on this route. */
    const val KEYBOARD = ROUTE_KEYBOARD_SETTINGS

    const val SETTINGS = "settings"

    /** Left to right, as the dock draws them and as a swipe moves through them. */
    val dockOrder: List<String> = listOf(STICKERS, KEYBOARD, SETTINGS)

    /**
     * Where the app opens, and the back-stack root every dock tap pops to. The keyboard is the
     * app's main feature and the reason for most launches.
     */
    const val START = KEYBOARD

    /**
     * The route a swipe from [currentRoute] lands on, or null when there is nowhere to go: the
     * current screen is not top-level (a detail screen holding work in progress), or the swipe
     * runs off either end of the dock.
     *
     * @param direction -1 towards the first tab, +1 towards the last.
     */
    fun swipeTarget(
        currentRoute: String?,
        direction: Int,
    ): String? {
        val index = dockOrder.indexOf(currentRoute)
        if (index < 0) return null
        return dockOrder.getOrNull(index + direction)
    }
}
