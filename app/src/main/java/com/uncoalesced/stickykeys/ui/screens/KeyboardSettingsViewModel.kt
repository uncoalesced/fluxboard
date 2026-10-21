// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface KeyboardSettingsUiState {
    data object Loading : KeyboardSettingsUiState

    data object Success : KeyboardSettingsUiState
}

@HiltViewModel
class KeyboardSettingsViewModel
    @Inject
    constructor(
        private val preferences: KeyboardPreferences,
        val themeManager: ThemeManager,
        val layoutManager: LayoutManager,
        private val clipboardDao: ClipboardDao,
        val appPreferences: com.uncoalesced.stickykeys.data.local.AppPreferences,
        private val usageLog: com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder,
        private val typingStats:
            com.uncoalesced.stickykeys.keyboardcore.diagnostics.TypingStatsStore,
    ) : ViewModel() {
        /** Opening either editor is what arms the default-keyboard prompt. */
        fun onCustomizerOpened() {
            appPreferences.markCustomizerVisited()
        }

        private val _uiState =
            MutableStateFlow<KeyboardSettingsUiState>(KeyboardSettingsUiState.Success)
        val uiState: StateFlow<KeyboardSettingsUiState> = _uiState.asStateFlow()

        val autoCapitalizeEnabled = preferences.autoCapitalizeEnabled
        val autoCorrectEnabled = preferences.autoCorrectEnabled

        val activeThemeId = preferences.activeThemeId
        val activeLayoutId = preferences.activeLayoutId

        val hapticsEnabled = preferences.hapticsEnabled
        val hapticsIntensity = preferences.hapticsIntensity

        val showNumberRow = preferences.showNumberRow
        val keySizePercent = preferences.keySizePercent
        val doubleSpacePeriod = preferences.doubleSpacePeriodEnabled
        val privateMode = preferences.privateModeEnabled
        val showKeyHints = preferences.showKeyHints
        val glideTyping = preferences.glideTypingEnabled

        fun setKeySizePercent(percent: Int) = preferences.setKeySizePercent(percent)

        fun setDoubleSpacePeriod(enabled: Boolean) = preferences.setDoubleSpacePeriod(enabled)

        /** Mirrors the toggle in the keyboard's own quick-access row; one stored flag. */
        fun setPrivateMode(enabled: Boolean) = preferences.setPrivateMode(enabled)

        fun setShowNumberRow(show: Boolean) = preferences.setShowNumberRow(show)

        /** Shows or hides the corner symbols. Long-press still types them either way. */
        fun setShowKeyHints(show: Boolean) = preferences.setShowKeyHints(show)

        /** Turns swipe-to-type on or off. */
        fun setGlideTyping(enabled: Boolean) = preferences.setGlideTyping(enabled)

        val keyboardHeightPercent = preferences.keyboardHeightPercent
        val keyboardBottomPaddingDp = preferences.keyboardBottomPaddingDp

        fun setKeyboardHeightPercent(percent: Int) = preferences.setKeyboardHeightPercent(percent)

        fun setKeyboardBottomPaddingDp(dp: Int) = preferences.setKeyboardBottomPaddingDp(dp)

        fun setAutoCapitalize(enabled: Boolean) = preferences.setAutoCapitalize(enabled)

        fun setAutoCorrect(enabled: Boolean) = preferences.setAutoCorrect(enabled)

        fun setHapticsEnabled(enabled: Boolean) = preferences.setHapticsEnabled(enabled)

        fun setHapticsIntensity(intensity: Int) = preferences.setHapticsIntensity(intensity)

        fun setActiveTheme(id: String) = preferences.setActiveThemeId(id)

        fun setActiveLayout(id: String) = preferences.setActiveLayoutId(id)

        fun clearClipboardHistory() {
            viewModelScope.launch(Dispatchers.IO) {
                clipboardDao.deleteAll()
            }
        }

        /**
         * The escape hatch from a keyboard that has rendered itself unusable.
         *
         * Both halves in one action on purpose: a user in this state cannot read their
         * keyboard, so they cannot be asked to work out whether it was the theme or the
         * layout that did it. Preferences that cannot blank a keyboard -- height, bottom
         * padding, haptics, the number row -- are deliberately left alone, so this is not a
         * general "reset all settings" that quietly throws away unrelated choices.
         */
        fun resetKeyboardAppearance() {
            viewModelScope.launch {
                themeManager.resetToDefaults()
                layoutManager.resetToDefaults()
            }
        }

        /** Compile-time false in release; the settings section is omitted entirely then. */
        val usageLoggingEnabled: Boolean get() = usageLog.enabled

        fun usageLogUri() = usageLog.shareIntentFile()

        fun clearUsageLog() = usageLog.clear()

        /**
         * Read on demand rather than exposed as a flow.
         *
         * These counters change on the typing path, and a flow emitting per keystroke would
         * mean this screen recomposing behind the keyboard for a number nobody is looking at.
         * A screen that is open is a screen the user just opened.
         */
        fun statsSnapshot() = typingStats.snapshot()

        fun clearStats() = typingStats.clear()
    }
