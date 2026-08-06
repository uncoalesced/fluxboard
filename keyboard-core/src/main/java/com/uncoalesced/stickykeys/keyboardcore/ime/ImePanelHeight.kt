// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences

/**
 * The panel must never eat more than this share of the window, or in a short window the
 * keyboard covers the app the user is typing into.
 */
private const val MAX_WINDOW_FRACTION = 0.5f

/**
 * The hard ceiling, reachable only by a user who has deliberately raised the height slider.
 *
 * The cap cannot simply be raised for everyone. It is what keeps a 280dp panel from leaving
 * roughly 20dp of a split-screen pane visible, and that is a real constraint rather than a
 * conservative guess. But leaving it at exactly half also means the top of the height slider
 * does nothing on a normal phone once the number row is on -- the request is clamped away and
 * the control silently stops responding, which is worse than either bound.
 *
 * So the cap scales with the user's own setting and stops here. Nobody who has not touched
 * the slider is affected at all.
 */
private const val MAX_WINDOW_FRACTION_USER_RAISED = 0.62f

/** Never shrink below this, or the panel stops being usable at all. */
private val MIN_PANEL_HEIGHT = 120.dp

/**
 * User-controlled panel sizing, provided once at the IME root.
 *
 * A CompositionLocal rather than a parameter because [rememberImePanelHeight] is called from
 * five different mode views, none of which otherwise has any reason to know about preferences.
 * The default is the shipped behaviour, so a preview or test that never provides it renders
 * exactly as before.
 */
@Immutable
internal data class ImePanelMetrics(
    /** 1.0 is the shipped height. */
    val heightScale: Float = 1f,
    /** Gap held below the bottom key row. */
    val bottomPadding: Dp = KeyboardPreferences.DEFAULT_BOTTOM_PADDING_DP.dp,
    /** Whether the digit row is drawn, and therefore needs its own height. */
    val showNumberRow: Boolean = false,
    /**
     * How large a key is drawn inside the cell the layout gives it. 1.0 is the shipped size.
     *
     * Separate from [heightScale], which changes how much screen the panel takes. This changes
     * the split between key and gap inside whatever space the panel already has, so the two
     * are genuinely independent controls rather than two names for the same slider.
     */
    val keyScale: Float = 1f,
)

/**
 * The gap left around each key, given the user's key-size preference.
 *
 * Inverse by construction: a larger key means a smaller gap, because the cell the key sits in
 * is fixed by the row layout. Pure so the inversion is assertable -- getting the sign wrong
 * here produces a control that visibly does something and does the opposite of its label.
 */
internal fun keyPaddingFor(
    keyScale: Float,
    basePadding: Dp,
): Dp = (basePadding * (2f - keyScale.coerceIn(0.5f, 1.5f))).coerceAtLeast(0.dp)

internal val LocalImePanelMetrics = compositionLocalOf { ImePanelMetrics() }

/**
 * The single height every IME mode uses, clamped against the window actually available.
 *
 * Three problems solved by one number. First, an IME window is WRAP_CONTENT, so nothing above
 * stops a panel from claiming whatever it asks for -- the clipboard tray asked for a flat
 * 280dp, which in a 300dp-tall split-screen pane left roughly 20dp of host app visible, and
 * the sticker grid used `fillMaxSize()`, which resolves against the window maximum. Second,
 * typing, stickers and clipboard previously each sized themselves differently, so switching
 * between them visibly resized the window; sharing this value means a mode switch changes
 * what is drawn, never how tall it is.
 *
 * Third, and the reason this is no longer a constant: the digit row used to be drawn *inside*
 * the same fixed height as everything else. Five rows dividing the space four had left every
 * key a fifth shorter the moment the setting was turned on, which is a real accuracy cost for
 * a row the user asked to add. It now brings its own height, and the whole panel is scaled by
 * the user's own preference on top of that -- there is no one correct keyboard height across
 * thumb lengths and screen sizes, so this stops trying to pick one.
 *
 * The base value comes from `R.dimen.ime_panel_height`, which `values-land` overrides, so
 * landscape gets a shorter keyboard from the resource system rather than from a branch.
 */
@Composable
internal fun rememberImePanelHeight(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val preferred = dimensionResource(R.dimen.ime_panel_height)
    val numberRow = dimensionResource(R.dimen.ime_number_row_height)
    val metrics = LocalImePanelMetrics.current
    return remember(screenHeight, preferred, numberRow, metrics) {
        val base = preferred + if (metrics.showNumberRow) numberRow else 0.dp
        panelHeightFor(base * metrics.heightScale, screenHeight, metrics.heightScale)
    }
}

/**
 * Clamps a requested panel height against the window.
 *
 * Pure and separate from the composable so the clamping is assertable without a display --
 * the interesting cases are short windows (split screen, landscape) where the request has to
 * be refused, and those are exactly the ones hardest to reproduce in a UI test.
 */
internal fun panelHeightFor(
    requested: Dp,
    windowHeight: Dp,
    heightScale: Float = 1f,
): Dp {
    // Only a scale above 1 lifts the cap, and never past the hard ceiling. Choosing a
    // *smaller* keyboard must not also tighten the clamp -- the request is already small, so
    // there would be nothing to clamp and it would only make short windows behave oddly.
    val fraction =
        (MAX_WINDOW_FRACTION * maxOf(1f, heightScale))
            .coerceAtMost(MAX_WINDOW_FRACTION_USER_RAISED)
    val cap = windowHeight * fraction
    return when {
        cap < MIN_PANEL_HEIGHT -> minOf(MIN_PANEL_HEIGHT, windowHeight)
        cap < requested -> cap
        else -> requested
    }
}
