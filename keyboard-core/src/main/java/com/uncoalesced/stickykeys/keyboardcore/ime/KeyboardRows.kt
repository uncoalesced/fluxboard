// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.view.inputmethod.EditorInfo
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import com.uncoalesced.stickykeys.keyboardcore.layout.ShiftRendering
import com.uncoalesced.stickykeys.keyboardcore.layout.keyGlyph
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyStyle
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysColors
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import kotlin.math.roundToInt

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
    onDeleteWord: () -> Unit = {},
    glide: GlideTracker? = null,
    onGlide: (com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlideStroke) -> Unit = {},
    onKeyRevoke: (String) -> Unit = {},
    enterAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    edgeInset: Dp = 0.dp,
) {
    val rows =
        remember(keyRows, edgeInset) {
            keyRows.rows.map { RowCells(rowCells(it, edgeInset)) }
        }

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

    // Debug builds only: every finger that lands on the grid, seen before any key reacts. A
    // touch that appears here with no per-press outcome after it never reached a key. The
    // modifier is not attached at all when recording is off, so a release grid is unchanged.
    val recorder = LocalUsageRecorder.current
    val touchObserver =
        if (recorder != null && recorder.enabled) {
            Modifier.pointerInput(recorder) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val fingers = event.changes.count { it.pressed }
                        for (change in event.changes) {
                            if (change.changedToDownIgnoreConsumed()) {
                                recorder.onTouchDown(change.uptimeMillis, fingers)
                            }
                        }
                    }
                }
            }
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(touchObserver)
                .onGloballyPositioned {
                    gridOrigin.value =
                        it.positionInRoot()
                },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            for (cells in rows) {
                KeyRowLayout(
                    cells = cells,
                    edgeInset = edgeInset,
                    // Rows share whatever height is left after the suggestion strip rather than
                    // each claiming a fixed key height. That is what lets the same layout work in
                    // a short landscape window without overflowing it.
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    for (cell in cells.cells) {
                        val keyDef = cell.key
                        if (keyDef.output == KeyboardLayouts.SPACER) {
                            // Only an interior spacer reaches here; one at either end of a row
                            // has already been folded into its neighbour by rowCells.
                            Spacer(modifier = Modifier.fillMaxHeight())
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
                            glide = glide,
                            onGlide = onGlide,
                            keyOutput = keyDef.output,
                            glyph =
                                keyGlyph(
                                    keyOutput = keyDef.output,
                                    displayLabel = keyDef.displayLabel,
                                    shift = shiftRenderingFor(mode),
                                    enterAction = enterAction,
                                ),
                            hint = keyDef.hint,
                            keyAlternates = keyDef.alternates,
                            keyAlternatesDefaultIndex = keyDef.alternatesDefaultIndex,
                            mode = mode,
                            onDeleteWord = onDeleteWord,
                            background = colors.background,
                            foreground = colors.foreground,
                            border = colors.border,
                            haze = colors.haze,
                            alternates = alternates,
                            preferredCellWidthPx = preferredCellPx,
                            availableWidthPx = availableWidthPx,
                            modifier = Modifier.fillMaxHeight(),
                            onKeyPress = onKeyPress,
                            onKeyRevoke = onKeyRevoke,
                            onScrub = onScrub,
                            drawInset = cell.inset,
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

/**
 * The part of a key's cell that is touchable but not painted, at each end.
 *
 * [startDp] and [endDp] come off first: the panel margin, a fixed distance. The fractions are
 * then taken of what remains, because an absorbed half-key spacer is a share of the row rather
 * than a distance. Applied below the gesture modifier, so it shrinks only what is drawn.
 */
@Immutable
internal data class KeyDrawInset(
    val startFraction: Float = 0f,
    val endFraction: Float = 0f,
    val startDp: Dp = 0.dp,
    val endDp: Dp = 0.dp,
) {
    companion object {
        val None = KeyDrawInset()
    }
}

/** Draws the content inside [inset] while reporting the full cell as this node's size. */
internal fun Modifier.drawInset(inset: KeyDrawInset): Modifier =
    if (inset == KeyDrawInset.None) {
        this
    } else {
        layout { measurable, constraints ->
            if (!constraints.hasBoundedWidth) {
                val placeable = measurable.measure(constraints)
                return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            val full = constraints.maxWidth
            val fixedStart = inset.startDp.roundToPx()
            val fixedEnd = inset.endDp.roundToPx()
            val flex = (full - fixedStart - fixedEnd).coerceAtLeast(0)
            val start = fixedStart + (flex * inset.startFraction).roundToInt()
            val end = fixedEnd + (flex * inset.endFraction).roundToInt()
            val inner = (full - start - end).coerceAtLeast(0)
            val placeable =
                measurable.measure(constraints.copy(minWidth = inner, maxWidth = inner))
            layout(full, placeable.height) { placeable.placeRelative(start, 0) }
        }
    }

/** One cell of a key row: the key, the share of the row it occupies, and what is not drawn. */
@Immutable
internal data class RowCell(
    val key: KeyDefinition,
    val weight: Float,
    val inset: KeyDrawInset,
)

/** A row's cells, wrapped for the same stability reason as [KeyboardRows]. */
@Immutable
internal data class RowCells(
    val cells: List<RowCell>,
)

/**
 * Turns a layout row into touch cells that tile the full width with no holes.
 *
 * Two kinds of space used to belong to no key, and a tap on either typed nothing at all
 * (roadmap 4R.1, measured on device: 18dp left of `a`, 15dp right of `l`, and the outer edge of
 * every row):
 *  - The home row's half-key stagger is a spacer at each end. An end spacer is folded into the
 *    key beside it: the key's cell grows by the spacer's weight and the key is drawn inset by
 *    the same share, so it looks exactly as before and owns the space.
 *  - The panel margin, [edgeInset], is given to the first and last cell of every row the same
 *    way. [KeyRowLayout] makes those cells wider by that distance.
 *
 * Interior spacers are left alone. Pure, so the arithmetic is testable without composing.
 */
internal fun rowCells(
    row: List<KeyDefinition>,
    edgeInset: Dp = 0.dp,
): List<RowCell> {
    val keys = row.toMutableList()
    val isSpacer = { k: KeyDefinition -> k.output == KeyboardLayouts.SPACER }
    var leading = 0f
    var trailing = 0f
    if (keys.size > 1 && isSpacer(keys.first()) && !isSpacer(keys[1])) {
        leading = keys.removeAt(0).weight
    }
    if (keys.size > 1 && isSpacer(keys.last()) && !isSpacer(keys[keys.lastIndex - 1])) {
        trailing = keys.removeAt(keys.lastIndex).weight
    }
    return keys.mapIndexed { i, key ->
        if (isSpacer(key)) return@mapIndexed RowCell(key, key.weight, KeyDrawInset.None)
        val lead = if (i == 0) leading else 0f
        val trail = if (i == keys.lastIndex) trailing else 0f
        val weight = key.weight + lead + trail
        val inset =
            KeyDrawInset(
                startFraction = lead / weight,
                endFraction = trail / weight,
                startDp = if (i == 0) edgeInset else 0.dp,
                endDp = if (i == keys.lastIndex) edgeInset else 0.dp,
            )
        RowCell(key, weight, inset)
    }
}

/**
 * Lays out one row of [cells] across the full width.
 *
 * Weights share the width left after [edgeInset] at each end, and that inset is then added to
 * the first and last cell, so every key lands on exactly the pixel it did when the margin was
 * padding around the grid. Cell edges are cumulative rather than per-cell rounded, so the row
 * tiles with no gap between neighbours for a tap to fall into.
 */
@Composable
private fun KeyRowLayout(
    cells: RowCells,
    edgeInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height =
            if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val edge = edgeInset.roundToPx()
        val inner = (width - 2 * edge).coerceAtLeast(0)
        val total = cells.cells.sumOf { it.weight.toDouble() }.takeIf { it > 0.0 } ?: 1.0
        var cumulative = 0.0
        var left = 0
        val placed =
            measurables.mapIndexed { i, measurable ->
                cumulative += cells.cells[i].weight
                val right =
                    if (i == measurables.lastIndex) {
                        width
                    } else {
                        edge + (inner * cumulative / total).roundToInt()
                    }
                val cellLeft = left
                left = right
                val cellWidth = (right - cellLeft).coerceAtLeast(0)
                cellLeft to measurable.measure(Constraints.fixed(cellWidth, height))
            }
        layout(width, height) {
            placed.forEach { (x, placeable) -> placeable.placeRelative(x, 0) }
        }
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
                .background(palette.surfaceVariant, StickyKeysTheme.shapes.medium),
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
                            StickyKeysTheme.shapes.small,
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
