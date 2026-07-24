// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker

@Composable
fun MainIMEView(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
    stickerIMEViewModel: StickerIMEViewModel,
    clipboardIMEViewModel: ClipboardIMEViewModel,
    fileManager: StickerFileManager,
    onStickerClick: (Sticker) -> Unit
) {
    var currentAppMode by remember { mutableStateOf(AppMode.TYPING) }
    val activeTheme by typingViewModel.activeTheme.collectAsState()

    // Intercept switchMode requests
    val interceptingController = object : KeyboardController {
        override fun commitText(text: String) = keyboardController.commitText(text)
        override fun sendDelete() = keyboardController.sendDelete()
        override fun sendEnter() = keyboardController.sendEnter()
        override fun handleEditorAction() = keyboardController.handleEditorAction()
        override fun switchMode(mode: AppMode) {
            currentAppMode = mode
            keyboardController.switchMode(mode)
        }
    }

    StickyKeysTheme(
        darkTheme = activeTheme?.isLight?.not() ?: true,
        typeScale = activeTheme?.typeScale ?: TypeScale.MEDIUM,
        customColors = activeTheme?.colors
    ) {
        when (currentAppMode) {
            AppMode.TYPING -> {
                TypingKeyboardView(
                    keyboardController = interceptingController,
                    typingViewModel = typingViewModel
                )
            }
            AppMode.STICKERS -> {
                StickerIMEView(
                    viewModel = stickerIMEViewModel,
                    fileManager = fileManager,
                    onStickerClick = {
                        onStickerClick(it)
                        // Switch back to typing mode after sending sticker (optional, but good UX)
                        interceptingController.switchMode(AppMode.TYPING)
                    },
                    onBackToKeyboard = {
                        interceptingController.switchMode(AppMode.TYPING)
                    }
                )
            }
            AppMode.CLIPBOARD -> {
                ClipboardIMEView(
                    viewModel = clipboardIMEViewModel,
                    onPasteText = { text ->
                        interceptingController.commitText(text)
                        interceptingController.switchMode(AppMode.TYPING)
                    },
                    onBackToKeyboard = {
                        interceptingController.switchMode(AppMode.TYPING)
                    }
                )
            }
        }
    }
}
