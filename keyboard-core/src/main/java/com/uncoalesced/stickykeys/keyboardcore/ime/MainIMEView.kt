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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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

            override fun textBeforeCursor(maxChars: Int): String =
                delegate.textBeforeCursor(maxChars)

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

            override fun moveCursor(
                move: CursorMove,
                extend: Boolean,
            ) = delegate.moveCursor(move, extend)

            override fun performEditAction(actionId: Int) = delegate.performEditAction(actionId)

            override fun showInputMethodPicker() = delegate.showInputMethodPicker()
        }
    }

@Composable
fun MainIMEView(
    keyboardController: KeyboardController,
    typingViewModel: TypingViewModel,
    clipboardIMEViewModel: ClipboardIMEViewModel,
    emojiPickerViewModel: EmojiPickerViewModel,
    fileManager: StickerFileManager,
    onStickerClick: (Sticker) -> Unit,
) {
    val appModeState = remember { mutableStateOf(AppMode.TYPING) }
    val currentAppMode = appModeState.value
    val activeTheme by typingViewModel.activeTheme.collectAsState()

    val interceptingController = rememberInterceptingController(keyboardController, appModeState)

    // A new field means the keyboard, whatever panel was open over the last one.
    //
    // Every other latch is already session-scoped -- shift, caps lock, the symbols page, the
    // quick-access row -- and the app mode was the one that was not. Observed on device:
    // opening clipboard history and then tapping a different text field left the clipboard
    // panel sitting over the new field, with no way to type into it until the user found their
    // way back. Keyed on the session counter rather than on the mode so that switching panels
    // by hand is untouched.
    val inputSession by typingViewModel.inputSession.collectAsState()
    LaunchedEffect(inputSession) {
        appModeState.value = AppMode.TYPING
    }

    // Sizing is read once, here, and published to every mode. rememberImePanelHeight() is
    // called from five different views and none of them should have to know about preferences
    // to be the right height.
    val heightPercent by typingViewModel.keyboardHeightPercent.collectAsState()
    val bottomPaddingDp by typingViewModel.keyboardBottomPaddingDp.collectAsState()
    val numberRowShown by typingViewModel.showNumberRow.collectAsState()
    val keySizePercent by typingViewModel.keySizePercent.collectAsState()
    val panelMetrics =
        remember(heightPercent, bottomPaddingDp, numberRowShown, keySizePercent) {
            ImePanelMetrics(
                heightScale = heightPercent / 100f,
                keyScale = keySizePercent / 100f,
                bottomPadding = bottomPaddingDp.dp,
                // Not gated on the mode, and that is the fix rather than an oversight.
                //
                // This used to read `numberRowShown && currentAppMode == AppMode.TYPING`, whose
                // comment claimed it stopped a mode switch resizing the window. Measured on
                // device, it caused exactly that: with the number row on -- the default --
                // typing was 956px and the emoji picker, clipboard and text-edit panels were
                // 830px, a 126px jump on every switch, which is precisely
                // `ime_number_row_height`. Opening the picker shrank the IME window and closing
                // it grew it back, shoving the host app's content down and up each time.
                //
                // The other modes now get the same height whether or not they draw a digit row,
                // which is what `dimens.xml` says the value is for: one number, every mode, so
                // switching changes what is drawn and never how tall the window is.
                showNumberRow = numberRowShown,
            )
        }

    CompositionLocalProvider(LocalImePanelMetrics provides panelMetrics) {
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
                        .padding(bottom = panelMetrics.bottomPadding),
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
                            // The emoji key lands on Recent, not on whatever tab was left
                            // selected last time. Keyed on entering the mode rather than on
                            // composition, so scrolling within the picker does not reset it.
                            LaunchedEffect(Unit) { emojiPickerViewModel.openAtDefaultTab() }
                            EmojiPickerView(
                                viewModel = emojiPickerViewModel,
                                fileManager = fileManager,
                                onEmojiClick = { glyph ->
                                    // Recorded before the commit so the Recent tab is already
                                    // correct if the user reopens the picker immediately.
                                    emojiPickerViewModel.onEmojiUsed(glyph)
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
}
