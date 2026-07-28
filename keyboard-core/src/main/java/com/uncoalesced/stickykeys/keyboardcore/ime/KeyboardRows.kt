// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysColors

/**
 * The key grid, in a wrapper Compose can prove is stable.
 *
 * `List<List<KeyDefinition>>` on its own is inferred unstable -- a plain `List` gives the
 * compiler no promise that its contents will not change behind its back -- which makes any
 * composable taking one unskippable no matter how the rest of its parameters look. Wrapping
 * it in an `@Immutable` type is what lets the whole key grid skip while the suggestion strip
 * above it changes on every keystroke.
 */
@Immutable
internal data class KeyboardRows(
    val rows: List<List<KeyDefinition>>,
)

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
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    modeState: androidx.compose.runtime.MutableState<KeyboardMode>,
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

/** The two colours a key is painted with. */
internal data class KeyColors(
    val background: Color,
    val foreground: Color,
)

/**
 * Picks a key's colours, including the latch accent.
 *
 * Pure and separate from the composable so the accent is assertable without rendering:
 * Robolectric cannot capture pixels (`captureToImage` needs a real window surface), and a
 * test that recomputed the expected colour the same way the view does would prove nothing.
 */
internal fun resolveKeyColors(
    keyOutput: String,
    weight: Float,
    mode: KeyboardMode,
    palette: StickyKeysColors,
    hasBackgroundImage: Boolean,
): KeyColors {
    val isSpecialKey = weight > 1f
    val accent = accentForKey(keyOutput, mode)

    val backgroundBase =
        when (accent) {
            KeyAccent.Active -> palette.primary
            KeyAccent.Locked -> palette.primaryVariant
            KeyAccent.None -> if (isSpecialKey) palette.surfaceVariant else palette.surface
        }
    val foreground =
        when (accent) {
            KeyAccent.None ->
                if (isSpecialKey) palette.onSurfaceVariant else palette.onSurface
            else -> palette.onPrimary
        }

    return KeyColors(
        background =
            if (hasBackgroundImage) backgroundBase.copy(alpha = 0.75f) else backgroundBase,
        foreground = foreground,
    )
}

/**
 * Renders every key row.
 *
 * Split out of `TypingKeyboardView` on purpose: that function reads suggestions, undo state,
 * incognito and the active theme, so it is invalidated on essentially every keystroke.
 * Keeping the grid in its own skippable composable means one skipped call per keystroke
 * instead of one recomposition per key.
 */
@Composable
internal fun KeyboardRowsView(
    keyRows: KeyboardRows,
    mode: KeyboardMode,
    palette: StickyKeysColors,
    hasBackgroundImage: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        for (row in keyRows.rows) {
            Row(
                // Rows share whatever height is left after the suggestion strip rather than
                // each claiming a fixed key height. That is what lets the same layout work in
                // a short landscape window without overflowing it.
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (keyDef in row) {
                    val colors =
                        resolveKeyColors(
                            keyOutput = keyDef.output,
                            weight = keyDef.weight,
                            mode = mode,
                            palette = palette,
                            hasBackgroundImage = hasBackgroundImage,
                        )

                    KeyboardKey(
                        keyOutput = keyDef.output,
                        displayLabel = keyDef.displayLabel ?: getDisplayLabel(keyDef.output),
                        mode = mode,
                        background = colors.background,
                        foreground = colors.foreground,
                        modifier = Modifier.weight(keyDef.weight).fillMaxHeight(),
                        onKeyPress = onKeyPress,
                    )
                }
            }
        }
    }
}
