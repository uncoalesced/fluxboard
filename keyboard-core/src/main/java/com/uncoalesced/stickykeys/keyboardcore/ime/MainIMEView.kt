// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker

/** Short enough not to sit between the user and their keyboard. */
internal const val MODE_TRANSITION_MS = 150

/**
 * The mode-intercepting wrapper around the real [KeyboardController], allocated once.
 *
 * This object used to be built fresh on every recomposition and handed straight to
 * [TypingKeyboardView], whose `KeyboardController` parameter Compose cannot prove stable. A
 * new instance each time meant the entire typing view was invalidated on any state change, so
 * per-key skipping never got a chance to apply. It writes through [appModeState] instead of
 * capturing the mode by value, which is what lets a single instance stay correct.
 */
@Composable
internal fun rememberInterceptingController(
    delegate: KeyboardController,
    appModeState: MutableState<AppMode>,
): KeyboardController =
    remember(delegate, appModeState) {
        object : KeyboardController {
            override fun commitText(text: String) = delegate.commitText(text)

            override fun replaceTextBeforeCursor(
                charCount: Int,
                replacement: String,
            ) = delegate.replaceTextBeforeCursor(charCount, replacement)

            override fun sendDelete() = delegate.sendDelete()

            override fun sendEnter() = delegate.sendEnter()

            override fun handleEditorAction() = delegate.handleEditorAction()

            override fun switchMode(mode: AppMode) {
                appModeState.value = mode
                delegate.switchMode(mode)
            }
        }
    }

@Composable
fun MainIMEView(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
    stickerIMEViewModel: StickerIMEViewModel,
    clipboardIMEViewModel: ClipboardIMEViewModel,
    fileManager: StickerFileManager,
    onStickerClick: (Sticker) -> Unit,
) {
    val appModeState = remember { mutableStateOf(AppMode.TYPING) }
    val currentAppMode = appModeState.value
    val activeTheme by typingViewModel.activeTheme.collectAsState()

    val interceptingController = rememberInterceptingController(keyboardController, appModeState)

    StickyKeysTheme(
        darkTheme = activeTheme?.isLight?.not() ?: true,
        typeScale = activeTheme?.typeScale ?: TypeScale.MEDIUM,
        customColors = activeTheme?.colors,
    ) {
        // The three modes are the same height, so a crossfade reads as the surface changing
        // contents rather than the window jumping. Sliding would fight the IME window, which
        // is anchored to the bottom of the screen; a fade is the honest motion here.
        AnimatedContent(
            targetState = currentAppMode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(MODE_TRANSITION_MS)))
                    .togetherWith(fadeOut(animationSpec = tween(MODE_TRANSITION_MS)))
            },
            label = "ime-mode",
        ) { targetMode ->
            when (targetMode) {
                AppMode.TYPING -> {
                    TypingKeyboardView(
                        keyboardController = interceptingController,
                        typingViewModel = typingViewModel,
                    )
                }
                AppMode.STICKERS -> {
                    StickerIMEView(
                        viewModel = stickerIMEViewModel,
                        fileManager = fileManager,
                        onStickerClick = {
                            onStickerClick(it)
                            interceptingController.switchMode(AppMode.TYPING)
                        },
                        onBackToKeyboard = {
                            interceptingController.switchMode(AppMode.TYPING)
                        },
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
                        },
                    )
                }
            }
        }
    }
}
