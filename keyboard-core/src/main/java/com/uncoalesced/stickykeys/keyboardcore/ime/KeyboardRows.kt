// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import com.uncoalesced.stickykeys.keyboardcore.layout.ShiftRendering
import com.uncoalesced.stickykeys.keyboardcore.layout.keyGlyph
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyStyle
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysColors
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

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

/**
 * The colours a key is painted with.
 *
 * [border] and [haze] are null when their effect is off, rather than transparent, so the
 * renderer can skip the modifier entirely instead of drawing an invisible one on every key.
 */
internal data class KeyColors(
    val background: Color,
    val foreground: Color,
    val border: Color? = null,
    val haze: Color? = null,
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
    keyStyle: KeyStyle = KeyStyle.Default,
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

    val backgroundOverImage =
        if (hasBackgroundImage) backgroundBase.copy(alpha = 0.75f) else backgroundBase

    // The accent keys keep their palette colour rather than taking the user's fill override.
    // A latched shift that looked identical to every other key would make caps lock invisible,
    // which is a correctness problem dressed as a styling one.
    val fill =
        if (accent == KeyAccent.None) {
            keyStyle.resolveFill(backgroundOverImage)
        } else {
            backgroundOverImage
        }

    return KeyColors(
        background = fill,
        foreground = keyStyle.resolveText(foreground),
        border = keyStyle.resolveBorder(foreground),
        haze = keyStyle.resolveHaze(fill),
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
    onScrub: (Int, Boolean) -> Unit = { _, _ -> },
) {
    // One strip for the whole grid, not one per key. A popup owned by the key that opened it
    // is dismissed by its own pointer-exit the moment the finger slides across to choose.
    val alternates = remember { KeyAlternatesState() }
    val keyStyle = StickyKeysTheme.keyStyle
    val density = LocalDensity.current
    val preferredCellPx = with(density) { ALTERNATE_CELL_WIDTH.toPx() }
    // The strip may not get its preferred cell width. A long one has to shrink to stay on
    // screen, and both drawing and selection then have to use the shrunk value.
    val availableWidthPx =
        with(density) {
            LocalConfiguration.current.screenWidthDp.dp
                .toPx()
        }
    val gridOrigin = remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier =
            modifier.fillMaxWidth().onGloballyPositioned {
                gridOrigin.value =
                    it.positionInRoot()
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            for (row in keyRows.rows) {
                Row(
                    // Rows share whatever height is left after the suggestion strip rather than
                    // each claiming a fixed key height. That is what lets the same layout work in
                    // a short landscape window without overflowing it.
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (keyDef in row) {
                        if (keyDef.output == KeyboardLayouts.SPACER) {
                            // Width only: the home row is inset by half a key at each end, and
                            // a spacer expresses that without the row needing its own layout.
                            Spacer(modifier = Modifier.weight(keyDef.weight).fillMaxHeight())
                            continue
                        }

                        val colors =
                            resolveKeyColors(
                                keyOutput = keyDef.output,
                                weight = keyDef.weight,
                                mode = mode,
                                palette = palette,
                                hasBackgroundImage = hasBackgroundImage,
                                keyStyle = keyStyle,
                            )

                        KeyboardKey(
                            keyOutput = keyDef.output,
                            glyph =
                                keyGlyph(
                                    keyOutput = keyDef.output,
                                    displayLabel = keyDef.displayLabel,
                                    shift = shiftRenderingFor(mode),
                                ),
                            hint = keyDef.hint,
                            keyAlternates = keyDef.alternates,
                            keyAlternatesDefaultIndex = keyDef.alternatesDefaultIndex,
                            mode = mode,
                            background = colors.background,
                            foreground = colors.foreground,
                            border = colors.border,
                            haze = colors.haze,
                            alternates = alternates,
                            preferredCellWidthPx = preferredCellPx,
                            availableWidthPx = availableWidthPx,
                            modifier = Modifier.weight(keyDef.weight).fillMaxHeight(),
                            onKeyPress = onKeyPress,
                            onScrub = onScrub,
                        )
                    }
                }
            }
        }

        AlternatesStrip(
            state = alternates,
            gridOrigin = gridOrigin.value,
            palette = palette,
        )
    }
}

/** Width of one choice in the alternates strip; also the drag step that selects it. */
private val ALTERNATE_CELL_WIDTH = 40.dp

/** Which shift artwork the current mode calls for. */
internal fun shiftRenderingFor(mode: KeyboardMode): ShiftRendering =
    when (mode) {
        KeyboardMode.LETTERS_UPPER -> ShiftRendering.LATCHED
        KeyboardMode.LETTERS_CAPS_LOCK -> ShiftRendering.LOCKED
        else -> ShiftRendering.OFF
    }

/**
 * The hold-to-choose strip, drawn inside the keyboard rather than in a [Popup].
 *
 * A real popup window would be a second window layered over the IME window, which on several
 * OEM builds is placed behind the keyboard or clipped to the app below it. Drawing in-grid
 * costs the strip the ability to overhang the keyboard's top edge, which is why it is
 * anchored below the key when there is no room above.
 */
@Composable
private fun AlternatesStrip(
    state: KeyAlternatesState,
    gridOrigin: Offset,
    palette: StickyKeysColors,
) {
    val anchor = state.anchor ?: return
    val options = state.options
    if (options.isEmpty()) return

    val density = LocalDensity.current
    // Taken from the state, not from the constant. The gesture machine may have had to shrink
    // the cells to fit a long strip on screen, and drawing at the preferred width while
    // selecting at the shrunk one would highlight a different cell than the finger is over.
    val cellWidthPx = state.cellWidthPx
    val cellWidth = with(density) { cellWidthPx.toDp() }
    val stripHeight = 44.dp
    val stripWidthPx = cellWidthPx * options.size

    // Keep the strip on screen when the originating key is near either edge.
    val rawLeft = anchor.left - gridOrigin.x
    val maxLeft =
        with(density) { (LocalConfiguration.current.screenWidthDp.dp).toPx() } - stripWidthPx
    val left = rawLeft.coerceIn(0f, maxOf(0f, maxLeft))
    val above = anchor.top - gridOrigin.y - with(density) { stripHeight.toPx() }
    val top = if (above >= 0f) above else anchor.bottom - gridOrigin.y

    Row(
        modifier =
            Modifier
                .offset { IntOffset(left.toInt(), top.toInt()) }
                .height(stripHeight)
                .background(palette.surfaceVariant, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == state.selectedIndex
            Box(
                modifier =
                    Modifier
                        .width(cellWidth)
                        .fillMaxHeight()
                        .padding(3.dp)
                        .background(
                            if (selected) palette.primary else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    color = if (selected) palette.onPrimary else palette.onSurfaceVariant,
                    style = StickyKeysTheme.typography.keyboardKey,
                )
            }
        }
    }
}
