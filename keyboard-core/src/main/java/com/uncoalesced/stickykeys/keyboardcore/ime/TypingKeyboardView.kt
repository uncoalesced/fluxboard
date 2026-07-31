// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyGlyph
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig
import com.uncoalesced.stickykeys.keyboardcore.layout.keyGlyph
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyStyle
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale
import kotlinx.coroutines.launch

@Composable
fun TypingKeyboardView(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
) {
    // Held as a MutableState rather than a `by` local so the key-press handler can be
    // remembered once and still read the current mode -- capturing the mode by value would
    // reallocate the handler, and with it every key, on each mode change.
    val modeState = remember { mutableStateOf(KeyboardMode.LETTERS_LOWER) }
    val mode = modeState.value
    val activeLayoutConfig by typingViewModel.activeLayout.collectAsState()

    val showNumberRow by typingViewModel.showNumberRow.collectAsState()

    // Collapsed by default: the row is a disclosure, and leaving it open would permanently
    // cost the panel its height for six buttons most sessions never touch.
    var quickExpanded by remember { mutableStateOf(false) }

    // Null when nothing is being announced. Holds the label of the stubbed feature so one
    // notice serves translate, grammar and voice rather than three near-identical ones.
    var comingSoon by remember { mutableStateOf<String?>(null) }

    // Recomputed only when the layout, the letter case or the number-row setting actually
    // changes. Rebuilding this list on every recomposition handed the key grid a fresh List
    // each keystroke, which is enough on its own to stop it skipping.
    val keyRows =
        remember(activeLayoutConfig, mode, showNumberRow) {
            val isLetterMode =
                mode == KeyboardMode.LETTERS_LOWER ||
                    mode == KeyboardMode.LETTERS_UPPER ||
                    mode == KeyboardMode.LETTERS_CAPS_LOCK
            if (isLetterMode) {
                val isUpper =
                    mode == KeyboardMode.LETTERS_UPPER || mode == KeyboardMode.LETTERS_CAPS_LOCK
                val letters =
                    activeLayoutConfig.rows.map { row ->
                        row.map { key ->
                            if (key.output.length == 1 && key.output.first().isLetter()) {
                                key.copy(
                                    output =
                                        if (isUpper) {
                                            key.output.uppercase()
                                        } else {
                                            key.output.lowercase()
                                        },
                                )
                            } else {
                                key
                            }
                        }
                    }
                // Prepended at render time rather than baked into the layout, so toggling the
                // setting does not rewrite the user's saved layout -- and so the symbol pages,
                // whose own first row is already digits, never get a duplicate.
                KeyboardRows(
                    if (showNumberRow) listOf(KeyboardLayouts.numberRow) + letters else letters,
                )
            } else {
                val legacyRows = KeyboardLayouts.getLayoutForMode(mode)
                KeyboardRows(
                    KeyboardLayoutConfig.fromLegacyLayout("_temp", "_temp", legacyRows).rows,
                )
            }
        }

    val suggestions by typingViewModel.suggestions.collectAsState()
    val undoState by typingViewModel.undoState.collectAsState()
    val incognito by typingViewModel.incognito.collectAsState()
    val shouldAutoCapitalize by typingViewModel.shouldAutoCapitalize.collectAsState()
    val activeTheme by typingViewModel.activeTheme.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val panelHeight = rememberImePanelHeight()
    val stripHeight = dimensionResource(R.dimen.suggestion_strip_height)
    val contentPadding = dimensionResource(R.dimen.ime_content_padding)

    // Decoded off the main thread and downsampled to the strip it is drawn into, rather
    // than a full-resolution decode inside remember during composition.
    val bgPath = activeTheme?.backgroundImagePath
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val bgWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val bgHeightPx = with(density) { panelHeight.roundToPx() }
    val bgBitmap = rememberBackgroundBitmap(bgPath, bgWidthPx, bgHeightPx)

    val onKeyPress =
        rememberKeyPressHandler(
            keyboardController = keyboardController,
            typingViewModel = typingViewModel,
            coroutineScope = coroutineScope,
            modeState = modeState,
        )

    // Remembered for the same reason as the key-press handler: an unmemoized lambda here is
    // a fresh instance every recomposition, which hands the space bar a changed parameter and
    // takes the whole grid out of skipping.
    val onScrub =
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
                typingViewModel.onWordFinished()
            }
        }

    // A different field is now focused, possibly in a different app. Every latch is
    // session-scoped: a caps lock set while writing one message must not still be on when the
    // user taps into a search box somewhere else, and neither must the symbols page.
    val inputSession by typingViewModel.inputSession.collectAsState()
    LaunchedEffect(inputSession) {
        modeState.value =
            if (shouldAutoCapitalize) KeyboardMode.LETTERS_UPPER else KeyboardMode.LETTERS_LOWER
        quickExpanded = false
        comingSoon = null
    }

    LaunchedEffect(shouldAutoCapitalize) {
        if (shouldAutoCapitalize && modeState.value == KeyboardMode.LETTERS_LOWER) {
            modeState.value = KeyboardMode.LETTERS_UPPER
        } else if (!shouldAutoCapitalize && modeState.value == KeyboardMode.LETTERS_UPPER) {
            modeState.value = KeyboardMode.LETTERS_LOWER
        }
    }

    StickyKeysTheme(
        darkTheme = activeTheme?.isLight?.not() ?: true,
        typeScale =
            activeTheme?.typeScale ?: TypeScale.MEDIUM,
        customColors = activeTheme?.colors,
        keyStyle = activeTheme?.keyStyle ?: KeyStyle.Default,
    ) {
        // The quick-access toolbar grows the window upward; it never takes space from the
        // keyboard.
        //
        // It used to sit inside the fixed-height panel, below the suggestion strip, with the
        // key grid on `weight(1f)`. That meant the only place its height could come from was
        // the grid: opening the toolbar squashed every key and shifted the whole board down
        // under the user's thumbs. Hoisting it out here, above a panel that keeps its own
        // fixed height, means the extra height is added at the top edge and the grid's
        // position and size are identical in both states.
        Column(modifier = Modifier.fillMaxWidth()) {
            QuickAccessRow(
                expanded = quickExpanded,
                palette = StickyKeysTheme.colors,
                onAction = { action ->
                    typingViewModel.performKeyPressHaptic()
                    when {
                        action.comingSoon -> comingSoon = action.label
                        action.id == "grid" -> keyboardController.switchMode(AppMode.EMOJI_PICKER)
                        action.id == "clipboard" ->
                            keyboardController.switchMode(AppMode.CLIPBOARD)
                        action.id == "textedit" ->
                            keyboardController.switchMode(AppMode.TEXT_EDIT)
                        action.id == "switchime" -> keyboardController.showInputMethodPicker()
                    }
                    if (!action.comingSoon) quickExpanded = false
                },
            )

            // Everything below here is the unchanging part: one fixed height, shared with the
            // sticker and clipboard panels, so switching mode changes what is drawn and never
            // how tall this section is.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(panelHeight),
            ) {
                if (bgBitmap != null) {
                    Image(
                        bitmap = bgBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Fades the image itself. Distinct from imageOverlayOpacity below, which
                        // darkens *over* it for legibility -- so a busy photo can be softened
                        // without also blackening the keyboard.
                        alpha = (activeTheme?.keyStyle ?: KeyStyle.Default).backgroundImageOpacity,
                        modifier = Modifier.matchParentSize(),
                    )
                    // Legibility dark overlay
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(
                                    Color.Black.copy(
                                        alpha =
                                            activeTheme?.imageOverlayOpacity ?: 0.4f,
                                    ),
                                ),
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                if (bgBitmap ==
                                    null
                                ) {
                                    StickyKeysTheme.colors.background
                                } else {
                                    Color.Transparent
                                },
                            ).padding(contentPadding),
                ) {
                    // Suggestion strip; also carries the passive incognito indicator.
                    //
                    // Always present, never conditionally absent. Showing it only when there was
                    // something to show changed the IME window height by the strip's height every
                    // time a word completed, which shoves the host app's content up and down mid
                    // sentence. An empty strip costs one blank row and keeps the window still.
                    val undo = undoState
                    run {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(stripHeight)
                                    .background(StickyKeysTheme.colors.surface)
                                    .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            QuickAccessToggle(
                                expanded = quickExpanded,
                                palette = StickyKeysTheme.colors,
                                onToggle = { quickExpanded = !quickExpanded },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            if (incognito) {
                                // Status only: intentionally not clickable and not a toggle.
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Incognito: typing is not being learned",
                                    tint = StickyKeysTheme.colors.onSurfaceVariant,
                                    modifier =
                                        Modifier
                                            .padding(end = 8.dp)
                                            .size(16.dp)
                                            // The indicator appears and disappears on its own as
                                            // the host field changes; without a live region a
                                            // screen reader user is never told either happened.
                                            .semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement =
                                    if (undo !=
                                        null
                                    ) {
                                        Arrangement.Center
                                    } else {
                                        Arrangement.SpaceEvenly
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (undo != null) {
                                    Text(
                                        text = "Undo: ${undo.original}",
                                        modifier =
                                            Modifier
                                                .semantics {
                                                    contentDescription =
                                                        "Undo autocorrect, restore ${undo.original}"
                                                    liveRegion = LiveRegionMode.Polite
                                                }.clickable(role = Role.Button) {
                                                    // Corrected -> original, one round-trip.
                                                    // +1 covers the trailing space.
                                                    val span = undo.corrected.length + 1
                                                    keyboardController.replaceTextBeforeCursor(
                                                        span,
                                                        undo.original + " ",
                                                    )
                                                    typingViewModel.onUndoApplied()
                                                }.padding(8.dp),
                                        color = StickyKeysTheme.colors.primary,
                                        style = StickyKeysTheme.typography.labelLarge,
                                    )
                                } else {
                                    suggestions.forEach { suggestion ->
                                        Text(
                                            text = suggestion,
                                            modifier =
                                                Modifier
                                                    .semantics {
                                                        contentDescription =
                                                            "Suggestion: $suggestion"
                                                    }.clickable(role = Role.Button) {
                                                        // Read the length and apply the swap in the same
                                                        // synchronous step, so nothing can be typed in
                                                        // between and shift what gets deleted.
                                                        keyboardController.replaceTextBeforeCursor(
                                                            typingViewModel.getCurrentWord().length,
                                                            "$suggestion ",
                                                        )
                                                        typingViewModel.onSuggestionSelected(
                                                            suggestion,
                                                        )
                                                    }.padding(8.dp),
                                            color = StickyKeysTheme.colors.onSurface,
                                            style = StickyKeysTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }

                            // Voice input sits here, at the strip's right edge, rather than in
                            // the quick-access row: it is reached for mid-sentence, and hiding
                            // it behind a disclosure toggle would cost two taps every time.
                            Box(
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        // Drawn at 28dp to fit the 40dp strip; the touch
                                        // target is expanded to the 48dp minimum without
                                        // changing what is drawn.
                                        .minimumInteractiveComponentSize()
                                        .clickable(role = Role.Button) {
                                            comingSoon = "Voice input"
                                        }.semantics {
                                            contentDescription = "Voice input, coming soon"
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_key_mic),
                                    contentDescription = null,
                                    tint = StickyKeysTheme.colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    KeyboardRowsView(
                        keyRows = keyRows,
                        mode = mode,
                        palette = StickyKeysTheme.colors,
                        hasBackgroundImage = bgBitmap != null,
                        onKeyPress = onKeyPress,
                        modifier = Modifier.weight(1f),
                        onScrub = onScrub,
                    )
                }

                // Drawn last so it sits over the keys. Tapping it dismisses.
                comingSoon?.let { label ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ComingSoonNotice(
                            label = label,
                            palette = StickyKeysTheme.colors,
                            onDismiss = { comingSoon = null },
                        )
                    }
                }
            }
        }
    }
}

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
    alternateCellWidthPx: Float,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    border: Color? = null,
    haze: Color? = null,
    onScrub: (Int, Boolean) -> Unit = { _, _ -> },
) {
    val spokenLabel = accessibleKeyLabel(keyOutput)
    val spokenState = accessibleKeyState(keyOutput, mode)
    val longPress = remember(keyOutput, hint) { longPressFor(keyOutput, hint) }
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
    val pressTarget =
        if (pressed.value) {
            // Blended rather than composited: the fill is usually opaque, so compositing the
            // accent behind it would change nothing. Alpha is carried over from the original
            // so a deliberately translucent key stays translucent while pressed.
            lerp(background, accent, PRESS_BLEND).copy(alpha = background.alpha)
        } else {
            background
        }
    val animatedBackground by
        animateColorAsState(
            pressTarget,
            // Down has to be immediate or the feedback arrives after the character does.
            // Release keeps the tween so the key fades back rather than snapping.
            animationSpec = tween(if (pressed.value) 0 else KEY_COLOR_ANIM_MS),
            label = "key-background",
        )
    val animatedForeground by
        animateColorAsState(
            foreground,
            animationSpec = tween(KEY_COLOR_ANIM_MS),
            label = "key-foreground",
        )

    // Read at hold time rather than captured, so a relayout between the down event and the
    // long-press threshold cannot anchor the alternates strip to a stale rectangle.
    val bounds = remember { mutableStateOf(Rect.Zero) }
    val shape = RoundedCornerShape(KEY_CORNER_RADIUS)

    Box(
        modifier =
            modifier
                .padding(2.dp)
                .onGloballyPositioned { bounds.value = it.boundsInRoot() }
                // Haze before the fill so it reads as glow behind the key rather than a
                // wash over it. Skipped entirely when off, rather than drawn at zero alpha:
                // a shadow modifier on every key costs a render-node per key whether or not
                // anything is visible.
                .then(
                    haze?.let {
                        Modifier.shadow(
                            elevation = keyStyle.hazeRadius,
                            shape = shape,
                            ambientColor = it,
                            spotColor = it,
                        )
                    } ?: Modifier,
                ).background(animatedBackground, shape)
                .then(
                    border?.let { Modifier.border(keyStyle.borderWidth, it, shape) }
                        ?: Modifier,
                ).keyGestures(
                    keyOutput = keyOutput,
                    longPress = longPress,
                    alternates = alternates,
                    cellWidthPx = alternateCellWidthPx,
                    keyBounds = { bounds.value },
                    onCommit = onKeyPress,
                    pressed = pressed,
                    onScrub = onScrub,
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
                        // accessibility path commits the first alternate outright.
                        onLongClick(label = "Type ${held.options.first()}") {
                            onKeyPress(held.options.first())
                            true
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // Decoration only, on both of these. Without clearing them the merged node would also
        // announce the raw glyph -- "backspace, backspace" -- or, for the space bar, a single
        // blank character.
        if (hint != null) {
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

/** Key colour transition length. Short enough that 80 concurrent ones stay cheap. */
private const val KEY_COLOR_ANIM_MS = 110

/** How far a pressed key moves toward the accent. Visible under a fingertip, not garish. */
private const val PRESS_BLEND = 0.3f

/** Corner radius shared by every key, matching the reference's rounded caps. */
private val KEY_CORNER_RADIUS = 6.dp
private val KEY_ICON_SIZE = 22.dp
private val HINT_ICON_SIZE = 13.dp

/** Corner hints are present but secondary; full-strength they compete with the letter. */
private const val HINT_ALPHA = 0.45f

/** The hold-available marker on keys whose alternates have no printable hint. */
private val LONG_PRESS_DOT = 3.dp

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

internal fun handleKeyPress(
    keyLabel: String,
    controller: KeyboardController,
    currentMode: KeyboardMode,
    viewModel: TypingViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    setMode: (KeyboardMode) -> Unit,
) {
    if (keyLabel == KeyboardLayouts.SPACER) return
    viewModel.performKeyPressHaptic()
    when (keyLabel) {
        "SHIFT" -> {
            setMode(
                when (currentMode) {
                    KeyboardMode.LETTERS_LOWER -> KeyboardMode.LETTERS_UPPER
                    KeyboardMode.LETTERS_UPPER -> KeyboardMode.LETTERS_CAPS_LOCK
                    KeyboardMode.LETTERS_CAPS_LOCK -> KeyboardMode.LETTERS_LOWER
                    else -> KeyboardMode.LETTERS_LOWER
                },
            )
        }
        "SYMBOLS_SHIFT" -> {
            setMode(
                if (currentMode == KeyboardMode.SYMBOLS) {
                    KeyboardMode.SYMBOLS_SHIFTED
                } else {
                    KeyboardMode.SYMBOLS
                },
            )
        }
        "SYMBOLS" -> setMode(KeyboardMode.SYMBOLS)
        "ABC" -> setMode(KeyboardMode.LETTERS_LOWER)
        // One tap from the emoji key straight into the unified picker: no menu,
        // no intermediate panel.
        "STICKERS" -> controller.switchMode(AppMode.EMOJI_PICKER)
        "CLIPBOARD" -> controller.switchMode(AppMode.CLIPBOARD)
        "DEL" -> {
            viewModel.onDelete()
            controller.sendDelete()
        }
        "ENTER" -> {
            viewModel.onWordFinished()
            controller.sendEnter()
            // Enter both finishes what was typed and starts something new -- a sent message,
            // or a fresh line. Latched shift and caps lock used to survive that, so the next
            // message began in whatever case the last one ended in, and a caps lock set for
            // one word stayed on across everything after it. Symbol pages are dropped for the
            // same reason: nobody starts a new message on the symbols page on purpose.
            viewModel.onSentenceStarted()
            // Read the flag rather than leaving this to the auto-capitalize effect: that
            // effect only re-runs when its key *changes*, so if the flag was already true
            // nothing would fire and the board would sit in lower case at a sentence start.
            setMode(
                if (viewModel.shouldAutoCapitalize.value) {
                    KeyboardMode.LETTERS_UPPER
                } else {
                    KeyboardMode.LETTERS_LOWER
                },
            )
        }
        "SPACE" -> {
            val typedWord = viewModel.getCurrentWord()
            // Commit the space FIRST, synchronously, so key order can never invert.
            // Waiting on the autocorrect lookup here used to let a following letter
            // commit before the space ("a b" arriving as "ab ").
            controller.commitText(" ")
            val token = viewModel.onSpacePressed()

            coroutineScope.launch {
                val corrected = viewModel.getAutoCorrectionFor(typedWord)
                // Only rewrite if nothing else touched the text meanwhile -- otherwise
                // the replace would eat characters the user typed during the lookup.
                if (corrected != null && viewModel.isCurrent(token)) {
                    // Replace "<typedWord> " with "<corrected> " in one round-trip.
                    controller.replaceTextBeforeCursor(typedWord.length + 1, "$corrected ")
                    viewModel.onAutoCorrected(typedWord, corrected)
                } else {
                    viewModel.onWordAccepted(typedWord)
                }
            }
        }
        else -> {
            val isLetter = keyLabel.length == 1 && keyLabel.first().isLetter()
            if (isLetter) {
                viewModel.onKeyPressed(keyLabel)
            } else {
                viewModel.onWordFinished()
                viewModel.onSymbolCommitted(keyLabel)
            }

            controller.commitText(keyLabel)
            if (currentMode == KeyboardMode.LETTERS_UPPER) {
                setMode(KeyboardMode.LETTERS_LOWER)
            }
        }
    }
}
