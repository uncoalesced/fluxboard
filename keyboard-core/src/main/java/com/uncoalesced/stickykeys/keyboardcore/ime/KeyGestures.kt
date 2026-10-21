// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.os.SystemClock
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.PressOutcome
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.keyKindOf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.LatencyTracker
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

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
    recorder: UsageRecorder? = null,
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
            // What this press ended as, for the debug build's per-press log. Assigned next to
            // each call that decides it; nothing reads it until the finally block below.
            var outcome = PressOutcome.NOTHING
            try {
                // The keystroke lands here rather than on release. See commitsOnPress for the
                // measurement that moved it, and for why only letters are eligible.
                var committedOnPress = false
                if (commitsOnPress(keyOutput, longPress)) {
                    onCommit(keyOutput)
                    committedOnPress = true
                    outcome = PressOutcome.COMMITTED
                }
                // Scrubbing is decided before the long-press clock, not after it. Gating it on
                // the hold threshold would mean the cursor sat still for the first 350ms of a
                // drag, which reads as the gesture being broken rather than deliberate.
                if (longPress is LongPress.Scrub) {
                    outcome = PressOutcome.SCRUB
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
                            outcome = PressOutcome.NOTHING
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            glide.move(origin.topLeft + change.position)
                            change.consume()
                            if (!change.pressed) {
                                glide.finish()?.let {
                                    outcome = PressOutcome.GLIDE
                                    onGlide(it)
                                }
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
                        outcome = PressOutcome.COMMITTED
                        return@awaitEachGesture
                    }
                    // Still down, still on the key: this is a hold, and the window has already
                    // been spent waiting it out. A hold commits the alternate rather than the
                    // key's own character, so the letter shown on press comes back off.
                    if (committedOnPress) {
                        committedOnPress = false
                        onRevoke(keyOutput)
                        outcome = PressOutcome.NOTHING
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
                    early == true -> {
                        if (!committedOnPress) onCommit(keyOutput)
                        outcome = PressOutcome.COMMITTED
                    }
                    // Cancelled out from under us before it resolved into anything.
                    early == false ->
                        if (committedOnPress) {
                            committedOnPress = false
                            onRevoke(keyOutput)
                            outcome = PressOutcome.NOTHING
                        }
                    longPress is LongPress.None -> {
                        // Held, but this key has no hold behaviour: still a keystroke on
                        // release, otherwise resting a moment on a letter would silently
                        // swallow it.
                        if (waitForUpOrCancellation() != null) {
                            if (!committedOnPress) onCommit(keyOutput)
                            outcome = PressOutcome.COMMITTED
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
                        outcome = PressOutcome.REPEAT
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
                            outcome = PressOutcome.NOTHING
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
                        if (picked != null) {
                            onCommit(picked)
                            outcome = PressOutcome.ALTERNATE
                        }
                    }
                }
            } finally {
                // finally, not after the when: every branch above can leave early -- the
                // scrub returns, a cancelled gesture falls through, and any of them can be
                // cancelled by the composition going away mid-press. A key stuck in its
                // pressed colour is worse than no feedback at all.
                pressed.value = false
                if (recorder != null && recorder.enabled) {
                    recorder.onPressResolved(
                        keyKindOf(keyOutput),
                        outcome,
                        SystemClock.uptimeMillis() - down.uptimeMillis,
                    )
                }
            }
        }
    }
