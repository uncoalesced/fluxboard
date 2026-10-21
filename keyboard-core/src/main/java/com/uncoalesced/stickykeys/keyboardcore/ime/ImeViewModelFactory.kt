// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository

/**
 * Builds the IME's ViewModels.
 *
 * Hilt cannot construct these: the IME is a Service hosting Compose itself, not an Activity
 * or Fragment, so there is no Hilt ViewModel factory to ask. Adding a constructor parameter to
 * TypingViewModel, ClipboardIMEViewModel or EmojiPickerViewModel therefore means adding it
 * here too, and passing it in from StickyKeysIME.
 */
internal class ImeViewModelFactory(
    private val predictionEngine: PredictionEngine,
    private val keyboardPreferences: KeyboardPreferences,
    private val themeManager: com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager,
    private val layoutManager: com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager,
    private val hapticsManager: com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager,
    private val incognitoState: IncognitoState,
    private val usageLog: com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder,
    private val typingStats: com.uncoalesced.stickykeys.keyboardcore.diagnostics.TypingStatsStore,
    private val clipboardDao: com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao,
    private val repository: StickerRepository,
    private val emojiRepository: com.uncoalesced.stickykeys.keyboardcore.emoji.EmojiRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TypingViewModel::class.java)) {
            return TypingViewModel(
                predictionEngine,
                keyboardPreferences,
                themeManager,
                layoutManager,
                hapticsManager,
                incognitoState,
                usageLog,
                typingStats,
            ) as T
        } else if (modelClass.isAssignableFrom(ClipboardIMEViewModel::class.java)) {
            return ClipboardIMEViewModel(clipboardDao) as T
        } else if (modelClass.isAssignableFrom(EmojiPickerViewModel::class.java)) {
            return EmojiPickerViewModel(
                repository,
                emojiRepository,
                keyboardPreferences,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
