// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File

@Composable
fun TypingKeyboardView(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel
) {
    var mode by remember { mutableStateOf(KeyboardMode.LETTERS_LOWER) }
    val activeLayoutConfig by typingViewModel.activeLayout.collectAsState()

    // For letter modes, use custom layout; for symbol modes, fall back to legacy
    val isLetterMode = mode == KeyboardMode.LETTERS_LOWER ||
        mode == KeyboardMode.LETTERS_UPPER ||
        mode == KeyboardMode.LETTERS_CAPS_LOCK

    val customRows: List<List<KeyDefinition>> = if (isLetterMode) {
        val isUpper = mode == KeyboardMode.LETTERS_UPPER || mode == KeyboardMode.LETTERS_CAPS_LOCK
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
    val shouldAutoCapitalize by typingViewModel.shouldAutoCapitalize.collectAsState()
    val activeTheme by typingViewModel.activeTheme.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()

    val bgPath = activeTheme?.backgroundImagePath
    val bgBitmap = remember(bgPath) {
        if (bgPath != null) {
            val file = File(bgPath)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else null
        } else null
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
        typeScale = activeTheme?.typeScale ?: com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale.MEDIUM,
        customColors = activeTheme?.colors
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                // Legibility dark overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = activeTheme?.imageOverlayOpacity ?: 0.4f))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (bgBitmap == null) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.background else Color.Transparent)
                    .padding(4.dp)
            ) {
            // Suggestion Strip
            val undo = undoState
        if (undo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surface)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Undo: ${undo.original}",
                    modifier = Modifier
                        .clickable {
                            val toDelete = undo.corrected.length + 1 // +1 for space
                            for (i in 0 until toDelete) {
                                keyboardController.sendDelete()
                            }
                            keyboardController.commitText(undo.original + " ")
                            typingViewModel.onUndoApplied()
                        }
                        .padding(8.dp),
                    color = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.primary,
                    style = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.typography.labelLarge
                )
            }
        } else if (suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surface)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                suggestions.forEach { suggestion ->
                    Text(
                        text = suggestion,
                        modifier = Modifier
                            .clickable {
                                // Delete the currently typed word
                                val currentWordLength = typingViewModel.getCurrentWord().length
                                for (i in 0 until currentWordLength) {
                                    keyboardController.sendDelete()
                                }
                                keyboardController.commitText("$suggestion ")
                                typingViewModel.onSuggestionSelected(suggestion)
                            }
                            .padding(8.dp),
                        color = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurface,
                        style = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.typography.labelLarge
                    )
                }
            }
        }
        
        for (row in customRows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (keyDef in row) {
                    val keyOutput = keyDef.output
                    val weight = keyDef.weight

                    val isSpecialKey = weight > 1f
                    val keyBgBase = if (isSpecialKey) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surfaceVariant else com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surface
                    val keyBg = if (bgBitmap != null) keyBgBase.copy(alpha = 0.75f) else keyBgBase
                    val keyFg = if (isSpecialKey) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurfaceVariant else com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurface

                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .padding(2.dp)
                            .height(48.dp)
                            .background(keyBg)
                            .clickable {
                                handleKeyPress(keyOutput, keyboardController, mode, typingViewModel, coroutineScope) { newMode ->
                                    mode = newMode
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = keyDef.displayLabel ?: getDisplayLabel(keyOutput),
                            color = keyFg,
                            style = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.typography.keyboardKey
                        )
                    }
                }
            }
        }
    }
}
}
}

private fun getDisplayLabel(keyLabel: String): String {
    return when (keyLabel) {
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
}

private fun handleKeyPress(
    keyLabel: String,
    controller: KeyboardController,
    currentMode: KeyboardMode,
    viewModel: TypingViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    setMode: (KeyboardMode) -> Unit
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
                }
            )
        }
        "SYMBOLS_SHIFT" -> {
            setMode(
                if (currentMode == KeyboardMode.SYMBOLS) KeyboardMode.SYMBOLS_SHIFTED
                else KeyboardMode.SYMBOLS
            )
        }
        "SYMBOLS" -> setMode(KeyboardMode.SYMBOLS)
        "ABC" -> setMode(KeyboardMode.LETTERS_LOWER)
        "STICKERS" -> controller.switchMode(AppMode.STICKERS)
        "CLIPBOARD" -> controller.switchMode(AppMode.CLIPBOARD)
        "DEL" -> {
            if (!viewModel.onDelete()) {
                controller.sendDelete()
            } else {
                controller.sendDelete()
            }
        }
        "ENTER" -> {
            viewModel.onWordFinished()
            controller.sendEnter()
        }
        "SPACE" -> {
            val currentWord = viewModel.getCurrentWord()
            coroutineScope.launch {
                val corrected = viewModel.getAutoCorrection()
                if (corrected != null) {
                    for (i in 0 until currentWord.length) {
                        controller.sendDelete()
                    }
                    controller.commitText(corrected)
                    viewModel.onAutoCorrected(currentWord, corrected)
                } else {
                    viewModel.onWordFinished()
                }
                controller.commitText(" ")
            }
        }
        else -> {
            val isLetter = keyLabel.length == 1 && keyLabel.first().isLetter()
            if (isLetter) {
                viewModel.onKeyPressed(keyLabel)
            } else {
                viewModel.onWordFinished()
            }
            
            controller.commitText(keyLabel)
            if (currentMode == KeyboardMode.LETTERS_UPPER) {
                setMode(KeyboardMode.LETTERS_LOWER)
            }
        }
    }
}
