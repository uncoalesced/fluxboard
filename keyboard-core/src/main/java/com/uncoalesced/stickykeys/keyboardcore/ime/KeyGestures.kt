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
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

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
 * The digit each top-row letter carries in its corner.
 *
 * Keyed by letter rather than by column index so it survives a custom layout reordering the
 * row, and so a layout without a given letter simply has no digit there rather than shifting
 * every other key's number.
 */
private val TOP_ROW_DIGITS =
    mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
    )

/** The digit shown small in a key's corner, or null if it carries none. */
internal fun cornerHintFor(keyOutput: String): String? = TOP_ROW_DIGITS[keyOutput.lowercase()]

/** The hold behaviour for a key, derived from its output alone. */
internal fun longPressFor(keyOutput: String): LongPress {
    if (keyOutput == "DEL") return LongPress.Repeat
    PUNCTUATION_ALTERNATES[keyOutput]?.let { return LongPress.Alternates(it) }
    cornerHintFor(keyOutput)?.let { return LongPress.Alternates(listOf(it)) }
    return LongPress.None
}

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
): Modifier =
    this.pointerInput(keyOutput, longPress, alternates, cellWidthPx) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)

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
