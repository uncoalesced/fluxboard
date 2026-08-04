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

        /**
         * A non-letter character was committed directly (punctuation, digits, symbols).
         *
         * Clears the in-progress word and returns the input token to validate against later,
         * exactly like [onSpacePressed], and for the same reason: it deliberately does **not**
         * learn the word. It used to be paired with a preceding [onWordFinished], which learned
         * whatever had been typed the instant a full stop arrived -- so a misspelling ending in
         * punctuation was committed to the personal dictionary before anything had a chance to
         * decide whether it needed correcting. The caller now makes that call once the
         * correction lookup resolves.
         */
        fun onSymbolCommitted(text: String): Int {
            currentWord = ""
            _suggestions.value = emptyList()
            _undoState.value = null
            // "." "!" "?" open a new sentence; anything else just continues.
            atSentenceStart = text.any { it in SENTENCE_ENDINGS }
            publishAutoCapitalize()
            return ++generation
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

        /**
         * Drops the in-progress word without learning it.
         *
         * Distinct from [onWordFinished], and the distinction is the whole point. A word is
         * only evidence of how the user writes if they finished writing it; a half-typed one
         * that the caret then moved away from is not. The space-bar scrub called
         * [onWordFinished] on *every step* of the drag, so scrubbing out of the middle of a
         * word taught the personal dictionary the fragment under the caret at that moment.
         * Those fragments then compete in suggestions, and once one has been seen twice
         * `PredictionEngine` treats it as deliberate and permanently refuses to autocorrect it
         * -- so the damage accumulates silently and outlives the gesture that caused it.
         */
        fun onWordAbandoned() {
            currentWord = ""
            _suggestions.value = emptyList()
        }

        /**
         * Re-derives the word under the caret from what the editor actually contains.
         *
         * Called only when something other than this keyboard changed the text or moved the
         * caret -- see `StickyKeysIME.onUpdateSelection`, which uses a predicted caret to tell
         * the two apart so that this never runs on the keystroke path.
         *
         * [currentWord] is otherwise a local mirror built by appending on each key press, which
         * is correct exactly while this keyboard is the only editor. After a caret tap, a
         * paste, or a selection replaced by the host, it describes text that is no longer
         * there, and the suggestion strip sizes a deletion from its length.
         */
        fun onEditorContextChanged(textBeforeCursor: String) {
            currentWord = wordUnderCaret(textBeforeCursor)
            // Anything in flight was computed against text that has since moved.
            generation++
            _undoState.value = null
            // Capitalization comes from the same source rather than being left at whatever the
            // last keystroke decided: a caret moved into the middle of a sentence must not
            // leave the board latched to upper case.
            atSentenceStart = startsNewSentence(textBeforeCursor)
            publishAutoCapitalize()
            updateSuggestions()
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

/**
 * The word the caret is sitting at the end of, given the text before it.
 *
 * Pure, and separate from the ViewModel, because it is the length used to size a destructive
 * edit: the suggestion strip replaces exactly this many characters of the user's text. An
 * off-by-one here does not show up as a wrong suggestion, it shows up as a letter of the
 * previous word being eaten, so it is worth being able to assert without a keyboard.
 *
 * Letters and interior apostrophes only, matching what the keyboard's own tracker accumulates
 * (`onKeyPressed` is called for letters and nothing else). Apostrophes count because "don't"
 * and "it's" are common enough that treating them as two words would make a suggestion tap
 * replace just the "t".
 */
internal fun wordUnderCaret(textBeforeCursor: String): String =
    textBeforeCursor
        .takeLastWhile { it.isLetter() || it == '\'' }
        // A leading apostrophe is a quotation mark, not part of the word. Keeping it would
        // add one to the replacement length and swallow the quote the user typed.
        .dropWhile { it == '\'' }

/**
 * Whether the caret sits where a new sentence begins.
 *
 * Same reasoning as [wordUnderCaret]: derived from the editor rather than from what the last
 * keystroke happened to set, so moving the caret into the middle of existing text does not
 * leave auto-capitalize armed.
 */
internal fun startsNewSentence(textBeforeCursor: String): Boolean {
    val trimmed = textBeforeCursor.trimEnd { it == ' ' }
    if (trimmed.isEmpty()) return true
    return trimmed.last() in SENTENCE_ENDINGS || trimmed.last() == '\n'
}

/** Marks that close a sentence, so the next letter is capitalized. */
internal const val SENTENCE_ENDINGS = ".!?"

/**
 * How much text is read back when re-deriving the word under the caret.
 *
 * One constant, referenced by both the IME service and the typing view, because the two sides
 * must agree: the service uses it to resync the tracker and the view uses it to size a
 * replacement, and a mismatch would silently truncate long words on one path only. Small on
 * purpose -- this crosses a binder transaction, and only the current word is wanted.
 */
internal const val WORD_CONTEXT_CHARS = 48
