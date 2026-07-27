// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun TypingKeyboardView(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
) {
    var mode by remember { mutableStateOf(KeyboardMode.LETTERS_LOWER) }
    val activeLayoutConfig by typingViewModel.activeLayout.collectAsState()

    // For letter modes, use custom layout; for symbol modes, fall back to legacy
    val isLetterMode =
        mode == KeyboardMode.LETTERS_LOWER ||
            mode == KeyboardMode.LETTERS_UPPER ||
            mode == KeyboardMode.LETTERS_CAPS_LOCK

    val customRows: List<List<KeyDefinition>> =
        if (isLetterMode) {
            val isUpper =
                mode == KeyboardMode.LETTERS_UPPER || mode == KeyboardMode.LETTERS_CAPS_LOCK
            activeLayoutConfig.rows.map { row ->
                row.map { key ->
                    if (isUpper && key.output.length == 1 && key.output.first().isLetter()) {
                        key.copy(output = key.output.uppercase())
                    } else {
                        key
                    }
                }
            }
        } else {
            val legacyRows = KeyboardLayouts.getLayoutForMode(mode)
            KeyboardLayoutConfig.fromLegacyLayout("_temp", "_temp", legacyRows).rows
        }

    val suggestions by typingViewModel.suggestions.collectAsState()
    val undoState by typingViewModel.undoState.collectAsState()
    val incognito by typingViewModel.incognito.collectAsState()
    val shouldAutoCapitalize by typingViewModel.shouldAutoCapitalize.collectAsState()
    val activeTheme by typingViewModel.activeTheme.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val bgPath = activeTheme?.backgroundImagePath
    val bgBitmap =
        remember(bgPath) {
            if (bgPath != null) {
                val file = File(bgPath)
                if (file.exists()) {
                    android.graphics.BitmapFactory
                        .decodeFile(file.absolutePath)
                        ?.asImageBitmap()
                } else {
                    null
                }
            } else {
                null
            }
        }

    LaunchedEffect(shouldAutoCapitalize) {
        if (shouldAutoCapitalize && mode == KeyboardMode.LETTERS_LOWER) {
            mode = KeyboardMode.LETTERS_UPPER
        } else if (!shouldAutoCapitalize && mode == KeyboardMode.LETTERS_UPPER) {
            mode = KeyboardMode.LETTERS_LOWER
        }
    }

    StickyKeysTheme(
        darkTheme = activeTheme?.isLight?.not() ?: true,
        typeScale =
            activeTheme?.typeScale ?: com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale.MEDIUM,
        customColors = activeTheme?.colors,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
                        .fillMaxWidth()
                        .background(
                            if (bgBitmap ==
                                null
                            ) {
                                com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.background
                            } else {
                                Color.Transparent
                            },
                        ).padding(4.dp),
            ) {
                // Suggestion strip; also carries the passive incognito indicator.
                val undo = undoState
                if (incognito || undo != null || suggestions.isNotEmpty()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(
                                    com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surface,
                                ).padding(horizontal = 8.dp),
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

                for (row in customRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (keyDef in row) {
                            val keyOutput = keyDef.output
                            val weight = keyDef.weight

                            val isSpecialKey = weight > 1f
                            val keyBgBase = if (isSpecialKey) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surfaceVariant else com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surface
                            val keyBg =
                                if (bgBitmap !=
                                    null
                                ) {
                                    keyBgBase.copy(alpha = 0.75f)
                                } else {
                                    keyBgBase
                                }
                            val keyFg = if (isSpecialKey) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurfaceVariant else com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurface

                            KeyboardKey(
                                keyOutput = keyOutput,
                                displayLabel = keyDef.displayLabel ?: getDisplayLabel(keyOutput),
                                mode = mode,
                                background = keyBg,
                                foreground = keyFg,
                                modifier = Modifier.weight(weight),
                                onPress = {
                                    handleKeyPress(
                                        keyOutput,
                                        keyboardController,
                                        mode,
                                        typingViewModel,
                                        coroutineScope,
                                    ) { newMode ->
                                        mode = newMode
                                    }
                                },
                            )
                        }
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
 */
@Composable
internal fun KeyboardKey(
    keyOutput: String,
    displayLabel: String,
    mode: KeyboardMode,
    background: Color,
    foreground: Color,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spokenLabel = accessibleKeyLabel(keyOutput)
    val spokenState = accessibleKeyState(keyOutput, mode)

    Box(
        modifier =
            modifier
                .padding(2.dp)
                .height(48.dp)
                .background(background)
                .clickable(role = Role.Button, onClick = onPress)
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
            color = foreground,
            style = StickyKeysTheme.typography.keyboardKey,
            // Decoration only. Without this the merged node would also carry "⌫", or for
            // the space bar a single blank character.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private fun getDisplayLabel(keyLabel: String): String =
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

private fun handleKeyPress(
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
