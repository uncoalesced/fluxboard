// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height the sticker and clipboard panels aim for when there is room. */
internal val PREFERRED_PANEL_HEIGHT = 280.dp

/**
 * The panel must never eat more than this share of the window, or in a short window the
 * keyboard covers the app the user is typing into.
 */
private const val MAX_WINDOW_FRACTION = 0.5f

/** Never shrink below this, or the panel stops being usable at all. */
private val MIN_PANEL_HEIGHT = 120.dp

/**
 * Height for the sticker and clipboard panels, clamped against the window actually
 * available.
 *
 * An IME window is WRAP_CONTENT, so nothing above stops a panel from claiming whatever it
 * asks for. The clipboard tray asked for a flat 280dp and the sticker grid used
 * `fillMaxSize()`, both of which are fine at full height and wrong in split-screen or
 * landscape: at a 300dp-tall window the tray alone left roughly 20dp of host app visible,
 * and `fillMaxSize()` resolves against the window maximum, so the grid took the entire
 * height it was offered.
 */
@Composable
internal fun rememberImePanelHeight(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return remember(screenHeight) {
        val cap = screenHeight * MAX_WINDOW_FRACTION
        when {
            cap < MIN_PANEL_HEIGHT -> minOf(MIN_PANEL_HEIGHT, screenHeight)
            cap < PREFERRED_PANEL_HEIGHT -> cap
            else -> PREFERRED_PANEL_HEIGHT
        }
    }
}
