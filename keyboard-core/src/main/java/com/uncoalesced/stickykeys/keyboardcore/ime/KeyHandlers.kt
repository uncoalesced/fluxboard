// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlideStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// The remembered event handlers the key grid is wired with. Each is built once and handed to
// every key, so each has to stay `remember`ed with the keys it has here: a lambda rebuilt per
// recomposition takes the whole grid out of skipping, which KeyboardRecompositionTest measures.
// What a press actually does lives in KeyPressHandling.kt.

/**
 * The key press handler, allocated once.
 *
 * Building this lambda inline at the call site meant a fresh `Function0` per key per
 * recomposition: it captures the controller, the ViewModel and the coroutine scope, all of
 * which Compose treats as unstable, so the compiler could not memoize it. A new lambda
 * instance never compares equal to the previous one, so every key saw a "changed" parameter
 * and recomposed -- which is why the whole keyboard rebuilt on each keystroke despite
 * [KeyboardKey] already being marked skippable.
 *
 * The mode is read through [modeState] rather than captured by value, so the handler stays
 * one instance for the life of the keyboard while still seeing the current mode.
 */
@Composable
internal fun rememberKeyPressHandler(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
    coroutineScope: CoroutineScope,
    modeState: MutableState<KeyboardMode>,
): (String) -> Unit =
    remember(keyboardController, typingViewModel, coroutineScope, modeState) {
        { key: String ->
            handleKeyPress(
                key,
                keyboardController,
                modeState.value,
                typingViewModel,
                coroutineScope,
            ) { newMode -> modeState.value = newMode }
        }
    }

/**
 * The counterpart to [rememberKeyPressHandler], for a press that turned out not to be a
 * keystroke.
 *
 * Remembered for exactly the reason that one is: it is captured by every key's gesture
 * modifier, so a fresh instance per recomposition would hand all of them a changed parameter
 * and take the whole grid out of skipping -- the invariant `KeyboardRecompositionTest`
 * measures.
 */
@Composable
internal fun rememberKeyRevokeHandler(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
): (String) -> Unit =
    remember(keyboardController, typingViewModel) {
        { key: String ->
            // Sized from the key itself rather than from the editor, unlike every other
            // destructive edit here. This one is not a guess about what is in front of the
            // caret: this keyboard put that character there microseconds ago and the gesture
            // that would have let anything else move has not finished yet.
            keyboardController.deleteBefore(key.length)
            typingViewModel.onKeyRevoked(key)
        }
    }

/**
 * Backspace held and dragged left: delete by words instead of by characters.
 *
 * Hoisted and remembered like every other key handler here. A lambda rebuilt per
 * recomposition is captured by every key's gesture modifier and takes the whole grid out
 * of skipping, which is the invariant KeyboardRecompositionTest measures.
 */
@Composable
internal fun rememberDeleteWordHandler(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
): () -> Unit =
    remember(keyboardController, typingViewModel) {
        {
            // Read the editor now rather than trusting the in-memory word mirror. This is
            // a destructive edit, and the mirror is only correct while this keyboard is
            // the sole editor of the field.
            val before = keyboardController.textBeforeCursor(MAX_WORD_DELETE_LOOKBEHIND)
            val length = wordDeleteLength(before)
            if (length > 0) {
                keyboardController.deleteBefore(length)
                // The tracked word is gone along with the text, and anything downstream
                // still holding it would size its next edit against text that no longer
                // exists. Abandoned rather than finished: the user is deleting it, which
                // is the clearest possible signal not to learn it.
                typingViewModel.onWordAbandoned()
                typingViewModel.performKeyPressHaptic()
            }
            Unit
        }
    }

/**
 * Space-bar scrub: one caret step per call, in the direction given.
 *
 * Remembered for the same reason as the key-press handler: an unmemoized lambda here is
 * a fresh instance every recomposition, which hands the space bar a changed parameter and
 * takes the whole grid out of skipping.
 */
@Composable
internal fun rememberScrubHandler(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
): (Int, Boolean) -> Unit =
    remember(keyboardController, typingViewModel) {
        { direction: Int, withHaptic: Boolean ->
            if (withHaptic) typingViewModel.performKeyPressHaptic()
            // moveCursor, never a DPAD key event. An arrow the editor cannot consume --
            // which is exactly what a scrub run to either end of the text produces -- is
            // handed to the host window's focus search, and the text field loses focus.
            // That is why the drag worked but releasing it left the field.
            keyboardController.moveCursor(
                if (direction > 0) CursorMove.RIGHT else CursorMove.LEFT,
            )
            // Whatever word was being tracked is no longer under the caret, so the
            // autocorrect buffer has to be dropped -- otherwise the next space would
            // rewrite text somewhere else entirely.
            //
            // Abandoned, not finished. This runs once per caret step, and onWordFinished
            // *learns* what it clears, so scrubbing out of the middle of a word was
            // writing the fragment under the caret into the personal dictionary -- where,
            // after two sightings, PredictionEngine starts treating it as a word the user
            // means and stops correcting it.
            typingViewModel.onWordAbandoned()
        }
    }

/**
 * Hoisted for the same reason every other handler here is: an unmemoized lambda handed to
 * every key is a fresh instance per recomposition, which takes the grid out of skipping.
 */
@Composable
internal fun rememberGlideHandler(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
    coroutineScope: CoroutineScope,
    modeState: MutableState<KeyboardMode>,
): (GlideStroke) -> Unit =
    remember(keyboardController, typingViewModel, coroutineScope) {
        { stroke: GlideStroke ->
            coroutineScope.launch {
                val candidates = typingViewModel.decodeGlide(stroke)
                val decoded = candidates.firstOrNull() ?: return@launch
                // The decoder only ever returns lower case -- the dictionary is lower case
                // and the tracker lowercases every key it records -- so the case the user
                // actually asked for is applied here. Read from the flow rather than the
                // composable's snapshot of it: this lambda is remembered, so a captured
                // local would still hold whatever the value was when it was built.
                val atSentenceStart = typingViewModel.shouldAutoCapitalize.value
                val word = glideCase(decoded, modeState.value, atSentenceStart)
                val alternatives =
                    candidates.map { glideCase(it, modeState.value, atSentenceStart) }
                // A glide replaces nothing -- it starts a new word -- so whatever partial
                // word the finger crossed on the way is dropped rather than deleted. The
                // keys were never committed; only the tracker saw them.
                typingViewModel.onWordAbandoned()
                keyboardController.commitText("$word ")
                typingViewModel.onGlideCommitted(word, alternatives)
            }
            Unit
        }
    }

/**
 * Hoisted rather than built at the call site, for both of the reasons this file already
 * hoists handlers: an unmemoized lambda per suggestion is a fresh instance on every
 * recomposition, and the body is long enough that inlining it eight levels deep inside the
 * strip pushed it past the line limit with nothing but indentation.
 *
 * The span is read from the editor, not from the keyboard's own running copy of the word.
 * That copy is a local mirror, correct only while this keyboard is the sole editor -- after
 * a caret tap or a paste, its length deleted the wrong span of the user's text. The read
 * and the replace happen in one synchronous step, so nothing can be typed in between and
 * shift what gets deleted.
 */
@Composable
internal fun rememberSuggestionTapHandler(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
): (String) -> Unit =
    remember(keyboardController, typingViewModel) {
        { suggestion: String ->
            // After a glide the strip holds the readings that lost, and the text already
            // contains the winner plus its space -- so the span to replace is what the
            // glide wrote, not the word under the caret, which is empty there.
            val glided = typingViewModel.consumeGlideCommit()
            val span =
                if (glided != null) glided.length + 1 else currentWordSpan(keyboardController)
            keyboardController.replaceTextBeforeCursor(span, "$suggestion ")
            typingViewModel.onSuggestionSelected(suggestion)
        }
    }
