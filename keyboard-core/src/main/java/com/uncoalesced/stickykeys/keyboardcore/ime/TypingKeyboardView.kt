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

@Composable
fun TypingKeyboardView(
    keyboardController: KeyboardController
) {
    var mode by remember { mutableStateOf(KeyboardMode.LETTERS_LOWER) }
    val rows = KeyboardLayouts.getLayoutForMode(mode)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray)
            .padding(4.dp)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (keyLabel in row) {
                    val weight = when (keyLabel) {
                        "SPACE" -> 4f
                        "ENTER", "SHIFT", "DEL", "SYMBOLS", "ABC", "STICKERS", "SYMBOLS_SHIFT" -> 1.5f
                        else -> 1f
                    }

                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .padding(2.dp)
                            .height(48.dp)
                            .background(Color.White)
                            .clickable {
                                handleKeyPress(keyLabel, keyboardController, mode) { newMode ->
                                    mode = newMode
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = getDisplayLabel(keyLabel))
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
        "STICKERS" -> "☺" // Or sticker icon
        "ENTER" -> "⏎"
        "SPACE" -> " "
        else -> keyLabel
    }
}

private fun handleKeyPress(
    keyLabel: String,
    controller: KeyboardController,
    currentMode: KeyboardMode,
    setMode: (KeyboardMode) -> Unit
) {
    when (keyLabel) {
        "SHIFT" -> {
            setMode(
                if (currentMode == KeyboardMode.LETTERS_LOWER) KeyboardMode.LETTERS_UPPER
                else KeyboardMode.LETTERS_LOWER
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
        "DEL" -> controller.sendDelete()
        "ENTER" -> controller.sendEnter()
        "SPACE" -> controller.commitText(" ")
        else -> {
            controller.commitText(keyLabel)
            if (currentMode == KeyboardMode.LETTERS_UPPER) {
                setMode(KeyboardMode.LETTERS_LOWER)
            }
        }
    }
}
