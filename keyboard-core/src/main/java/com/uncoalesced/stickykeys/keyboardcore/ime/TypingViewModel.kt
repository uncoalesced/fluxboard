// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyboardTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UndoAction(
    val original: String,
    val corrected: String,
)

@HiltViewModel
class TypingViewModel
    @Inject
    constructor(
        private val predictionEngine: PredictionEngine,
        private val keyboardPreferences: KeyboardPreferences,
        private val themeManager: ThemeManager,
        private val layoutManager: LayoutManager,
        private val hapticsManager: HapticsManager,
        private val incognitoState: IncognitoState,
    ) : ViewModel() {
        private val _suggestions = MutableStateFlow<List<String>>(emptyList())
        val suggestions: StateFlow<List<String>> = _suggestions

        private val _undoState = MutableStateFlow<UndoAction?>(null)
        val undoState: StateFlow<UndoAction?> = _undoState

        private val _shouldAutoCapitalize = MutableStateFlow(false)
        val shouldAutoCapitalize: StateFlow<Boolean> = _shouldAutoCapitalize

        /**
         * True while the focused editor sets IME_FLAG_NO_PERSONALIZED_LEARNING.
         * Session-scoped and automatic: no user toggle, no persistence. Backed by
         * the shared [IncognitoState] that also gates clipboard capture.
         */
        val incognito: StateFlow<Boolean> = incognitoState.active

        val activeTheme: StateFlow<KeyboardTheme?> = themeManager.activeTheme

        val activeLayout: StateFlow<KeyboardLayoutConfig> = layoutManager.activeLayout

        private var currentWord = ""

        init {
            viewModelScope.launch {
                predictionEngine.initialize()
            }
        }

        /**
         * Monotonic input token. Bumped by anything that changes what sits before the
         * cursor, so a slow async autocorrect can tell whether the text it was computed
         * against is still there before it rewrites it.
         */
        private var generation = 0

        /** True when the next letter should be capitalized, tracked locally (no IPC). */
        private var atSentenceStart = true

        /** Called from the IME when a new input session starts. [initialCapsMode] comes
         *  from EditorInfo.initialCapsMode -- non-zero means the field wants a capital. */
        fun onInputStarted(initialCapsMode: Int) {
            currentWord = ""
            _suggestions.value = emptyList()
            _undoState.value = null
            generation++
            atSentenceStart = initialCapsMode != 0
            publishAutoCapitalize()
        }

        fun isCurrent(token: Int): Boolean = token == generation

        private fun publishAutoCapitalize() {
            _shouldAutoCapitalize.value =
                atSentenceStart &&
                keyboardPreferences.autoCapitalizeEnabled.value
        }

        /** Driven by the editor's IME_FLAG_NO_PERSONALIZED_LEARNING for the current input session. */
        fun onIncognitoChanged(enabled: Boolean) {
            incognitoState.set(enabled)
        }

        /**
         * The single write path into the personal dictionary. Reading (suggestions,
         * autocorrect) is unaffected by incognito -- only writes are suspended.
         */
        private fun learn(word: String) {
            if (incognitoState.active.value) return
            viewModelScope.launch {
                predictionEngine.learnWord(word)
            }
        }

        fun performKeyPressHaptic() {
            hapticsManager.performKeyPressHaptic()
        }

        fun onKeyPressed(char: String) {
            currentWord += char
            _undoState.value = null // Typing clears undo state
            generation++
            // A letter was typed, so we are no longer at a sentence boundary.
            atSentenceStart = false
            publishAutoCapitalize()
            updateSuggestions()
        }

        /** Non-letter character committed directly (punctuation, digits, symbols). */
        fun onSymbolCommitted(text: String) {
            generation++
            // "." "!" "?" open a new sentence; anything else just continues.
            atSentenceStart = text.any { it == '.' || it == '!' || it == '?' }
            publishAutoCapitalize()
        }

        fun onDelete(): Boolean {
            generation++
            atSentenceStart = false
            publishAutoCapitalize()
            if (currentWord.isNotEmpty()) {
                currentWord = currentWord.dropLast(1)
                updateSuggestions()
                return true // handled internally
            }
            return false // let controller handle delete
        }

        /**
         * Clears the in-progress word for a space press and returns the input token to
         * validate against later. Deliberately does NOT learn the word: the caller
         * decides that once the async autocorrect check resolves.
         */
        fun onSpacePressed(): Int {
            currentWord = ""
            _suggestions.value = emptyList()
            _undoState.value = null
            atSentenceStart = false
            publishAutoCapitalize()
            return ++generation
        }

        /** Learns a word the user kept as-is (no correction applied). */
        fun onWordAccepted(word: String) {
            if (word.isNotBlank()) {
                learn(word)
            }
        }

        /** Autocorrect lookup against an explicit word, so it cannot race [currentWord]. */
        suspend fun getAutoCorrectionFor(word: String): String? {
            if (word.isBlank() || !keyboardPreferences.autoCorrectEnabled.value) return null
            return predictionEngine.getAutoCorrection(word)
        }

        fun onAutoCorrected(
            original: String,
            corrected: String,
        ) {
            _undoState.value = UndoAction(original, corrected)
            currentWord = ""
            _suggestions.value = emptyList()
            learn(corrected)
        }

        fun clearUndoState() {
            _undoState.value = null
        }

        fun onUndoApplied() {
            _undoState.value = null
        }

        fun onWordFinished() {
            val word = currentWord
            currentWord = ""
            _suggestions.value = emptyList()
            if (word.isNotBlank()) {
                learn(word)
            }
        }

        fun onSuggestionSelected(suggestion: String) {
            currentWord = ""
            _suggestions.value = emptyList()
            learn(suggestion)
        }

        fun getCurrentWord(): String = currentWord

        private fun updateSuggestions() {
            if (currentWord.isBlank()) {
                _suggestions.value = emptyList()
                return
            }
            viewModelScope.launch {
                _suggestions.value = predictionEngine.getSuggestions(currentWord)
            }
        }
    }
