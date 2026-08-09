// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlidePoint
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlideStroke
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.buildGlideStroke

/**
 * Collects a glide across the whole key grid.
 *
 * A glide is the one gesture on this keyboard that is not owned by a key. It begins on one and
 * ends on another, so the per-key state machine in `KeyGestures` cannot decide it alone -- but
 * neither can a second pointer handler layered over the grid, because two detectors claiming
 * the same down event is exactly the problem `keyGestures` was written to avoid.
 *
 * So the key that receives the down keeps ownership of the gesture and reports positions here,
 * in root coordinates. This object holds the only thing a single key cannot know: where all the
 * *other* keys are.
 *
 * Registration is by output character rather than by key id because that is what the decoder
 * speaks. Keys that cannot appear in a word -- shift, backspace, the symbol pages -- are simply
 * never registered, so a path crossing them contributes nothing rather than needing a filter.
 */
@Stable
internal class GlideTracker {
    private val keyRects = mutableMapOf<Char, Rect>()

    /** Points collected so far, in root coordinates. */
    private val points = mutableListOf<Offset>()

    /**
     * Live so the view can dim the suggestion strip or show a trail while a glide is running.
     * A plain field would not recompose anything.
     */
    var isGliding = mutableStateOf(false)
        private set

    /** Set once at the start, so [buildGlideStroke] can work in key widths rather than pixels. */
    private var keyWidthPx = 1f

    /**
     * Registers where a letter key currently is.
     *
     * Called from each key's own `onGloballyPositioned`, which already runs for the alternates
     * strip. Re-registering on every layout pass is deliberate: the grid changes shape when the
     * number row is toggled, when the height preference moves, and on rotation, and a stale
     * rectangle would silently decode glides against the previous layout.
     */
    fun register(
        key: Char,
        bounds: Rect,
    ) {
        // Lowercased, and this is not cosmetic. A key reports whatever it currently *types*,
        // so while shift is armed the letter keys register as 'H', 'E', 'L'. The dictionary is
        // lowercase, so a path recorded in capitals matches nothing and the glide silently
        // decodes to nothing -- which is exactly what happened on the first device run, where
        // auto-capitalize had armed shift on an empty field and every glide returned null with
        // no error anywhere. Case belongs to rendering; the path is about which key was
        // crossed.
        keyRects[key.lowercaseChar()] = bounds
        if (bounds.width > 0f) keyWidthPx = bounds.width
    }

    fun forget(key: Char) {
        keyRects.remove(key)
    }

    fun begin(at: Offset) {
        points.clear()
        points.add(at)
        isGliding.value = true
    }

    fun move(to: Offset) {
        // Sub-pixel jitter from a resting finger would otherwise dominate the sample list and
        // drown the direction changes that identify which letters were aimed at.
        val last = points.lastOrNull()
        if (last != null && (last - to).getDistance() < MIN_SAMPLE_DISTANCE_PX) return
        points.add(to)
    }

    /** Ends the gesture and returns what was drawn, or null if it was not a usable glide. */
    fun finish(): GlideStroke? {
        isGliding.value = false
        if (points.size < 2) {
            points.clear()
            return null
        }
        val width = keyWidthPx.coerceAtLeast(1f)
        val samples =
            points.map { p ->
                GlidePoint(
                    // Divided by key width so the decoder's thresholds are in key widths and
                    // hold on any screen density or keyboard height.
                    x = p.x / width,
                    y = p.y / width,
                    key = keyAt(p),
                )
            }
        points.clear()
        val stroke = buildGlideStroke(samples)
        return if (stroke.isUsable) stroke else null
    }

    fun cancel() {
        points.clear()
        isGliding.value = false
    }

    /** The registered letter under [point], or null between keys and off the letter rows. */
    private fun keyAt(point: Offset): Char? {
        keyRects.forEach { (key, rect) ->
            if (rect.contains(point)) return key
        }
        return null
    }

    private companion object {
        /** Roughly a tenth of a key: enough to drop jitter, fine enough to keep corners. */
        const val MIN_SAMPLE_DISTANCE_PX = 6f
    }
}
