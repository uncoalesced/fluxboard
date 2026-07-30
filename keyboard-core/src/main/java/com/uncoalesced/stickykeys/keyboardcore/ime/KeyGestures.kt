// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * What a key does when it is held rather than tapped.
 *
 * One type for every hold behaviour on the board, because they are the same gesture with
 * different payloads: the press/threshold/release state machine is identical and only what
 * happens after the threshold differs. Three separate per-key handlers would each need their
 * own copy of the timing, their own cancellation handling, and their own interaction with the
 * tap path -- which is how "held backspace sometimes types a character" bugs happen.
 */
internal sealed interface LongPress {
    /** Tap-only. */
    data object None : LongPress

    /** Fire the key's own output over and over, faster the longer it is held. Backspace. */
    data object Repeat : LongPress

    /** Show a pick-one strip above the key. Punctuation alternates, top-row digits. */
    data class Alternates(
        val options: List<String>,
    ) : LongPress

    /** Drag sideways to walk the caret instead of typing. The space bar. */
    data object Scrub : LongPress
}

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

/** How long a press must be held before it stops counting as a tap. */
internal const val LONG_PRESS_MS = 350L

private const val REPEAT_FIRST_INTERVAL_MS = 90L
private const val REPEAT_MIN_INTERVAL_MS = 22L
private const val REPEAT_DECAY = 0.88

/**
 * Delay before the nth repeat of a held key, in milliseconds.
 *
 * Geometric decay to a floor rather than a fixed rate: a fixed rate is either too slow to
 * clear a line or so fast that a short hold overshoots and eats a word the user wanted. The
 * floor exists because past roughly 45 deletes per second the user cannot react in time to
 * stop where they meant to.
 *
 * Pure and separate from the gesture so the curve is assertable without a touch screen.
 */
internal fun repeatIntervalAt(step: Int): Long {
    var interval = REPEAT_FIRST_INTERVAL_MS.toDouble()
    repeat(step) { interval *= REPEAT_DECAY }
    return interval.toLong().coerceAtLeast(REPEAT_MIN_INTERVAL_MS)
}

/**
 * Punctuation reachable by holding, so common marks do not cost a trip to the symbols page.
 *
 * Ordered by how often they are actually wanted -- the first entry is what a TalkBack
 * long-press commits, and what the finger is already over when the strip appears.
 */
private val PUNCTUATION_ALTERNATES =
    mapOf(
        "." to listOf(",", "?", "!", ";", ":", "'"),
        "," to listOf(".", "?", "!"),
    )

/**
 * The hold behaviour for a key.
 *
 * Driven by the layout's own [hint] rather than a table keyed on letters. A hardcoded map
 * would go stale the moment a custom layout moved a key, and would disagree with the corner
 * glyph the user can see -- the promise the superscript makes is precisely "hold this and
 * you get that", so the two must come from one field.
 *
 * Hints that are not a single character are labels, not outputs: `,!?` advertises that
 * alternates exist, and `MIC` names an icon. Neither is typeable, so both fall through to
 * the explicit tables.
 */
internal fun longPressFor(
    keyOutput: String,
    hint: String? = null,
): LongPress {
    if (keyOutput == "SPACE") return LongPress.Scrub
    if (keyOutput == "DEL") return LongPress.Repeat
    PUNCTUATION_ALTERNATES[keyOutput]?.let { return LongPress.Alternates(it) }
    if (hint != null && hint.length == 1) return LongPress.Alternates(listOf(hint))
    return LongPress.None
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
private suspend fun AwaitPointerEventScope.runScrub(
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

/**
 * The alternates strip, hoisted out of the key that opened it.
 *
 * It has to outlive the key's own bounds: the finger routinely slides off the originating key
 * while choosing, and a popup owned by that key would be dismissed by its own pointer-exit.
 * Holding it at the grid level also means exactly one strip can be open at a time, which is
 * what stops a second finger opening a competing one.
 */
@Stable
internal class KeyAlternatesState {
    var options by mutableStateOf<List<String>>(emptyList())
        private set

    /** The originating key's bounds, in root coordinates. Null when nothing is showing. */
    var anchor by mutableStateOf<Rect?>(null)
        private set

    var selectedIndex by mutableIntStateOf(0)
        private set

    private var cellWidthPx = 1f

    val visible: Boolean get() = anchor != null

    fun show(
        options: List<String>,
        anchor: Rect,
        cellWidthPx: Float,
    ) {
        this.options = options
        this.anchor = anchor
        this.cellWidthPx = cellWidthPx.coerceAtLeast(1f)
        this.selectedIndex = 0
    }

    /** Track the finger across the strip. [x] is in root coordinates. */
    fun moveTo(x: Float) {
        val bounds = anchor ?: return
        if (options.isEmpty()) return
        val offset = ((x - bounds.left) / cellWidthPx).toInt()
        selectedIndex = offset.coerceIn(0, options.lastIndex)
    }

    /** Hide, returning whatever was under the finger. */
    fun consume(): String? {
        val picked = options.getOrNull(selectedIndex)
        hide()
        return picked
    }

    fun hide() {
        anchor = null
        options = emptyList()
        selectedIndex = 0
    }
}

/**
 * The whole press/hold/release state machine for one key.
 *
 * Replaces `clickable` rather than sitting alongside it: two gesture detectors over the same
 * key both claim the down event, and the tap would fire again on release after a hold had
 * already committed something.
 *
 * [keyBounds] is read lazily at hold time rather than captured, so a layout pass between the
 * down event and the threshold cannot anchor the strip to a stale rectangle.
 */
internal fun Modifier.keyGestures(
    keyOutput: String,
    longPress: LongPress,
    alternates: KeyAlternatesState,
    cellWidthPx: Float,
    keyBounds: () -> Rect,
    onCommit: (String) -> Unit,
    onScrub: (Int, Boolean) -> Unit = { _, _ -> },
): Modifier =
    this.pointerInput(keyOutput, longPress, alternates, cellWidthPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            // Scrubbing is decided before the long-press clock, not after it. Gating it on
            // the hold threshold would mean the cursor sat still for the first 350ms of a
            // drag, which reads as the gesture being broken rather than deliberate.
            if (longPress is LongPress.Scrub) {
                runScrub(
                    activationPx = SCRUB_ACTIVATION_DP.dp.toPx(),
                    stepPxFor = { held -> scrubStepDp(held).dp.toPx() },
                    onScrub = onScrub,
                    onTap = { onCommit(keyOutput) },
                )
                return@awaitEachGesture
            }

            // null  -> the threshold elapsed, this is a hold
            // true  -> released before the threshold, an ordinary tap
            // false -> the gesture was cancelled out from under us
            val early =
                withTimeoutOrNull(LONG_PRESS_MS) {
                    waitForUpOrCancellation() != null
                }

            when {
                early == true -> onCommit(keyOutput)
                early == false -> Unit
                longPress is LongPress.None -> {
                    // Held, but this key has no hold behaviour: still a keystroke on release,
                    // otherwise resting a moment on a letter would silently swallow it.
                    if (waitForUpOrCancellation() != null) onCommit(keyOutput)
                }
                longPress is LongPress.Repeat -> {
                    var step = 0
                    while (true) {
                        onCommit(keyOutput)
                        val ended =
                            withTimeoutOrNull(repeatIntervalAt(step)) {
                                waitForUpOrCancellation()
                            }
                        if (ended != null) break
                        step++
                    }
                }
                longPress is LongPress.Alternates -> {
                    alternates.show(longPress.options, keyBounds(), cellWidthPx)
                    val origin = keyBounds().left
                    var committed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null) break
                        alternates.moveTo(origin + change.position.x)
                        if (!change.pressed) {
                            committed = true
                            break
                        }
                    }
                    val picked = if (committed) alternates.consume() else null
                    alternates.hide()
                    if (picked != null) onCommit(picked)
                }
            }
        }
    }
