// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.hypot

// The decisions and thresholds behind every key press: when a press commits, when it becomes
// a hold, when it becomes a glide, and how fast a held key repeats. Pure functions and
// constants, so the gesture code in KeyGestures.kt and its tests read the same numbers.

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
