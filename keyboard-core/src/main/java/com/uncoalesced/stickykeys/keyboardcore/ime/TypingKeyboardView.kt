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
import androidx.compose.ui.platform.LocalContext
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
    val fieldKind by typingViewModel.fieldKind.collectAsState()

    val keyRows =
        remember(activeLayoutConfig, mode, showNumberRow, fieldKind) {
            if (fieldKind == FieldKind.PIN) {
                return@remember KeyboardRows(KeyboardLayouts.pinRows)
            }
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
                //
                // While shift is armed the row shows each digit's shifted symbol, matching the
                // reference and every hardware keyboard. Substituted rather than transformed,
                // so the symbols are real keys: pressing one consumes a one-shot shift through
                // the same branch as any other key, with no special case for the digit row.
                val digits =
                    if (isUpper) KeyboardLayouts.shiftedNumberRow else KeyboardLayouts.numberRow
                KeyboardRows(
                    if (showNumberRow) listOf(digits) + letters else letters,
                )
            } else {
                // Read from the layout, not from a hardcoded table converted on the fly. The
                // symbol pages used to live outside KeyboardLayoutConfig entirely, which is
                // the single reason they could not be remapped, weighted, or given the corner
                // hints and long-press alternates the letter pages have had all along.
                KeyboardRows(KeyboardLayouts.symbolRowsForMode(mode, activeLayoutConfig))
            }
        }

    val suggestions by typingViewModel.suggestions.collectAsState()
    val undoState by typingViewModel.undoState.collectAsState()
    val incognito by typingViewModel.incognito.collectAsState()
    val privateMode by typingViewModel.privateMode.collectAsState()
    val shouldAutoCapitalize by typingViewModel.shouldAutoCapitalize.collectAsState()
    val activeTheme by typingViewModel.activeTheme.collectAsState()
    val context = LocalContext.current

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
                //
                // Abandoned, not finished. This runs once per caret step, and onWordFinished
                // *learns* what it clears, so scrubbing out of the middle of a word was
                // writing the fragment under the caret into the personal dictionary -- where,
                // after two sightings, PredictionEngine starts treating it as a word the user
                // means and stops correcting it.
                typingViewModel.onWordAbandoned()
            }
        }

    // Hoisted rather than built at the call site, for both of the reasons this file already
    // hoists handlers: an unmemoized lambda per suggestion is a fresh instance on every
    // recomposition, and the body is long enough that inlining it eight levels deep inside the
    // strip pushed it past the line limit with nothing but indentation.
    //
    // The span is read from the editor, not from the keyboard's own running copy of the word.
    // That copy is a local mirror, correct only while this keyboard is the sole editor -- after
    // a caret tap or a paste, its length deleted the wrong span of the user's text. The read
    // and the replace happen in one synchronous step, so nothing can be typed in between and
    // shift what gets deleted.
    // One tracker for the whole grid, remembered: every key registers its rectangle into it,
    // and a reallocated instance would lose the map mid-gesture.
    val glideTracker = remember { GlideTracker() }
    val glideEnabled by typingViewModel.glideTypingEnabled.collectAsState()

    // Hoisted for the same reason every other handler here is: an unmemoized lambda handed to
    // every key is a fresh instance per recomposition, which takes the grid out of skipping.
    val onGlide =
        remember(keyboardController, typingViewModel, coroutineScope) {
            { stroke: com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlideStroke ->
                coroutineScope.launch {
                    val candidates = typingViewModel.decodeGlide(stroke)
                    val word = candidates.firstOrNull() ?: return@launch
                    // A glide replaces nothing -- it starts a new word -- so whatever partial
                    // word the finger crossed on the way is dropped rather than deleted. The
                    // keys were never committed; only the tracker saw them.
                    typingViewModel.onWordAbandoned()
                    keyboardController.commitText("$word ")
                    typingViewModel.onGlideCommitted(word, candidates)
                }
                Unit
            }
        }

    val onSuggestionTap =
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

    // A different field is now focused, possibly in a different app. Every latch is
    // session-scoped: a caps lock set while writing one message must not still be on when the
    // user taps into a search box somewhere else, and neither must the symbols page.
    val inputSession by typingViewModel.inputSession.collectAsState()
    LaunchedEffect(inputSession, fieldKind) {
        // The field's own type wins over the letter-case reset. Both of these effects used to
        // force a LETTERS_* mode unconditionally, which is exactly what would have flipped a
        // numeric pad back to QWERTY the instant it was shown -- a keypad that looks right in
        // a screenshot and is gone by the time a finger reaches it.
        modeState.value =
            when {
                fieldKind == FieldKind.PIN -> KeyboardMode.PIN
                shouldAutoCapitalize -> KeyboardMode.LETTERS_UPPER
                else -> KeyboardMode.LETTERS_LOWER
            }
    }

    // Panel state is scoped to the input *session*, and deliberately not to [fieldKind].
    //
    // These two lines used to sit in the effect above, which keys on both. That was correct
    // while every fieldKind change meant a new field -- and stopped being correct the moment
    // the manual privacy switch could change it in place. Observed on device: tapping the
    // padlock flipped NORMAL to PRIVATE, which re-ran that effect and closed the toolbar out
    // from under the finger that had just opened it, so confirming the switch had taken meant
    // reopening the row. Keying the resets on the session alone keeps a new field clearing
    // them without a same-field reclassification doing the same.
    LaunchedEffect(inputSession) {
        quickExpanded = false
        comingSoon = null
    }

    LaunchedEffect(shouldAutoCapitalize) {
        if (fieldKind == FieldKind.PIN) return@LaunchedEffect
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
                privateMode = privateMode,
                onAction = { action ->
                    typingViewModel.performKeyPressHaptic()
                    when {
                        action.comingSoon -> comingSoon = action.label
                        action.id == "grid" -> keyboardController.switchMode(AppMode.EMOJI_PICKER)
                        action.id == "clipboard" ->
                            keyboardController.switchMode(AppMode.CLIPBOARD)
                        action.id == "textedit" ->
                            keyboardController.switchMode(AppMode.TEXT_EDIT)
                        action.id == "private" ->
                            typingViewModel.setPrivateMode(!privateMode)
                        action.id == "switchime" -> keyboardController.showInputMethodPicker()
                    }
                    // The privacy toggle stays put. Collapsing the row on it would hide the
                    // control the instant it was used, so confirming the state means reopening
                    // the toolbar -- and a privacy switch you cannot see is one you stop
                    // trusting. Everything else here navigates away, so it collapses.
                    if (!action.comingSoon && action.id != "private") quickExpanded = false
                },
                onMedia = { action -> MediaTransport.dispatch(context, action) },
                isMediaPlaying = { MediaTransport.isPlaying(context) },
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
                                // The toggle is a labelled control in the quick-access row, and
                                // overloading the indicator with a hidden action would mean the
                                // one thing on screen reporting the state could also silently
                                // change it.
                                //
                                // It says which of the two it is, because they end differently:
                                // the automatic one lifts by itself when the field changes, the
                                // manual one only when the user turns it off, and a user who
                                // cannot tell them apart cannot know whether to go looking.
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription =
                                        if (privateMode) {
                                            "Private mode on: nothing typed is being learned"
                                        } else {
                                            "Incognito: typing is not being learned"
                                        },
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
                                                        onSuggestionTap(suggestion)
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
                        // Null when the feature is off, which is what disables it: the gesture
                        // branch is skipped entirely rather than running and discarding its
                        // result, so a user who turns glide off gets the old pointer handling
                        // back exactly.
                        glide = if (glideEnabled) glideTracker else null,
                        onGlide = onGlide,
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
    preferredCellWidthPx: Float,
    availableWidthPx: Float,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyAlternates: List<String>? = null,
    keyAlternatesDefaultIndex: Int = 0,
    border: Color? = null,
    haze: Color? = null,
    onScrub: (Int, Boolean) -> Unit = { _, _ -> },
    glide: GlideTracker? = null,
    onGlide: (com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlideStroke) -> Unit = {},
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

    // The gap around the key comes from the user's key-size preference rather than a
    // constant: the cell is fixed by the row layout, so the only way to make a key bigger
    // inside it is to make the gap smaller. See keyPaddingFor for the inversion.
    val keyPadding = keyPaddingFor(LocalImePanelMetrics.current.keyScale, KEY_BASE_PADDING)

    Box(
        modifier =
            modifier
                .padding(keyPadding)
                .onGloballyPositioned {
                    bounds.value = it.boundsInRoot()
                    // Re-registered on every layout pass rather than once: the grid changes
                    // shape when the number row is toggled, when the height preference moves
                    // and on rotation, and a stale rectangle would decode glides against the
                    // previous layout without anything looking wrong.
                    if (glide != null && isGlideCandidate(keyOutput)) {
                        glide.register(keyOutput[0], bounds.value)
                    }
                }
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
                    glide = glide,
                    onGlide = onGlide,
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

/** Key colour transition length. Short enough that 80 concurrent ones stay cheap. */
private const val KEY_COLOR_ANIM_MS = 110

/** How far a pressed key moves toward the accent. Visible under a fingertip, not garish. */
private const val PRESS_BLEND = 0.3f

/** Corner radius shared by every key, matching the reference's rounded caps. */
private val KEY_CORNER_RADIUS = 6.dp

/** The gap around a key at 100% key size. Scaled by the user's preference. */
internal val KEY_BASE_PADDING = 2.dp
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
            // Timing decides, not position in a cycle. A second tap soon after the first
            // latches caps lock; a later one turns shift back off. See nextShiftMode.
            setMode(nextShiftMode(currentMode, viewModel.consumeShiftTapGap()))
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
            // Whether Enter may finish a word depends on what Enter *is* in this field, and
            // the two cases are opposites rather than shades.
            //
            // Submit-style Enter must not learn. It deliberately does not autocorrect -- there
            // is no safe moment, since before needs a blocking lookup and after is too late in
            // a field that has already sent -- so learning anyway writes an unchecked word to
            // disk. Measured on device: two sends of "teh" put it in the dictionary at
            // frequency 2, after which autocorrect stops fixing it permanently and the
            // suggestion strip starts offering it. In a send-on-enter chat app that is the
            // ordinary typing path, so the damage is routine rather than exotic.
            //
            // Newline Enter has no send and therefore no race, so the word is genuinely
            // finished and may be learned.
            //
            // The correction is *evaluated* and never *applied*. The return key does not
            // rewrite what the user typed, in any field -- that part is settled -- but running
            // the lookup anyway is what stops Enter teaching the dictionary a misspelling. The
            // dictionary learns what the word should have been; the text on screen keeps what
            // the user actually pressed, and they can see it and fix it themselves.
            //
            // No generation token here, unlike the space and punctuation paths. Those guard a
            // *rewrite* against text having moved underneath it. Nothing is rewritten here, and
            // a word the user finished stays a word they finished however much they type next.
            if (viewModel.enterEndsAWord()) {
                val typedWord = currentWordText(controller)
                controller.sendEnter()
                viewModel.onSentenceStarted()
                if (typedWord.isNotBlank()) {
                    coroutineScope.launch {
                        val corrected = viewModel.getAutoCorrectionFor(typedWord)
                        viewModel.onWordAccepted(corrected ?: typedWord)
                    }
                }
                setMode(
                    if (viewModel.shouldAutoCapitalize.value) {
                        KeyboardMode.LETTERS_UPPER
                    } else {
                        KeyboardMode.LETTERS_LOWER
                    },
                )
                return
            }
            viewModel.onWordAbandoned()
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
            // Read from the editor, not from the running buffer, and read before the space is
            // committed so the word is still the last thing before the caret. A field that
            // filtered the keystrokes out reports nothing here, which is exactly right: it
            // has no word to correct and none to learn.
            val typedWord = currentWordText(controller)

            // Two quick spaces become a full stop. Decided from the text rather than from the
            // taps alone: the pure decision reads what is actually before the caret, so it
            // cannot produce ".. " after a sentence that already ended, or a stray period at
            // the start of a field. Handled before the ordinary commit because it *replaces*
            // the space already there rather than adding to it.
            if (viewModel.consumeDoubleSpace()) {
                val replacement =
                    doubleSpaceReplacement(controller.textBeforeCursor(WORD_CONTEXT_CHARS))
                if (replacement != null) {
                    controller.replaceTextBeforeCursor(1, replacement)
                    viewModel.onSentenceStarted()
                    setMode(
                        if (viewModel.shouldAutoCapitalize.value) {
                            KeyboardMode.LETTERS_UPPER
                        } else {
                            KeyboardMode.LETTERS_LOWER
                        },
                    )
                    return
                }
            }

            // Commit the space FIRST, synchronously, so key order can never invert.
            // Waiting on the autocorrect lookup here used to let a following letter
            // commit before the space ("a b" arriving as "ab ").
            controller.commitText(" ")
            val token = viewModel.onSpacePressed()

            // A space ends a word, and the next word is almost never more symbols. Leaving the
            // board on the symbols page meant the following word was typed on the wrong plane
            // and the user had to notice and press ABC. Enter already reset the page for the
            // same reason; space did not, which made the two inconsistent as well as wrong.
            if (currentMode == KeyboardMode.SYMBOLS ||
                currentMode == KeyboardMode.SYMBOLS_SHIFTED
            ) {
                setMode(
                    if (viewModel.shouldAutoCapitalize.value) {
                        KeyboardMode.LETTERS_UPPER
                    } else {
                        KeyboardMode.LETTERS_LOWER
                    },
                )
            }

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
                controller.commitText(keyLabel)
            } else {
                // Editor-derived, same as the space bar and for the same reason.
                val typedWord = currentWordText(controller)
                // Committed first and synchronously, for the same reason the space bar is:
                // waiting on the correction lookup here would let a following key land before
                // this one and invert what the user typed.
                controller.commitText(keyLabel)
                val token = viewModel.onSymbolCommitted(keyLabel)

                if (typedWord.isNotBlank() && endsAWord(keyLabel)) {
                    // Autocorrect used to fire on the space bar and nowhere else, so anyone
                    // who ends sentences with punctuation -- which is everyone -- saw it work
                    // on some words and not others with no discernible pattern.
                    coroutineScope.launch {
                        val corrected = viewModel.getAutoCorrectionFor(typedWord)
                        if (corrected != null && viewModel.isCurrent(token)) {
                            // Replace "<typed><punctuation>" in one round-trip, so the
                            // punctuation the user just saw appear never flickers.
                            controller.replaceTextBeforeCursor(
                                typedWord.length + keyLabel.length,
                                corrected + keyLabel,
                            )
                            viewModel.onAutoCorrected(typedWord, corrected)
                        } else {
                            viewModel.onWordAccepted(typedWord)
                        }
                    }
                } else {
                    viewModel.onWordAccepted(typedWord)
                }
            }

            if (currentMode == KeyboardMode.LETTERS_UPPER) {
                setMode(KeyboardMode.LETTERS_LOWER)
            }
        }
    }
}

/**
 * Whether committing [keyLabel] means the word before it is finished.
 *
 * Only marks that genuinely close a word. The apostrophe is excluded because it sits *inside*
 * words ("don't"), and the hyphen because a hyphenated compound is still being typed --
 * correcting on either would fire halfway through a word the user had not finished.
 */
internal fun endsAWord(keyLabel: String): Boolean =
    keyLabel.length == 1 && keyLabel[0] in WORD_TERMINATORS

/** Punctuation after which the preceding word is complete and worth checking. */
private const val WORD_TERMINATORS = ".,!?;:"

/**
 * The word under the caret, as the *editor* has it.
 *
 * The single source for anything that must be true of the text rather than of this keyboard's
 * running buffer. Two things depend on that distinction, and both were bugs before they did:
 *
 *  - **Sizing a destructive edit.** The suggestion strip deleted `currentWord.length`
 *    characters of whatever happened to be there, which is the wrong span after a caret tap
 *    or a paste.
 *  - **Learning.** A field with an input filter accepts none of what was typed and the buffer
 *    records all of it, so `qwxzj` typed into a phone field was learned from an empty field.
 *
 * One editor read per word boundary, never per keystroke -- the thing this codebase forbids
 * everywhere else.
 */
internal fun currentWordText(controller: KeyboardController): String =
    wordUnderCaret(controller.textBeforeCursor(WORD_CONTEXT_CHARS))

/** How many characters the word under the caret occupies, read from the editor. */
internal fun currentWordSpan(controller: KeyboardController): Int =
    currentWordText(controller).length
