// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uncoalesced.stickykeys.keyboardcore.ime.AppMode
import com.uncoalesced.stickykeys.keyboardcore.ime.KeyboardController
import com.uncoalesced.stickykeys.keyboardcore.ime.TypingKeyboardView
import com.uncoalesced.stickykeys.keyboardcore.ime.TypingViewModel

/**
 * Drives the real [TypingKeyboardView] against a local text field instead of an editor.
 *
 * The third implementor of [KeyboardController]; the other two are the IME service itself
 * and the mode-intercepting wrapper inside `MainIMEView`. Adding a method to the interface
 * means editing all three.
 *
 * Editing operations are honest about what they cannot do here: there is no host editor and
 * no system clipboard involvement in a styling preview, so the movement keys walk the local
 * selection and the clipboard actions do nothing rather than pretending.
 */
private class PreviewKeyboardController(
    private val state: androidx.compose.runtime.MutableState<TextFieldValue>,
) : KeyboardController {
    override fun commitText(text: String) {
        val current = state.value
        val start = current.selection.start.coerceIn(0, current.text.length)
        val end = current.selection.end.coerceIn(0, current.text.length)
        val updated = current.text.replaceRange(start, end, text)
        state.value =
            TextFieldValue(updated, TextRange(start + text.length))
    }

    override fun replaceTextBeforeCursor(
        charCount: Int,
        replacement: String,
    ) {
        val current = state.value
        val cursor = current.selection.start.coerceIn(0, current.text.length)
        val from = (cursor - charCount).coerceAtLeast(0)
        val updated = current.text.replaceRange(from, cursor, replacement)
        state.value = TextFieldValue(updated, TextRange(from + replacement.length))
    }

    override fun sendDelete() {
        val current = state.value
        val cursor = current.selection.start.coerceIn(0, current.text.length)
        if (current.selection.length > 0) {
            val end = current.selection.end.coerceIn(0, current.text.length)
            state.value =
                TextFieldValue(current.text.replaceRange(cursor, end, ""), TextRange(cursor))
        } else if (cursor > 0) {
            state.value =
                TextFieldValue(
                    current.text.replaceRange(cursor - 1, cursor, ""),
                    TextRange(cursor - 1),
                )
        }
    }

    override fun sendEnter() = commitText("\n")

    override fun handleEditorAction() = Unit

    override fun switchMode(mode: AppMode) = Unit

    override fun sendEditingKey(
        keyCode: Int,
        shift: Boolean,
        ctrl: Boolean,
    ) {
        // Enough for the space-bar scrub to be felt, which is the point of the preview.
        val current = state.value
        val cursor = current.selection.start
        val next =
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> cursor - 1
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> cursor + 1
                android.view.KeyEvent.KEYCODE_MOVE_HOME -> 0
                android.view.KeyEvent.KEYCODE_MOVE_END -> current.text.length
                else -> cursor
            }
        state.value =
            current.copy(selection = TextRange(next.coerceIn(0, current.text.length)))
    }

    override fun performEditAction(actionId: Int) = Unit

    override fun showInputMethodPicker() = Unit
}

/**
 * Live preview: the current styling, rendered by the same composable the IME uses, with
 * somewhere to type.
 *
 * Deliberately the real [TypingKeyboardView] and the real [TypingViewModel] rather than a
 * mock-up of one. A hand-drawn approximation is exactly the sort of thing that stays
 * plausible while drifting from the keyboard it claims to preview -- which is the defect
 * this project already had in three separate places.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardPreviewScreen(
    onBack: () -> Unit = {},
    typingViewModel: TypingViewModel = hiltViewModel(),
) {
    val text = remember { mutableStateOf(TextFieldValue("")) }
    // Remembered, for the same reason MainIMEView remembers its intercepting controller: a
    // fresh instance per recomposition invalidates every key's press lambda.
    val controller = remember { PreviewKeyboardController(text) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Type here to feel the current styling and haptics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text.value,
                    onValueChange = { text.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    // The on-screen keyboard below is the one being previewed; letting the
                    // system IME open over it would cover the thing under test.
                    readOnly = true,
                    label = { Text("Sample text") },
                )
            }

            TypingKeyboardView(
                keyboardController = controller,
                typingViewModel = typingViewModel,
            )
        }
    }
}
