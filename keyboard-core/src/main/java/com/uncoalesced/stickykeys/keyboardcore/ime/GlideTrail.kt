// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

/**
 * The glide trail: a tapered, fading stroke over the key grid tracing the finger's path.
 *
 * Reads [GlideTracker.trailPoints] and [GlideTracker.isGliding] only inside the `Canvas` draw
 * lambda, which runs in the draw phase -- a moving finger invalidates this draw call and
 * nothing else. Neither the key grid nor this composable's own recomposition scope reads
 * either value, so [KeyboardRowsView] is untouched by a glide in progress.
 *
 * Points arrive in root coordinates, the same space [KeyboardKey] registers its bounds in, so
 * they are translated by this overlay's own root offset before drawing.
 */
@Composable
internal fun GlideTrailOverlay(glideTracker: GlideTracker) {
    val rootOffset = remember { mutableStateOf(Offset.Zero) }
    val accent = StickyKeysTheme.colors.primary
    val density = LocalDensity.current
    val maxWidthPx = with(density) { GLIDE_TRAIL_MAX_WIDTH.toPx() }
    val minWidthPx = with(density) { GLIDE_TRAIL_MIN_WIDTH.toPx() }

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOffset.value = it.positionInRoot() },
    ) {
        if (!glideTracker.isGliding.value) return@Canvas
        val origin = rootOffset.value
        val trail = glideTracker.trailPoints
        val last = trail.size - 1
        if (last < 1) return@Canvas
        for (i in 1..last) {
            // 0 at the tail (oldest sample), 1 at the head (the finger's current position) --
            // both the taper and the fade ride the same fraction, so the thickest point of the
            // stroke is also its most opaque one.
            val t = i / last.toFloat()
            drawLine(
                color = accent.copy(alpha = t),
                start = trail[i - 1] - origin,
                end = trail[i] - origin,
                strokeWidth = minWidthPx + (maxWidthPx - minWidthPx) * t,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Glide trail stroke width at the finger's current position. */
private val GLIDE_TRAIL_MAX_WIDTH = 7.dp

/** Glide trail stroke width at the oldest visible sample -- the taper's thin end. */
private val GLIDE_TRAIL_MIN_WIDTH = 1.dp
