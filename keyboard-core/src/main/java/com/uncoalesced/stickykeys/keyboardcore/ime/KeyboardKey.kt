// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyGlyph
import com.uncoalesced.stickykeys.keyboardcore.layout.keyGlyph
import com.uncoalesced.stickykeys.keyboardcore.theme.PRESS_COLOR_ANIM_MS
import com.uncoalesced.stickykeys.keyboardcore.theme.PRESS_ELEVATION_SINK
import com.uncoalesced.stickykeys.keyboardcore.theme.PRESS_SCALE
import com.uncoalesced.stickykeys.keyboardcore.theme.PressSpring
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.pressedFill

/**
 * A single key.
 *
 * The drawn glyph and the spoken label are separate on purpose: [displayLabel] is what the
 * eye gets, [accessibleKeyLabel] is what a screen reader gets, and the glyph is stripped
 * from the semantics tree so it cannot leak into the announcement.
 *
 * [onKeyPress] takes the key rather than being a per-key closure so one handler instance can
 * be shared by every key on the board. Passing a `() -> Unit` built at the call site was what
 * defeated skipping: the closure captured unstable values, so it was reallocated on each
 * recomposition and no key ever compared equal to its previous arguments.
 */
@Composable
internal fun KeyboardKey(
    keyOutput: String,
    glyph: KeyGlyph,
    hint: String?,
    mode: KeyboardMode,
    background: Color,
    foreground: Color,
    alternates: KeyAlternatesState,
    preferredCellWidthPx: Float,
    availableWidthPx: Float,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyAlternates: List<String>? = null,
    keyAlternatesDefaultIndex: Int = 0,
    border: Color? = null,
    haze: Color? = null,
    onScrub: (Int, Boolean) -> Unit = { _, _ -> },
    onDeleteWord: () -> Unit = {},
    glide: GlideTracker? = null,
    onGlide: (com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlideStroke) -> Unit = {},
    onKeyRevoke: (String) -> Unit = {},
    drawInset: KeyDrawInset = KeyDrawInset.None,
) {
    val spokenLabel = accessibleKeyLabel(keyOutput)
    val spokenState = accessibleKeyState(keyOutput, mode)
    val longPress =
        remember(keyOutput, hint, keyAlternates, keyAlternatesDefaultIndex) {
            longPressFor(keyOutput, hint, keyAlternates, keyAlternatesDefaultIndex)
        }
    // Sized per key rather than once for the grid: the strip's cell width depends on how many
    // options this particular key offers, and a long strip has to shrink or its last cells
    // land off the screen edge where they can be neither seen nor selected.
    val alternateCellWidthPx =
        remember(longPress, availableWidthPx, preferredCellWidthPx) {
            val count = (longPress as? LongPress.Alternates)?.options?.size ?: 1
            alternateCellWidthPx(count, availableWidthPx, preferredCellWidthPx)
        }
    val keyStyle = StickyKeysTheme.keyStyle
    val pressed = remember { mutableStateOf(false) }

    // Shift latching between off, on and caps lock is the one key colour that changes while
    // the user is looking at it; snapping straight to the accent reads as a glitch.
    //
    // A short tween rather than the default spring. Every key on the board holds two of these,
    // so a mode change starts ~80 animations at once; a spring keeps each of them producing
    // frames while it settles, for a colour change nobody is watching past the first 100ms.
    // A fixed 110ms tween ends when the eye says it has.
    // Press feedback. Replacing `clickable` with a raw pointer state machine also removed the
    // indication it was supplying, so keys were the only interactive surface in the app with
    // no visual response to touch -- every Material button still had one through
    // LocalIndication. Lifting the fill toward the accent is legible on both light and dark
    // palettes and, unlike a ripple, cannot be missed on a key the finger is covering.
    val accent = StickyKeysTheme.colors.primary
    val pressTarget = if (pressed.value) pressedFill(background, accent) else background
    val animatedBackground by
        animateColorAsState(
            pressTarget,
            // Down has to be immediate or the feedback arrives after the character does.
            // Release keeps the tween so the key fades back rather than snapping.
            animationSpec = tween(if (pressed.value) 0 else PRESS_COLOR_ANIM_MS),
            label = "key-background",
        )
    val animatedForeground by
        animateColorAsState(
            foreground,
            animationSpec = tween(PRESS_COLOR_ANIM_MS),
            label = "key-foreground",
        )

    // Depth, alongside the colour rather than instead of it. Colour alone left the key
    // visually flat under the one finger that cannot see it: a fingertip covers the middle of
    // a key, so a fill change is the feedback most likely to be hidden by the thing causing
    // it. Scale reads at the edges, which stay visible.
    val pressScale by
        animateFloatAsState(
            targetValue = if (pressed.value) PRESS_SCALE else 1f,
            animationSpec = PressSpring,
            label = "key-scale",
        )
    // Driven off the theme's own haze radius rather than a constant, so this compresses the
    // shadow a preset asked for instead of imposing one. Gated on `haze` as well as `pressed`
    // so a flat preset -- the AMOLED one, deliberately -- has a target that never changes and
    // therefore animates nothing at all.
    val restingElevation = keyStyle.hazeRadius.value
    val pressElevation by
        animateFloatAsState(
            targetValue =
                if (haze != null && pressed.value) {
                    restingElevation * PRESS_ELEVATION_SINK
                } else {
                    restingElevation
                },
            animationSpec = tween(if (pressed.value) 0 else PRESS_COLOR_ANIM_MS),
            label = "key-elevation",
        )

    // Read at hold time rather than captured, so a relayout between the down event and the
    // long-press threshold cannot anchor the alternates strip to a stale rectangle.
    val bounds = remember { mutableStateOf(Rect.Zero) }
    // The theme's shape scale rather than a private constant. The visual difference is two
    // device-independent pixels; the point is that corner radius becomes a thing a preset can
    // say, which is what stops every theme looking identically sharp.
    val shape = StickyKeysTheme.shapes.medium

    // The gap around the key comes from the user's key-size preference rather than a
    // constant: the cell is fixed by the row layout, so the only way to make a key bigger
    // inside it is to make the gap smaller. See keyPaddingFor for the inversion.
    val keyPadding = keyPaddingFor(LocalImePanelMetrics.current.keyScale, KEY_BASE_PADDING)

    Box(
        modifier =
            modifier
                // First in the chain, so the scale wraps the padding too and the key shrinks
                // toward the centre of its own cell. Applied after the padding it would pull
                // the key's edge away from where the finger came down.
                //
                // The lambda form is load-bearing: it defers the transform to the draw phase,
                // so a press does not invalidate layout and cannot re-fire the
                // onGloballyPositioned below with a transformed rectangle -- which would hand
                // GlideTracker a key six percent smaller than the one on screen.
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                // Measured and touched at the *cell*, drawn inset from it.
                //
                // These two sit above `padding` on purpose, and it is a hit-testing fix
                // rather than tidying. Below the padding they describe the painted key, and
                // the 2dp gap around it then belongs to no key at all. Horizontally that
                // never showed: a key is about 36dp wide, under the 48dp minimum touch
                // target, so Compose quietly expands the touch bounds outward and the gap is
                // covered. Vertically a key is about 52dp, over the minimum, nothing is
                // expanded, and the gap between two rows stayed dead -- a tap landing there
                // produced no letter, no correction and no feedback of any kind. Measured on
                // device at 420dpi: a 9px band at every row boundary, about 6 percent of the
                // grid's height, and it is the only mechanism found so far that drops a tap
                // silently rather than typing the wrong thing.
                //
                // Keeping them together is a correctness requirement, not a preference.
                // `keyGestures` reconstructs root coordinates as `keyBounds().topLeft +
                // change.position`, so the rectangle reported here and the node receiving the
                // pointer have to be the same one. Split them and every glide is decoded
                // against a position off by the padding, which nothing would report.
                //
                // The rectangles handed to GlideTracker are now cells, which tile the board
                // with no gaps, so a path crossing between two keys is still evidence of
                // both instead of falling into a hole in the grid.
                .onGloballyPositioned {
                    bounds.value = it.boundsInRoot()
                    // Re-registered on every layout pass rather than once: the grid changes
                    // shape when the number row is toggled, when the height preference moves
                    // and on rotation, and a stale rectangle would decode glides against the
                    // previous layout without anything looking wrong.
                    if (glide != null && isGlideCandidate(keyOutput)) {
                        glide.register(keyOutput[0], bounds.value)
                    }
                }.keyGestures(
                    keyOutput = keyOutput,
                    longPress = longPress,
                    alternates = alternates,
                    cellWidthPx = alternateCellWidthPx,
                    keyBounds = { bounds.value },
                    onCommit = onKeyPress,
                    pressed = pressed,
                    onScrub = onScrub,
                    onDeleteWord = onDeleteWord,
                    glide = glide,
                    onGlide = onGlide,
                    onRevoke = onKeyRevoke,
                    recorder = LocalUsageRecorder.current,
                )
                // Everything below this line is drawing, and the padding belongs to drawing
                // alone: it is what makes a key look separate from its neighbour without
                // making the space between them untouchable.
                //
                // The draw inset is the same idea one level out. An end key's cell also covers
                // the home row's half-key stagger and the panel margin, so a thumb landing
                // there types the key instead of nothing (roadmap 4R.1); the key is still
                // painted where it always was.
                .drawInset(drawInset)
                .padding(keyPadding)
                // Haze before the fill so it reads as glow behind the key rather than a
                // wash over it. Skipped entirely when off, rather than drawn at zero alpha:
                // a shadow modifier on every key costs a render-node per key whether or not
                // anything is visible.
                .then(
                    haze?.let {
                        Modifier.shadow(
                            // Animated rather than static, so the glow the theme draws
                            // compresses under the finger and settles back on release. A
                            // shadow that never moves is decoration; one that does is the same
                            // press event the scale is reporting, seen from underneath.
                            elevation = pressElevation.dp,
                            shape = shape,
                            ambientColor = it,
                            spotColor = it,
                        )
                    } ?: Modifier,
                ).background(animatedBackground, shape)
                .then(
                    border?.let { Modifier.border(keyStyle.borderWidth, it, shape) }
                        ?: Modifier,
                )
                // pointerInput replaces `clickable`, which also supplied the button role and
                // the click action. Both are restated here rather than lost: a screen reader
                // reaches a key through the semantics action, never through the raw pointer
                // stream, so dropping this would have made every key unusable with TalkBack.
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = spokenLabel
                    if (spokenState != null) {
                        stateDescription = spokenState
                    }
                    onClick(label = "Type") {
                        onKeyPress(keyOutput)
                        true
                    }
                    val held = longPress
                    if (held is LongPress.Alternates) {
                        // A drag-to-choose strip is not operable without sight of it, so the
                        // accessibility path commits the default cell outright -- the same
                        // character a sighted user gets by holding and releasing without
                        // moving. Not `first()`: on the currency key the default sits in the
                        // middle of the strip, and committing the first entry there would give
                        // TalkBack users a different character than everyone else.
                        val default = held.options[held.defaultIndex]
                        onLongClick(label = "Type $default") {
                            onKeyPress(default)
                            true
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // Decoration only, on both of these. Without clearing them the merged node would also
        // announce the raw glyph -- "backspace, backspace" -- or, for the space bar, a single
        // blank character.
        // Drawn only when the user wants corner symbols. The hint is still handed to the
        // gesture machine above, so hiding it changes what the key *looks like* and never what
        // it types -- a long press produces the symbol either way. Withholding the hint instead
        // would have been the obvious implementation and the wrong one: `longPressFor` falls
        // back to the hint for keys with no alternates of their own, so a visual setting would
        // have quietly removed a way of typing.
        //
        // The branch shape matters as much as the condition. A hidden hint must not fall
        // through to the hold-available dot below, or turning the setting off would swap one
        // mark for another on every letter -- the opposite of the quieter board it was asked
        // for. Keys that never had a hint keep their dot exactly as before.
        if (hint != null) {
            if (LocalImePanelMetrics.current.showKeyHints) {
                KeyGlyphContent(
                    glyph = keyGlyph(hint),
                    tint = animatedForeground.copy(alpha = HINT_ALPHA),
                    style = StickyKeysTheme.typography.labelMedium,
                    iconSize = HINT_ICON_SIZE,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 5.dp, top = 2.dp)
                            .clearAndSetSemantics { },
                )
            }
        } else if (longPress is LongPress.Alternates) {
            // A key can carry alternates without carrying a hint glyph -- the punctuation keys
            // do, from PUNCTUATION_ALTERNATES rather than from the layout. Those had a hold
            // behaviour with nothing on screen to suggest it, so it was only ever found by
            // accident. A dot rather than the first alternate: the strip holds up to six
            // characters and printing one of them would imply it is the only one.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp, top = 4.dp)
                        .size(LONG_PRESS_DOT)
                        .background(
                            animatedForeground.copy(alpha = HINT_ALPHA),
                            CircleShape,
                        ).clearAndSetSemantics { },
            )
        }
        KeyGlyphContent(
            glyph = glyph,
            tint = animatedForeground,
            style = StickyKeysTheme.typography.keyboardKey,
            iconSize = KEY_ICON_SIZE,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** The gap around a key at 100% key size. Scaled by the user's preference. */
internal val KEY_BASE_PADDING = 2.dp
private val KEY_ICON_SIZE = 22.dp
private val HINT_ICON_SIZE = 13.dp

/** Corner hints are present but secondary; full-strength they compete with the letter. */
private const val HINT_ALPHA = 0.45f

/** The hold-available marker on keys whose alternates have no printable hint. */
private val LONG_PRESS_DOT = 3.dp

/**
 * Floor for a shrunk key label.
 *
 * Below this the character is smaller than the corner hint beside it, at which point a legible
 * clipped label would have been the better of two bad outcomes.
 */
private val MIN_KEY_LABEL_SIZE = 10.sp

/** Draws either case of [KeyGlyph] so no call site has to branch on it. */
@Composable
internal fun KeyGlyphContent(
    glyph: KeyGlyph,
    tint: Color,
    style: androidx.compose.ui.text.TextStyle,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    when (glyph) {
        is KeyGlyph.Label ->
            Text(
                text = glyph.text,
                color = tint,
                style = style,
                maxLines = 1,
                // Shrink rather than clip. `maxLines = 1` defaults to TextOverflow.Clip, so a
                // label wider than its key silently lost characters off the end -- and the
                // multi-character labels are exactly the ones with no room to spare: "123" and
                // "ABC" sit on a 1.5-weight key, the type scale is a user preference, and `sp`
                // is multiplied again by the system font-size setting on top of that. Three
                // independent multipliers, none of which the key width knows about.
                //
                // Capped at the style's own size, so a label that already fits is drawn at
                // exactly the size it was before this existed and nothing else on the board
                // moves. Only the case that used to be cut short behaves differently.
                autoSize =
                    TextAutoSize.StepBased(
                        minFontSize = MIN_KEY_LABEL_SIZE,
                        maxFontSize = style.fontSize,
                    ),
                modifier = modifier,
            )
        is KeyGlyph.Icon ->
            Icon(
                painter = painterResource(glyph.res),
                contentDescription = null,
                tint = tint,
                modifier = modifier.size(iconSize),
            )
    }
}
