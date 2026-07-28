// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
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

    // Recomputed only when the layout or the letter case actually changes. Rebuilding this
    // list on every recomposition handed the key grid a fresh List each keystroke, which is
    // enough on its own to stop it skipping.
    val keyRows =
        remember(activeLayoutConfig, mode) {
            val isLetterMode =
                mode == KeyboardMode.LETTERS_LOWER ||
                    mode == KeyboardMode.LETTERS_UPPER ||
                    mode == KeyboardMode.LETTERS_CAPS_LOCK
            if (isLetterMode) {
                val isUpper =
                    mode == KeyboardMode.LETTERS_UPPER || mode == KeyboardMode.LETTERS_CAPS_LOCK
                KeyboardRows(
                    activeLayoutConfig.rows.map { row ->
                        row.map { key ->
                            if (isUpper &&
                                key.output.length == 1 &&
                                key.output.first().isLetter()
                            ) {
                                key.copy(output = key.output.uppercase())
                            } else {
                                key
                            }
                        }
                    },
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
            activeTheme?.typeScale ?: com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale.MEDIUM,
        customColors = activeTheme?.colors,
    ) {
        // One fixed height, shared with the sticker and clipboard panels, so switching mode
        // changes what is drawn and never how tall the IME window is.
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
                        if (incognito) {
                            // Status only: intentionally not clickable and not a toggle.
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Incognito: typing is not being learned",
                                tint = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurfaceVariant,
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
                                                // "<corrected> " -> "<original> " in one round-trip.
                                                keyboardController.replaceTextBeforeCursor(
                                                    undo.corrected.length + 1, // +1 for the space
                                                    undo.original + " ",
                                                )
                                                typingViewModel.onUndoApplied()
                                            }.padding(8.dp),
                                    color = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.primary,
                                    style = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.typography.labelLarge,
                                )
                            } else {
                                suggestions.forEach { suggestion ->
                                    Text(
                                        text = suggestion,
                                        modifier =
                                            Modifier
                                                .semantics {
                                                    contentDescription = "Suggestion: $suggestion"
                                                }.clickable(role = Role.Button) {
                                                    // Read the length and apply the swap in the same
                                                    // synchronous step, so nothing can be typed in
                                                    // between and shift what gets deleted.
                                                    keyboardController.replaceTextBeforeCursor(
                                                        typingViewModel.getCurrentWord().length,
                                                        "$suggestion ",
                                                    )
                                                    typingViewModel.onSuggestionSelected(suggestion)
                                                }.padding(8.dp),
                                        color = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurface,
                                        style = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.typography.labelLarge,
                                    )
                                }
                            }
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
                )
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
    displayLabel: String,
    mode: KeyboardMode,
    background: Color,
    foreground: Color,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spokenLabel = accessibleKeyLabel(keyOutput)
    val spokenState = accessibleKeyState(keyOutput, mode)

    // Shift latching between off, on and caps lock is the one key colour that changes while
    // the user is looking at it; snapping straight to the accent reads as a glitch.
    val animatedBackground by animateColorAsState(background, label = "key-background")
    val animatedForeground by animateColorAsState(foreground, label = "key-foreground")

    Box(
        modifier =
            modifier
                .padding(2.dp)
                .background(animatedBackground)
                .clickable(role = Role.Button) { onKeyPress(keyOutput) }
                .semantics(mergeDescendants = true) {
                    contentDescription = spokenLabel
                    if (spokenState != null) {
                        stateDescription = spokenState
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayLabel,
            color = animatedForeground,
            style = StickyKeysTheme.typography.keyboardKey,
            // Decoration only. Without this the merged node would also carry "⌫", or for
            // the space bar a single blank character.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

internal fun getDisplayLabel(keyLabel: String): String =
    when (keyLabel) {
        "SHIFT" -> "⇧"
        "DEL" -> "⌫"
        "SYMBOLS" -> "?123"
        "ABC" -> "ABC"
        "SYMBOLS_SHIFT" -> "=\\<"
        "STICKERS" -> ":)"
        "CLIPBOARD" -> "CLIP"
        "ENTER" -> "⏎"
        "SPACE" -> " "
        else -> keyLabel
    }

internal fun handleKeyPress(
    keyLabel: String,
    controller: KeyboardController,
    currentMode: KeyboardMode,
    viewModel: TypingViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    setMode: (KeyboardMode) -> Unit,
) {
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
        "STICKERS" -> controller.switchMode(AppMode.STICKERS)
        "CLIPBOARD" -> controller.switchMode(AppMode.CLIPBOARD)
        "DEL" -> {
            viewModel.onDelete()
            controller.sendDelete()
        }
        "ENTER" -> {
            viewModel.onWordFinished()
            controller.sendEnter()
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
