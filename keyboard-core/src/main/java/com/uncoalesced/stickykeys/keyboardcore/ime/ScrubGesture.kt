// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

// The space-bar scrub: drag along the space bar to move the caret.

/**
 * How far the finger must travel sideways before a space-bar press becomes a scrub.
 *
 * This is the "low sensitivity" requirement, and it is the whole reason the gesture is safe
 * to put on the most-pressed key on the board. Ordinary taps move a couple of pixels and
 * thumbs wobble on the way down; anything under this is still a space. Once crossed, the
 * press can no longer produce a space at all -- a gesture that both moved the caret and
 * typed would be worse than either.
 */
internal const val SCRUB_ACTIVATION_DP = 18f

/** Finger travel per caret step at the start of a scrub. */
private const val SCRUB_STEP_START_DP = 14f

/** Finger travel per caret step once the drag has been sustained. */
private const val SCRUB_STEP_MIN_DP = 4f

/** Sustained dragging only starts winding up after this long, so short corrections stay 1:1. */
private const val SCRUB_ACCEL_DELAY_MS = 250L

/** How long the ramp from the start step to the minimum step takes. */
private const val SCRUB_ACCEL_RAMP_MS = 1200f

/**
 * Finger travel required for one caret step, given how long the scrub has been running.
 *
 * This is the acceleration: the distance per character *shrinks* the longer the drag is
 * sustained, so the same finger speed walks the caret faster the longer it is held. Ramping
 * the step rather than multiplying a velocity keeps the gesture positional -- the caret
 * still tracks the finger and stops dead when the finger stops, which a velocity model does
 * not do.
 *
 * Pure, so the ramp is assertable without a touchscreen.
 */
internal fun scrubStepDp(heldMillis: Long): Float {
    val past = (heldMillis - SCRUB_ACCEL_DELAY_MS).coerceAtLeast(0L)
    val progress = (past / SCRUB_ACCEL_RAMP_MS).coerceIn(0f, 1f)
    return SCRUB_STEP_START_DP + (SCRUB_STEP_MIN_DP - SCRUB_STEP_START_DP) * progress
}

/**
 * The space-bar scrub: touch down, drag distance, hold duration.
 *
 * Three pieces of state rather than an `onDrag` callback, because each answers a different
 * question that a raw drag stream cannot. Accumulated horizontal travel decides *whether*
 * this is a scrub at all and survives the finger changing direction; elapsed time since
 * activation decides *how fast* it walks; and the leftover accumulator decides *when* the
 * next step lands, so travel is never rounded away between events. A plain drag callback
 * would emit one step per touch sample, which makes the speed a property of the device's
 * reporting rate instead of the user's finger.
 *
 * Emits nothing until [activationPx] is crossed, and once it has, never types a space.
 */
internal suspend fun AwaitPointerEventScope.runScrub(
    activationPx: Float,
    stepPxFor: (Long) -> Float,
    onScrub: (Int, Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var travelX = 0f
    var travelY = 0f
    var accumulator = 0f
    var scrubbing = false
    var scrubStartedAt = 0L
    var lastHapticAt = 0L

    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break
        val delta = change.positionChange()
        travelX += delta.x
        travelY += delta.y

        if (!scrubbing) {
            // Sideways intent required, not just distance: a swipe down off the space bar to
            // dismiss the keyboard travels far, and should not drag the caret on its way out.
            if (abs(travelX) >= activationPx && abs(travelX) > abs(travelY)) {
                scrubbing = true
                scrubStartedAt = change.uptimeMillis
                accumulator = 0f
                // Step once on activation rather than only arming the accumulator. Crossing
                // the threshold is already an unambiguous sideways intent, and making the
                // user travel the activation distance *and then* a full step before anything
                // moved would put roughly 32dp of dead travel at the start of every scrub.
                onScrub(if (travelX > 0f) 1 else -1, true)
                lastHapticAt = change.uptimeMillis
            }
        }

        if (scrubbing) {
            accumulator += delta.x
            val held = change.uptimeMillis - scrubStartedAt
            val step = stepPxFor(held)
            while (abs(accumulator) >= step && step > 0f) {
                val direction = if (accumulator > 0f) 1 else -1
                // Throttled, not per-step. At the accelerated end a fast drag crosses a step
                // every few milliseconds, and an unthrottled buzz there stops being discrete
                // feedback and becomes one continuous vibration -- which conveys nothing and
                // is unpleasant to hold.
                val hapticDue = change.uptimeMillis - lastHapticAt >= SCRUB_HAPTIC_MIN_INTERVAL_MS
                onScrub(direction, hapticDue)
                if (hapticDue) lastHapticAt = change.uptimeMillis
                accumulator -= direction * step
            }
            // Claim the events so no ancestor can also read this drag as a scroll.
            change.consume()
        }

        if (!change.pressed) break
    }

    // A press that never became a scrub is still an ordinary space. The elapsed time is not
    // consulted: resting on the space bar without moving should type one space, not none.
    if (!scrubbing) {
        onTap()
    }
}

/** Floor between scrub haptics, so the accelerated end does not become a continuous buzz. */
private const val SCRUB_HAPTIC_MIN_INTERVAL_MS = 28L
