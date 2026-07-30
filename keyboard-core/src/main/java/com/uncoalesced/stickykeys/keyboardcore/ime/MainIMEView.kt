// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyStyle
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker

/** Short enough not to sit between the user and their keyboard. */
internal const val MODE_TRANSITION_MS = 150

/**
 * Breathing room above the gesture bar, on top of the navigation-bar inset.
 *
 * The inset alone (24dp on this class of device) leaves the bottom key row flush against the
 * gesture pill: legal, but a swipe-up begun on that row is ambiguous between "type" and
 * "go home", and it reads as cramped. Half the inset again is enough separation to remove
 * both problems without eating usable key height, since this grows the window rather than
 * shrinking the panel.
 *
 * Tuned by eye on a 375dpi device; this is the single number to change if the keyboard ever
 * sits too low or too high above the gesture bar again.
 */
private val GESTURE_BAR_CLEARANCE = 12.dp

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

            override fun sendEditingKey(
                keyCode: Int,
                shift: Boolean,
                ctrl: Boolean,
            ) = delegate.sendEditingKey(keyCode, shift, ctrl)

            override fun performEditAction(actionId: Int) = delegate.performEditAction(actionId)

            override fun showInputMethodPicker() = delegate.showInputMethodPicker()
        }
    }

@Composable
fun MainIMEView(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
    stickerIMEViewModel: StickerIMEViewModel,
    clipboardIMEViewModel: ClipboardIMEViewModel,
    emojiPickerViewModel: EmojiPickerViewModel,
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
        keyStyle = activeTheme?.keyStyle ?: KeyStyle.Default,
    ) {
        // Hold the panel above the gesture bar.
        //
        // An IME window is anchored to the bottom of the screen and, under the edge-to-edge
        // behaviour that targetSdk 35+ enforces, extends *behind* the navigation bar rather
        // than being inset out of it. Nothing in this project consumed insets anywhere, so
        // the bottom key row was drawn underneath the gesture pill: the two shared the same
        // pixels, and a swipe there hit whichever won the gesture race.
        //
        // Padding here rather than inside each mode's own view for two reasons: it is the one
        // place all three modes pass through, and it grows the window instead of shrinking the
        // keys -- `rememberImePanelHeight()` stays the height of the *content*, so the panel
        // keeps its usable size and the strip below it is what gets added.
        //
        // The background is painted outside the padding so that strip is opaque keyboard
        // surface; left transparent it showed the host app through the gap.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StickyKeysTheme.colors.background)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = GESTURE_BAR_CLEARANCE),
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
                    AppMode.EMOJI_PICKER -> {
                        EmojiPickerView(
                            viewModel = emojiPickerViewModel,
                            fileManager = fileManager,
                            onEmojiClick = { glyph ->
                                // Committed as plain text; the platform's emoji font draws
                                // it. Deliberately no mode switch afterwards -- picking one
                                // emoji is almost always followed by picking another.
                                interceptingController.commitText(glyph)
                            },
                            onStickerClick = {
                                onStickerClick(it)
                                interceptingController.switchMode(AppMode.TYPING)
                            },
                            onBackToKeyboard = {
                                interceptingController.switchMode(AppMode.TYPING)
                            },
                            modifier = Modifier.height(rememberImePanelHeight()),
                        )
                    }
                    // Phase 16's dedicated sticker-only panel. No key routes here any more --
                    // the emoji key and the quick-access grid both open EMOJI_PICKER. Kept
                    // reachable in code pending a decision on retiring it; see the report.
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
                    AppMode.TEXT_EDIT -> {
                        TextEditPanel(
                            controller = interceptingController,
                            palette = StickyKeysTheme.colors,
                            onBackToKeyboard = {
                                interceptingController.switchMode(AppMode.TYPING)
                            },
                            modifier = Modifier.height(rememberImePanelHeight()),
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
}
