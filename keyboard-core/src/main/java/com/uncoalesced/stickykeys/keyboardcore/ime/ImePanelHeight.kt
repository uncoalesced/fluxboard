// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R

/**
 * The panel must never eat more than this share of the window, or in a short window the
 * keyboard covers the app the user is typing into.
 */
private const val MAX_WINDOW_FRACTION = 0.5f

/** Never shrink below this, or the panel stops being usable at all. */
private val MIN_PANEL_HEIGHT = 120.dp

/**
 * The single height every IME mode uses, clamped against the window actually available.
 *
 * Two problems solved by one number. First, an IME window is WRAP_CONTENT, so nothing above
 * stops a panel from claiming whatever it asks for -- the clipboard tray asked for a flat
 * 280dp, which in a 300dp-tall split-screen pane left roughly 20dp of host app visible, and
 * the sticker grid used `fillMaxSize()`, which resolves against the window maximum. Second,
 * typing, stickers and clipboard previously each sized themselves differently, so switching
 * between them visibly resized the window; sharing this value means a mode switch changes
 * what is drawn, never how tall it is.
 *
 * The base value comes from `R.dimen.ime_panel_height`, which `values-land` overrides, so
 * landscape gets a shorter keyboard from the resource system rather than from a branch.
 */
@Composable
internal fun rememberImePanelHeight(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val preferred = dimensionResource(R.dimen.ime_panel_height)
    return remember(screenHeight, preferred) {
        val cap = screenHeight * MAX_WINDOW_FRACTION
        when {
            cap < MIN_PANEL_HEIGHT -> minOf(MIN_PANEL_HEIGHT, screenHeight)
            cap < preferred -> cap
            else -> preferred
        }
    }
}
