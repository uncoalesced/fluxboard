// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder
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
        private val usageLog: UsageRecorder,
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

        /** Whether the always-visible digit row is drawn above the letters. */
        val showNumberRow: StateFlow<Boolean> = keyboardPreferences.showNumberRow

        /** Panel sizing, chosen by the user rather than fixed. See `ImePanelHeight`. */
        val keyboardHeightPercent: StateFlow<Int> = keyboardPreferences.keyboardHeightPercent
        val keyboardBottomPaddingDp: StateFlow<Int> = keyboardPreferences.keyboardBottomPaddingDp

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

        /**
         * Bumped once per input session, so the view can tell "a different field is now
         * focused" from an ordinary recomposition.
         *
         * The keyboard's latch state lives in the view, not here, and nothing was telling the
         * view a new session had begun -- so a caps lock left on in one app was still on in
         * the next field the user tapped, in a different app.
         */
        private val _inputSession = MutableStateFlow(0)
        val inputSession: StateFlow<Int> = _inputSession

        /** Called from the IME when a new input session starts. [initialCapsMode] comes
         *  from EditorInfo.initialCapsMode -- non-zero means the field wants a capital. */
        fun onInputStarted(initialCapsMode: Int) {
            currentWord = ""
            _suggestions.value = emptyList()
            _undoState.value = null
            generation++
            atSentenceStart = initialCapsMode != 0
            publishAutoCapitalize()
            _inputSession.value += 1
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
            // Counted here rather than in onKeyPressed: this fires for every key including
            // shift, symbols and enter, which is what "keystrokes" means to a tester.
            usageLog.onKeystroke()
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
            usageLog.onBackspace()
            generation++
            if (currentWord.isNotEmpty()) {
                currentWord = currentWord.dropLast(1)
                // Deleting back to nothing puts the caret where a sentence would start again,
                // so capitalization has to come back with it. Forcing this false
                // unconditionally meant clearing a message and retyping it produced a
                // lower-case first letter every time.
                atSentenceStart = currentWord.isEmpty()
                publishAutoCapitalize()
                updateSuggestions()
                return true // handled internally
            }
            atSentenceStart = true
            publishAutoCapitalize()
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
            // atSentenceStart is deliberately left alone. A space does not begin a word, it
            // ends one, so it carries whatever the preceding character decided: after "." it
            // must stay true. Clearing it here was why the letter following a full stop was
            // never capitalized -- the period set the flag and the space that always follows
            // it immediately cleared it again, so auto-capitalize only ever fired on the very
            // first word of a field.
            publishAutoCapitalize()
            return ++generation
        }

        /**
         * A new sentence begins: the message was sent, or a newline was inserted.
         *
         * Distinct from [onWordFinished], which only ends a word. Enter both commits and
         * starts fresh, and without this the shift state carried over from the message just
         * sent into the one being started.
         */
        fun onSentenceStarted() {
            currentWord = ""
            _suggestions.value = emptyList()
            _undoState.value = null
            generation++
            atSentenceStart = true
            publishAutoCapitalize()
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
            usageLog.onAutocorrectAccepted()
            _undoState.value = UndoAction(original, corrected)
            currentWord = ""
            _suggestions.value = emptyList()
            learn(corrected)
        }

        fun clearUndoState() {
            _undoState.value = null
        }

        fun onUndoApplied() {
            usageLog.onAutocorrectUndone()
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
