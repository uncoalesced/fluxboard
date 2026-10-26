// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyStyle
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale

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
    val shouldAutoCapitalize by typingViewModel.shouldAutoCapitalize.collectAsState()

    val keyRows =
        remember(activeLayoutConfig, mode, showNumberRow, fieldKind, shouldAutoCapitalize) {
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
                // Automatic sentence-start shift is the exception; see showsShiftedDigits.
                val digits = KeyboardLayouts.numberRowFor(mode, shouldAutoCapitalize)
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

    val onKeyRevoke =
        rememberKeyRevokeHandler(
            keyboardController = keyboardController,
            typingViewModel = typingViewModel,
        )

    val onDeleteWord = rememberDeleteWordHandler(keyboardController, typingViewModel)

    val onScrub = rememberScrubHandler(keyboardController, typingViewModel)

    // One tracker for the whole grid, remembered: every key registers its rectangle into it,
    // and a reallocated instance would lose the map mid-gesture.
    val glideTracker = remember { GlideTracker() }
    val glideEnabled by typingViewModel.glideTypingEnabled.collectAsState()
    val enterAction by typingViewModel.enterAction.collectAsState()

    val onGlide =
        rememberGlideHandler(keyboardController, typingViewModel, coroutineScope, modeState)

    val onSuggestionTap = rememberSuggestionTapHandler(keyboardController, typingViewModel)

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
                        action.id == "settings" ->
                            openAppAt(context, ROUTE_KEYBOARD_SETTINGS)
                        action.id == "switchime" -> keyboardController.showInputMethodPicker()
                        action.id == "resize" -> typingViewModel.setResizeMode(true)
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
                            )
                            // Vertical only. The side margin is handed to the key grid as
                            // edgeInset instead, so the outermost keys own it rather than it
                            // being a strip down each edge where a tap types nothing (4R.1).
                            .padding(vertical = contentPadding),
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
                                    .padding(horizontal = contentPadding)
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
                                    StripChip(
                                        label = "Undo: ${undo.original}",
                                        description =
                                            "Undo autocorrect, restore ${undo.original}",
                                        color = StickyKeysTheme.colors.primary,
                                        announce = true,
                                        onClick = {
                                            // Corrected -> original, one round-trip.
                                            // +1 covers the trailing space.
                                            val span = undo.corrected.length + 1
                                            keyboardController.replaceTextBeforeCursor(
                                                span,
                                                undo.original + " ",
                                            )
                                            typingViewModel.onUndoApplied()
                                        },
                                    )
                                } else {
                                    suggestions.forEach { suggestion ->
                                        StripChip(
                                            label = suggestion,
                                            description = "Suggestion: $suggestion",
                                            color = StickyKeysTheme.colors.onSurface,
                                            onClick = { onSuggestionTap(suggestion) },
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

                    // Grid and trail share one Box so the trail can be drawn in the same
                    // coordinate space without the grid knowing it exists. KeyboardRowsView
                    // never reads glideTracker.trailPoints, so a moving finger repaints only
                    // the overlay -- the grid does not recompose for it.
                    Box(modifier = Modifier.weight(1f)) {
                        KeyboardRowsView(
                            keyRows = keyRows,
                            mode = mode,
                            palette = StickyKeysTheme.colors,
                            hasBackgroundImage = bgBitmap != null,
                            onKeyPress = onKeyPress,
                            onKeyRevoke = onKeyRevoke,
                            modifier = Modifier.fillMaxSize(),
                            onScrub = onScrub,
                            onDeleteWord = onDeleteWord,
                            // Null when the feature is off, which is what disables it: the
                            // gesture branch is skipped entirely rather than running and
                            // discarding its result, so a user who turns glide off gets the
                            // old pointer handling back exactly.
                            glide = if (glideEnabled) glideTracker else null,
                            onGlide = onGlide,
                            enterAction = enterAction,
                            edgeInset = contentPadding,
                        )
                        if (glideEnabled) {
                            GlideTrailOverlay(glideTracker = glideTracker)
                        }
                    }
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
