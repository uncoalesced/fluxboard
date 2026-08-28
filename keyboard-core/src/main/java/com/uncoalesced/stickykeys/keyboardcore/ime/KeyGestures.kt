// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.LatencyTracker
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

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

    /**
     * Show a pick-one strip above the key. Punctuation alternates, top-row digits.
     *
     * [defaultIndex] is the cell selected when the strip opens, and therefore what a release
     * without any sideways drag commits. It is 0 almost everywhere; the currency key is the
     * exception, because the character its own face shows sits in the middle of the run of
     * currencies rather than at the start.
     */
    data class Alternates(
        val options: List<String>,
        val defaultIndex: Int = 0,
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

/**
 * How far the finger must travel before it starts choosing cells in an alternates strip.
 *
 * Well below one cell, because this is not a selection threshold -- it only has to absorb the
 * jitter of a stationary finger so that a hold-and-release keeps the strip's default cell.
 * Past it, every cell including the first stays reachable by dragging back.
 */
internal const val ALTERNATE_DRAG_SLOP_DP = 6f

/**
 * How far left the finger must drag, during a backspace hold, before deletes become word-wise.
 *
 * Deliberately larger than [SCRUB_ACTIVATION_DP], and that gap is the whole safety argument.
 * The repeat path has never had an activation distance because it never needed one: holding
 * backspace is the most-used gesture on this board and a thumb resting on it drifts. Reusing
 * the scrub's 18dp would turn that ordinary drift into a word delete, which regresses every
 * plain backspace hold rather than only adding a new gesture.
 */
internal const val WORD_DELETE_ACTIVATION_DP = 24f

/**
 * Further leftward travel per word deleted, once word mode is active.
 *
 * Roughly one word's visual width at the default size. Positional like the scrub rather than
 * timed: the words disappear as the finger passes over where they were, and stop the instant
 * it stops, which is what makes a run of them feel controllable instead of runaway.
 */
internal const val WORD_DELETE_STEP_DP = 40f

/**
 * Whether accumulated travel during a backspace hold means "delete by words".
 *
 * Leftward only: backspace deletes backwards, so a rightward drag has no matching meaning and
 * is left doing nothing rather than invented. The sideways-intent test is the scrub's, for the
 * same reason -- a swipe down off the keyboard travels a long way and must not take a sentence
 * with it on the way out.
 *
 * Pure, so the threshold is assertable without a pointer harness.
 */
internal fun wordDeleteActivated(
    travelX: Float,
    travelY: Float,
    activationPx: Float,
): Boolean = -travelX >= activationPx && abs(travelX) > abs(travelY)

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
 * How long a press must be held before it stops counting as a tap.
 *
 * 150ms, down from 350ms. At the old value holding a letter for its corner symbol felt like
 * waiting rather than pressing, which is the whole complaint: the symbol is the point of the
 * gesture and it arrived long after the finger expected it.
 *
 * This is the deadline for a finger that is *not moving*. A press that has already travelled
 * [GLIDE_STIR_DP] is going somewhere and is given until [GLIDE_WATCH_MS] to say where, which
 * is what keeps a shorter hold from cutting glides short. Shortening this to 150ms on its own
 * did exactly that: gliding "ok" started committing o's corner symbol, because a diagonal
 * needs about 190ms to clear the key it began on.
 *
 * A finger that rests on the first letter before setting off still reaches the alternates
 * strip rather than gliding, and now reaches it sooner. That has always been the behaviour;
 * resting before swiping has never produced a word.
 *
 * Backspace's repeat also starts from here, so hold-to-delete begins sooner too.
 */
internal const val LONG_PRESS_MS = 150L

/**
 * Longest a *moving* finger is given to leave its key before the press is called a hold.
 *
 * The old single threshold did both jobs and could not do them differently. Shortening it to
 * make holds prompt also cut short every glide that needs longer to clear its first key, and
 * a diagonal is exactly that case: "ok" runs from the o key down to k and takes roughly
 * 190ms to clear the origin at an ordinary speed. On device that turned "ok" into "{", the
 * corner symbol of the key it started on.
 *
 * Kept at the value the single threshold used to have, so a glide has exactly the time it
 * always had. Only a finger that has already travelled [GLIDE_STIR_DP] ever reaches it.
 */
internal const val GLIDE_WATCH_MS = 350L

/**
 * How far a finger must travel inside its own key before it stops looking like a hold.
 *
 * Below this it is a resting thumb, and the alternates strip should arrive on time. Above it
 * the press is going somewhere and is worth waiting on. Deliberately larger than the 6dp slop
 * the alternates strip already uses to absorb jitter, and well below the escape distance, so
 * it separates "moving" from "still" without deciding anything on its own.
 */
internal const val GLIDE_STIR_DP = 10f

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
 * Every currency the board offers, and the cell selected when the strip opens.
 *
 * One list, reached from two keys -- the symbols page's `$` and digit 4 on the number row --
 * because a user who found `€` under one of them and not the other would reasonably conclude
 * the keyboard had lost it. Still attached to each [KeyDefinition] individually rather than
 * looked up by output: both keys type `$`, and an output-keyed table is exactly the shape that
 * gave the symbols page digit 4's fraction strip.
 *
 * `$` sits at index 2 because the list reads in a conventional order rather than starting with
 * the key's own face -- so the default cell has to be named rather than assumed to be first.
 */
internal val CURRENCY_ALTERNATES = listOf("€", "¥", "$", "¢", "₹")
internal const val CURRENCY_DEFAULT_INDEX = 2

/**
 * What holding a digit on the number row offers.
 *
 * **The shifted symbol is deliberately not here**, with one named exception below. It used to
 * lead every strip, on the reasoning that the corner hint promised it. But the same character
 * is already directly reachable by arming shift, which swaps the whole row to real
 * shifted-symbol keys, so the cell was a second route to something one tap away and it pushed
 * the content the strip exists for down the row. The corner hint stays as information -- it
 * still tells the user what shift will produce -- it simply is no longer what the hold commits.
 *
 * A release without any sideways drag therefore commits the superscript, which is index 0.
 *
 * Ordering is superscript, then the vulgar fractions with that digit as numerator ascending by
 * value, then any superscript letter that belongs with it. There is no length ceiling: the
 * strip sizes its own cells to fit (see [alternateCellWidthPx]).
 *
 * **Digit 4 is the exception, by decision rather than oversight.** It carries the currencies
 * instead of `⁴ ⅘`, and keeps `$` among them even though `$` is its own shifted symbol. The
 * general rule exists because a duplicated cell crowds out content the strip is *for*; here
 * the currencies are the content, `$` belongs beside them to read as a set rather than as four
 * strays, and it is the cell a straight hold should commit. So the trade the rule was written
 * to prevent does not arise: the superscript and fraction were dropped for the currencies, not
 * squeezed alongside them. Six cells is the practical ceiling on a narrow phone and this is
 * five. See [DIGIT_ALTERNATES_DEFAULT_INDEX] for the cell it opens on.
 *
 * `1` and `5` are Joel's confirmed spec, quoted rather than derived -- note that `1` is a
 * *curated* subset of the nine numerator-1 fractions, so it cannot be generated. The remaining
 * digits were proposed rather than inferred from it, and are confirmed as shipped.
 *
 * Attached to the keys themselves rather than consulted here -- see [KeyDefinition.alternates]
 * for why an output-keyed table was the wrong shape.
 */
internal val DIGIT_ALTERNATES =
    mapOf(
        "0" to listOf("⁰"),
        "1" to listOf("¹", "⅛", "¼", "⅓", "½", "ⁱ"),
        "2" to listOf("²", "⅔", "⅖"),
        "3" to listOf("³", "¾", "⅗", "⅜"),
        "4" to CURRENCY_ALTERNATES,
        "5" to listOf("⁵", "⅝", "⅚", "ⁿ"),
        "6" to listOf("⁶"),
        "7" to listOf("⁷", "⅞"),
        "8" to listOf("⁸"),
        "9" to listOf("⁹"),
    )

/**
 * Which cell of a digit's strip opens selected, for the digits where it is not the first.
 *
 * A map with one entry rather than a field on every digit: index 0 is right for a strip that
 * leads with its superscript, and naming the exception is what keeps the rule readable. This
 * is also what a TalkBack long-press commits, so it is behaviour rather than presentation --
 * getting it wrong gives screen-reader users a different character from everyone else.
 */
internal val DIGIT_ALTERNATES_DEFAULT_INDEX = mapOf("4" to CURRENCY_DEFAULT_INDEX)

/**
 * Whether a key can take part in a glide.
 *
 * Only single letters. A path across shift, backspace or the symbol switcher says nothing
 * about a word, and letting those start a glide would mean a mistimed drag off the shift key
 * typed something. The space bar is excluded here too because it owns the scrub gesture, which
 * is decided before this point.
 */
internal fun isGlideCandidate(keyOutput: String): Boolean =
    keyOutput.length == 1 && keyOutput[0].isLetter()

/**
 * Dead zone past a key's own edge, before a press is read as the start of a glide.
 *
 * Crossing the edge is still what decides a glide -- see the comment in the watch loop for why
 * that, and not a travel distance, is the test. This is only the tolerance on it, and it exists
 * because "crossed the edge" and "is a glide" stopped being the same statement the moment
 * letters began committing on press (see [commitsOnPress]). Before that, a tap that strayed a
 * pixel over its own boundary and lifted still typed on release and nothing was lost. Now the
 * same stray press revokes the letter already on screen and hands the gesture to the decoder,
 * which either finds nothing -- a dropped character -- or commits some short unrelated reading
 * over the text near the caret. That is zap's "it sometimes doesnt register a character if I
 * type too fast" and "its randomly deleting chars" (roadmap 4J.1): fast typing is exactly what
 * produces the off-centre press that lands near an edge and rolls over it.
 *
 * Measured from the *edge*, not from the touch point, so a press that lands mid-key still has
 * to cross the whole way out. That asymmetry is right: a tap already sitting on the boundary
 * is the genuinely ambiguous one, and it is the only one this has to be careful about.
 *
 * ponytail: 10dp is a guess with no device behind it. It costs a real glide a couple of pointer
 * samples and nothing else, because the stroke is anchored at the key's centre either way, so
 * erring high is the cheap direction. If a device pass still drops characters, raise it (16dp
 * is still under half a key width); if real glides start failing to trigger, lower it toward
 * the 6dp [ALTERNATE_DRAG_SLOP_DP] already uses to absorb the same jitter.
 */
internal const val GLIDE_ESCAPE_DP = 10f

/**
 * Whether the finger has left this key far enough that a tap is no longer a plausible reading.
 *
 * Pure, so the dead zone is assertable without a pointer harness -- which matters here more
 * than usual, because the thing it prevents is invisible until someone types fast on a phone.
 *
 * [bounds] and [point] are both in root coordinates. An [escapePx] of zero collapses this back
 * to the bare bounds test it replaced.
 */
internal fun glideEscaped(
    bounds: Rect,
    point: Offset,
    escapePx: Float,
): Boolean {
    if (bounds.contains(point)) return false
    val dx = maxOf(bounds.left - point.x, point.x - bounds.right, 0f)
    val dy = maxOf(bounds.top - point.y, point.y - bounds.bottom, 0f)
    return hypot(dx, dy) >= escapePx
}

/**
 * Whether this key puts its character on screen when the finger lands, rather than when it lifts.
 *
 * Every key used to commit on release, because release is the moment a tap is finally
 * distinguishable from a glide and from a hold. That is correct and it is also the whole of
 * the intermittent-lag report: measured on device, the press-to-letter time tracked finger
 * dwell one-for-one (50ms dwell -> 57ms, 300ms -> 306ms) with this keyboard's own share a
 * flat ~5ms. Nothing was stalling. The letter was waiting for the finger, so the lag was
 * whatever the user's own dwell happened to be that keystroke -- which is exactly why it came
 * and went with no pattern anyone could describe.
 *
 * So the letter goes on screen at once and is taken back on the two gestures that turn out
 * not to be a keystroke. That trade is only safe where the take-back is exact:
 *
 * - **Letters only.** A letter goes through `onKeyPressed`, which appends one character to
 *   the word mirror and is therefore reversible. Punctuation and digits go through
 *   `onSymbolCommitted`, which *clears* the mirror -- the word it discarded cannot be
 *   restored, so revoking one would leave the mirror describing text that is not there. That
 *   is the class of desync this codebase sizes destructive edits from the editor to avoid.
 * - **Not the repeat or scrub keys.** Backspace already fires on press through its repeat
 *   loop, and the space bar's press may still become a caret scrub, which must not type.
 *   Both are excluded here as well as by the letter test, because the two facts are
 *   independent and a future key could satisfy one without the other.
 */
internal fun commitsOnPress(
    keyOutput: String,
    longPress: LongPress,
): Boolean =
    isGlideCandidate(keyOutput) &&
        longPress !is LongPress.Repeat &&
        longPress !is LongPress.Scrub

/**
 * Width of one alternates cell, shrunk when the strip would otherwise run off the screen.
 *
 * The strip used to assume every cell could have its full preferred width, and the only thing
 * standing between that and an unreachable cell was a hard six-entry ceiling asserted in a
 * test. Remove the ceiling without this and a long strip is clipped at the screen edge: the
 * cells past the edge are drawn nowhere and, because selection is computed from the same cell
 * width, they cannot be selected either.
 *
 * Pure so the arithmetic is assertable without a screen -- which matters, because the failure
 * it prevents is invisible until someone tries the last cell of a long strip on a narrow phone.
 */
internal fun alternateCellWidthPx(
    optionCount: Int,
    availableWidthPx: Float,
    preferredWidthPx: Float,
): Float {
    if (optionCount <= 0) return preferredWidthPx
    val fits = availableWidthPx / optionCount
    return minOf(preferredWidthPx, fits).coerceAtLeast(1f)
}

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
    alternates: List<String>? = null,
    alternatesDefaultIndex: Int = 0,
): LongPress {
    if (keyOutput == "SPACE") return LongPress.Scrub
    if (keyOutput == "DEL") return LongPress.Repeat
    // The key's own alternates win over every shared table. This is what keeps two keys that
    // type the same character from inheriting each other's hold behaviour -- the digit row and
    // the symbols page both carry "1", and the currency key types the same "$" as digit 4's
    // shifted symbol.
    if (alternates != null && alternates.isNotEmpty()) {
        return LongPress.Alternates(
            alternates,
            alternatesDefaultIndex.coerceIn(0, alternates.lastIndex),
        )
    }
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

    /**
     * The width one cell was actually given, which is not always the preferred width.
     *
     * Read by the strip when it draws. Drawing from the shared constant while selecting from
     * this value is how a long strip ends up highlighting a different cell than the one under
     * the finger, so both sides take the same number from here.
     */
    var cellWidthPx by androidx.compose.runtime.mutableFloatStateOf(1f)
        private set

    val visible: Boolean get() = anchor != null

    fun show(
        options: List<String>,
        anchor: Rect,
        cellWidthPx: Float,
        defaultIndex: Int = 0,
    ) {
        this.options = options
        this.anchor = anchor
        this.cellWidthPx = cellWidthPx.coerceAtLeast(1f)
        // Not always zero. A release with no drag commits whatever is selected here, so this
        // is what decides the "just hold it" character.
        this.selectedIndex = defaultIndex.coerceIn(0, maxOf(0, options.lastIndex))
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
    pressed: MutableState<Boolean>,
    onScrub: (Int, Boolean) -> Unit = { _, _ -> },
    onDeleteWord: () -> Unit = {},
    glide: GlideTracker? = null,
    onGlide: (com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlideStroke) -> Unit = {},
    onRevoke: (String) -> Unit = {},
): Modifier =
    this.pointerInput(keyOutput, longPress, alternates, cellWidthPx, glide) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // The only place in this process that sees a press before anything reacts to it,
            // which is why the latency measurement starts here rather than in handleKeyPress.
            // The event's own timestamp is passed rather than read here: the gap between the
            // two IS the number worth having.
            LatencyTracker.markDown(down.uptimeMillis)
            // Replacing `clickable` also removed the indication it supplied, so keys had no
            // press feedback of any kind while every other control in the app did. A
            // MutableState rather than a callback: an instance is stable and remembered per
            // key, where a lambda parameter would be reallocated on each recomposition and
            // take the whole grid out of skipping.
            pressed.value = true
            try {
                // The keystroke lands here rather than on release. See commitsOnPress for the
                // measurement that moved it, and for why only letters are eligible.
                var committedOnPress = false
                if (commitsOnPress(keyOutput, longPress)) {
                    onCommit(keyOutput)
                    committedOnPress = true
                }
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

                // A glide is the one gesture here that belongs to no single key, and this is
                // where it is separated from a tap and from a hold.
                //
                // The key that received the down keeps ownership rather than handing over to a
                // detector layered across the grid: two detectors both claiming the down event
                // is the exact problem `keyGestures` replaced `clickable` to avoid. What the
                // key cannot know is where the *other* keys are, and that is all the tracker
                // supplies.
                //
                // The test is leaving this key's own bounds, not travelling some number of
                // pixels. A threshold in pixels is a different gesture on a small key than on
                // the space bar, whereas crossing into a neighbour means the same thing
                // everywhere -- and it is precisely the moment a tap stops being a plausible
                // reading of what the finger is doing.
                //
                // With a dead zone on it, and only a dead zone: the edge still decides,
                // GLIDE_ESCAPE_DP only says how far past it the finger has to be before the
                // crossing is believed. Without one, a fast off-centre tap that rolls a pixel
                // over its own boundary is indistinguishable from a glide's first sample, and
                // since v0.1.7.2 that costs the letter already on screen. See GLIDE_ESCAPE_DP.
                //
                // The glide watch runs *inside* the long-press window rather than instead of
                // it, and that is the whole reason this is shaped the way it is.
                //
                // The first version simply took over the gesture for every letter, which made
                // holding a letter commit it as an ordinary tap: long-press stopped producing
                // the corner symbol on all 26 keys. Nothing failed and nothing logged -- the
                // key just typed the wrong thing, which is how a gesture regression hides.
                //
                // So only three outcomes are decided here. The finger left the key, which is a
                // glide. The finger lifted, which is a tap. Or the window elapsed with the
                // finger still on the key, which is a hold and is handed to the existing
                // machinery below untouched.
                var heldPastThreshold = false
                if (glide != null && isGlideCandidate(keyOutput)) {
                    val origin = keyBounds()
                    val escapePx = GLIDE_ESCAPE_DP.dp.toPx()
                    val stirPx = GLIDE_STIR_DP.dp.toPx()
                    var glided = false
                    var lifted = false
                    var travelled = 0f

                    // Whether the finger is doing anything decides how long it is given.
                    //
                    // A stationary finger is a hold and gets [LONG_PRESS_MS], which is short
                    // so the corner symbol arrives promptly. A finger that is moving has not
                    // said what it is yet, and cutting it off at the same moment is what
                    // broke diagonal glides when the hold threshold was shortened: gliding
                    // "ok" crosses from the o key down to the k key, which needs about 190ms
                    // to clear the origin at an ordinary speed, so the watch expired first
                    // and committed o's corner symbol instead of a word. Measured on device,
                    // where it turned "ok" into "{".
                    //
                    // So movement buys time, up to [GLIDE_WATCH_MS]. It cannot make a hold
                    // slow, because reaching the second phase at all requires the finger to
                    // have already moved further than a resting thumb ever wobbles.
                    val stirred =
                        withTimeoutOrNull(LONG_PRESS_MS) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change =
                                    event.changes.firstOrNull() ?: return@withTimeoutOrNull false
                                if (!change.pressed) {
                                    lifted = true
                                    return@withTimeoutOrNull false
                                }
                                travelled += change.positionChange().getDistance()
                                val root = origin.topLeft + change.position
                                if (glideEscaped(origin, root, escapePx)) {
                                    glided = true
                                    glide.begin(origin.center)
                                    glide.move(root)
                                    // Claimed only once this is definitely a glide, so an
                                    // ordinary tap or hold is left entirely alone.
                                    change.consume()
                                    return@withTimeoutOrNull false
                                }
                                if (travelled >= stirPx) return@withTimeoutOrNull true
                            }
                            @Suppress("UNREACHABLE_CODE")
                            false
                        } ?: false

                    if (stirred) {
                        withTimeoutOrNull(GLIDE_WATCH_MS - LONG_PRESS_MS) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    lifted = true
                                    break
                                }
                                val root = origin.topLeft + change.position
                                if (glideEscaped(origin, root, escapePx)) {
                                    glided = true
                                    glide.begin(origin.center)
                                    glide.move(root)
                                    change.consume()
                                    break
                                }
                            }
                        }
                    }

                    if (glided) {
                        // The letter this press already put on screen was the first key of a
                        // stroke, not a keystroke. Taken back before the stroke can finish, so
                        // the word the decoder commits is not prefixed by its own first letter.
                        if (committedOnPress) {
                            committedOnPress = false
                            onRevoke(keyOutput)
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            glide.move(origin.topLeft + change.position)
                            change.consume()
                            if (!change.pressed) {
                                glide.finish()?.let(onGlide)
                                return@awaitEachGesture
                            }
                        }
                        glide.cancel()
                        return@awaitEachGesture
                    }
                    if (lifted) {
                        // Already on screen for an eligible key; this is the lift that only
                        // confirms it.
                        if (!committedOnPress) onCommit(keyOutput)
                        return@awaitEachGesture
                    }
                    // Still down, still on the key: this is a hold, and the window has already
                    // been spent waiting it out. A hold commits the alternate rather than the
                    // key's own character, so the letter shown on press comes back off.
                    if (committedOnPress) {
                        committedOnPress = false
                        onRevoke(keyOutput)
                    }
                    heldPastThreshold = true
                }

                // null  -> the threshold elapsed, this is a hold
                // true  -> released before the threshold, an ordinary tap
                // false -> the gesture was cancelled out from under us
                // Travel during the hold window, tracked only for the key that can use it.
                //
                // `waitForUpOrCancellation` throws the finger's position away, so a drag
                // beginning before the hold threshold elapsed was invisible. "Hold backspace
                // and swipe left" performed as one motion takes well under that threshold,
                // so the gesture looked absent to anyone who did not already know to hold
                // still first and only then drag -- which is why it was reported missing
                // twice against a feature that shipped and was verified on a device by
                // somebody who knew the trick.
                //
                // Only the repeat key reads these, so only the repeat key takes the
                // different wait. This block runs for every key on the board and the
                // ordinary path is deliberately left byte-for-byte as it was.
                var preTravelX = 0f
                var preTravelY = 0f
                val early =
                    if (heldPastThreshold) {
                        // Already established as a hold above; waiting a second full window
                        // would make every long press on a letter take twice as long.
                        null
                    } else if (longPress is LongPress.Repeat) {
                        withTimeoutOrNull(LONG_PRESS_MS) {
                            var releasedCleanly = false
                            while (true) {
                                val change =
                                    awaitPointerEvent().changes.firstOrNull() ?: break
                                val delta = change.positionChange()
                                preTravelX += delta.x
                                preTravelY += delta.y
                                if (!change.pressed) {
                                    releasedCleanly = true
                                    break
                                }
                            }
                            releasedCleanly
                        }
                    } else {
                        withTimeoutOrNull(LONG_PRESS_MS) {
                            waitForUpOrCancellation() != null
                        }
                    }

                when {
                    early == true -> if (!committedOnPress) onCommit(keyOutput)
                    // Cancelled out from under us before it resolved into anything.
                    early == false ->
                        if (committedOnPress) {
                            committedOnPress = false
                            onRevoke(keyOutput)
                        }
                    longPress is LongPress.None -> {
                        // Held, but this key has no hold behaviour: still a keystroke on
                        // release, otherwise resting a moment on a letter would silently
                        // swallow it.
                        if (waitForUpOrCancellation() != null) {
                            if (!committedOnPress) onCommit(keyOutput)
                        } else {
                            if (committedOnPress) {
                                committedOnPress = false
                                onRevoke(keyOutput)
                            }
                        }
                    }
                    longPress is LongPress.Repeat -> {
                        // Character repeat, which a leftward drag promotes to word repeat.
                        //
                        // The repeat clock is left exactly as it was and the drag is read
                        // inside the same wait, rather than the two being separate loops. That
                        // ordering matters: the wait is what already ends the gesture on
                        // lift-off, and a second detector reading the same pointer stream would
                        // have to agree with it about when the press is over.
                        var step = 0
                        // Seeded from the hold window rather than starting at zero, so a
                        // drag that began before the threshold is still the same gesture.
                        var travelX = preTravelX
                        var travelY = preTravelY
                        var accumulator = 0f
                        var wordMode = false
                        val activationPx = WORD_DELETE_ACTIVATION_DP.dp.toPx()
                        val wordStepPx = WORD_DELETE_STEP_DP.dp.toPx()
                        // Checked once before the loop as well as inside it. The loop only
                        // reconsiders on a new pointer event, so a finger that completed its
                        // swipe during the hold window and then stopped moving would sit
                        // there having earned word mode and never being given it.
                        if (wordDeleteActivated(travelX, travelY, activationPx)) {
                            wordMode = true
                            onDeleteWord()
                        }
                        while (true) {
                            // In word mode the timer stops producing deletes entirely: they are
                            // driven by travel below, so the caret follows the finger instead of
                            // running away from it while it is still moving.
                            if (!wordMode) onCommit(keyOutput)
                            val ended =
                                withTimeoutOrNull(repeatIntervalAt(step)) {
                                    // Replaces waitForUpOrCancellation, which threw the
                                    // finger's position away -- the reason the repeat path had
                                    // no drag to read in the first place.
                                    while (true) {
                                        val change =
                                            awaitPointerEvent().changes.firstOrNull()
                                                ?: return@withTimeoutOrNull Unit
                                        val delta = change.positionChange()
                                        travelX += delta.x
                                        travelY += delta.y
                                        if (!wordMode) {
                                            if (wordDeleteActivated(
                                                    travelX,
                                                    travelY,
                                                    activationPx,
                                                )
                                            ) {
                                                wordMode = true
                                                accumulator = 0f
                                                onDeleteWord()
                                                change.consume()
                                            }
                                        } else {
                                            accumulator += delta.x
                                            while (-accumulator >= wordStepPx) {
                                                accumulator += wordStepPx
                                                onDeleteWord()
                                            }
                                            change.consume()
                                        }
                                        if (!change.pressed) return@withTimeoutOrNull Unit
                                    }
                                    @Suppress("UNREACHABLE_CODE")
                                    Unit
                                }
                            if (ended != null) break
                            step++
                        }
                    }
                    longPress is LongPress.Alternates -> {
                        // Same as the hold path above: the strip is about to commit a
                        // different character than the one already on screen.
                        if (committedOnPress) {
                            committedOnPress = false
                            onRevoke(keyOutput)
                        }
                        alternates.show(
                            longPress.options,
                            keyBounds(),
                            cellWidthPx,
                            longPress.defaultIndex,
                        )
                        val origin = keyBounds().left
                        var committed = false
                        // The finger has to actually move before it starts choosing cells.
                        //
                        // This loop used to call moveTo on every pointer event, and the
                        // release is a pointer event -- it carries the finger's position,
                        // which for a hold-and-release has never left the key. So the very
                        // first thing that reached moveTo overwrote defaultIndex with the cell
                        // under the finger, and a plain hold could only ever commit whichever
                        // cell the key sits over.
                        //
                        // Invisible on every digit, because their defaultIndex is 0 and cell 0
                        // is the one under the finger, so both answers agree. The currency key
                        // is the single case where they differ, and it was wrong: the strip
                        // drew "$" highlighted and committed "€". Worse, the TalkBack path
                        // commits options[defaultIndex] directly, so touch and screen reader
                        // produced different characters from the same key -- the exact
                        // inconsistency defaultIndex exists to prevent.
                        val startX = down.position.x
                        val slopPx = ALTERNATE_DRAG_SLOP_DP.dp.toPx()
                        var tracking = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change == null) break
                            if (!tracking && abs(change.position.x - startX) > slopPx) {
                                tracking = true
                            }
                            if (tracking) alternates.moveTo(origin + change.position.x)
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
            } finally {
                // finally, not after the when: every branch above can leave early -- the
                // scrub returns, a cancelled gesture falls through, and any of them can be
                // cancelled by the composition going away mid-press. A key stuck in its
                // pressed colour is worse than no feedback at all.
                pressed.value = false
            }
        }
    }
