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
         * True while nothing typed may be written to the dictionary or the clipboard history.
         *
         * Three separate things raise it and they are combined in exactly one place,
         * [publishPrivacy]: the focused editor setting IME_FLAG_NO_PERSONALIZED_LEARNING, the
         * field declaring itself a password or a PIN through `inputType`, and the user's own
         * manual switch. Backed by the shared [IncognitoState] that also gates clipboard
         * capture, so there is still only one flag in the process.
         */
        val incognito: StateFlow<Boolean> = incognitoState.active

        /** Whether the manual privacy switch is on. Drawn as a toggle in the quick-access row. */
        val privateMode: StateFlow<Boolean> = keyboardPreferences.privateModeEnabled

        val activeTheme: StateFlow<KeyboardTheme?> = themeManager.activeTheme

        val activeLayout: StateFlow<KeyboardLayoutConfig> = layoutManager.activeLayout

        /** Whether the always-visible digit row is drawn above the letters. */
        val showNumberRow: StateFlow<Boolean> = keyboardPreferences.showNumberRow

        /** Panel sizing, chosen by the user rather than fixed. See `ImePanelHeight`. */
        val keyboardHeightPercent: StateFlow<Int> = keyboardPreferences.keyboardHeightPercent
        val keyboardBottomPaddingDp: StateFlow<Int> = keyboardPreferences.keyboardBottomPaddingDp

        /** How large a key is drawn inside its cell. Independent of the panel height. */
        val keySizePercent: StateFlow<Int> = keyboardPreferences.keySizePercent

        /** Whether a second quick space becomes a full stop. */
        val doubleSpacePeriodEnabled: StateFlow<Boolean> =
            keyboardPreferences.doubleSpacePeriodEnabled

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

        /**
         * What kind of input is in front of the user, after the manual switch is folded in.
         *
         * Drives the digits-only PIN grid and, together with [incognito], everything that must
         * not happen on a secret. Never assigned directly -- see [publishPrivacy].
         */
        private val _fieldKind = MutableStateFlow(FieldKind.NORMAL)
        val fieldKind: StateFlow<FieldKind> = _fieldKind

        /** What the field itself declared, before the manual switch. */
        private var detectedFieldKind = FieldKind.NORMAL

        /** Whether the host asked not to be learned from, for the current session. */
        private var hostNoLearning = false

        init {
            // The switch can be thrown from the keyboard mid-sentence or from the settings
            // screen, and both have to take effect without waiting for the next field focus --
            // a privacy control that only arms itself later is one the user cannot trust.
            viewModelScope.launch {
                keyboardPreferences.privateModeEnabled.collect { publishPrivacy() }
            }
        }

        /** Called from the IME when a new input session starts. [initialCapsMode] comes
         *  from EditorInfo.initialCapsMode -- non-zero means the field wants a capital.
         *  [noPersonalizedLearning] is the host's IME_FLAG_NO_PERSONALIZED_LEARNING. */
        fun onInputStarted(
            initialCapsMode: Int,
            fieldKind: FieldKind = FieldKind.NORMAL,
            noPersonalizedLearning: Boolean = false,
            enterIsNewline: Boolean = false,
        ) {
            currentWord = ""
            _suggestions.value = emptyList()
            _undoState.value = null
            generation++
            detectedFieldKind = fieldKind
            hostNoLearning = noPersonalizedLearning
            enterInsertsNewline = enterIsNewline
            publishPrivacy()
            atSentenceStart = initialCapsMode != 0
            publishAutoCapitalize()
            _inputSession.value += 1
        }

        /**
         * Whether the return key inserts a newline in this field rather than submitting.
         *
         * Decides whether Enter is allowed to finish a word. On a submit-style field it is
         * not: Enter deliberately does not autocorrect there, because there is no safe moment
         * to do it -- before needs a blocking lookup, after is too late in a field that has
         * already sent. Learning anyway meant every typo the user *sent* went into the
         * personal dictionary unchecked, and after two sightings `PredictionEngine` treats a
         * word as deliberate and stops correcting it. Measured on device: two sends of "teh"
         * and the keyboard stops fixing it, permanently, and starts suggesting it.
         *
         * Where Enter is a newline there is no send and therefore no race, so the word is
         * ended exactly the way the space bar ends one.
         */
        private var enterInsertsNewline = false

        fun enterEndsAWord(): Boolean = enterInsertsNewline

        /**
         * The input session ended: the keyboard was hidden, or focus left the field.
         *
         * Drops what the *field* said and keeps what the *user* said. The host's flag and the
         * field's type describe one editor and must not linger over the next one, but the
         * manual switch is persistent by design -- clearing it here would silently disarm it
         * every time the keyboard was dismissed, which is the exact failure it exists to avoid.
         */
        fun onInputFinished() {
            detectedFieldKind = FieldKind.NORMAL
            hostNoLearning = false
            publishPrivacy()
        }

        /** Throws the manual privacy switch. Persisted; [publishPrivacy] runs off the flow. */
        fun setPrivateMode(enabled: Boolean) {
            keyboardPreferences.setPrivateMode(enabled)
        }

        /**
         * The single place the three privacy inputs are combined.
         *
         * Everything downstream reads one of the two values written here and nothing
         * recomputes the decision for itself -- which is what keeps a future third caller from
         * arriving at a different answer than the suggestion strip did.
         */
        private fun publishPrivacy() {
            val effective =
                effectiveFieldKind(detectedFieldKind, keyboardPreferences.privateModeEnabled.value)
            _fieldKind.value = effective
            incognitoState.set(hostNoLearning || effective.isSensitive)
        }

        fun isCurrent(token: Int): Boolean = token == generation

        /** When the last space was committed, for the double-space window. */
        private var lastSpaceAt = 0L

        /**
         * True when this space arrived quickly enough after the previous one to count as a
         * double tap, and the feature is on.
         *
         * Consumes the timestamp either way, so three spaces in a row produce one substitution
         * and then a plain space rather than a second one. The window is the platform's own
         * double-tap timeout, so it matches every other double tap on the device and there is
         * nothing here to tune wrongly.
         */
        fun consumeDoubleSpace(): Boolean {
            val now = System.currentTimeMillis()
            val quick = now - lastSpaceAt <= DOUBLE_TAP_WINDOW_MS
            lastSpaceAt = if (quick) 0L else now
            return quick && keyboardPreferences.doubleSpacePeriodEnabled.value
        }

        private fun publishAutoCapitalize() {
            _shouldAutoCapitalize.value =
                atSentenceStart &&
                keyboardPreferences.autoCapitalizeEnabled.value &&
                // Never on a credential field. `initialCapsMode` is non-zero for an empty text
                // field whatever its variation, so a password field armed shift and the first
                // character of the password was silently not the one the user pressed --
                // observed on device as "correcthorse" entered as "Correcthorse". In a masked
                // field that is invisible, which makes it worse rather than more forgivable:
                // the user gets a failed login and no way to see why.
                //
                // Gated on isCredential, not isSensitive: the manual privacy switch also makes
                // a field sensitive, and it must not stop capitalizing ordinary sentences.
                !_fieldKind.value.isCredential
        }

        /**
         * Driven by the editor's IME_FLAG_NO_PERSONALIZED_LEARNING for the current session.
         *
         * Routed through [publishPrivacy] rather than writing [IncognitoState] directly: the
         * host's flag is now one of three inputs, and a direct write here would be undone by
         * the next thing that recomputed the combination.
         */
        fun onIncognitoChanged(enabled: Boolean) {
            hostNoLearning = enabled
            publishPrivacy()
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
         *
         * Returns the new generation token, so a correction dispatched afterwards can check it
         * is still the current one before rewriting anything.
         */
        fun onSentenceStarted(): Int {
            currentWord = ""
            _suggestions.value = emptyList()
            _undoState.value = null
            atSentenceStart = true
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
            // Never on a secret, for two separate reasons: the lookup itself puts the password
            // through the dictionary, and a correction that fired would silently rewrite what
            // the user typed into a field where they cannot see it to check.
            if (_fieldKind.value.isSensitive) return null
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

        /**
         * Ends the current word and learns [word].
         *
         * [word] is a parameter rather than [currentWord] on purpose, and this is the root fix
         * for a defect found on device: typing `qwxzj` into a `TYPE_CLASS_PHONE` field left
         * the field **empty** -- the editor's own input filter dropped every letter -- and put
         * `qwxzj` in the personal dictionary. Nothing had gone wrong with the keyboard; it had
         * simply learned its own running buffer rather than the text that exists.
         *
         * That buffer is a local mirror, built by appending on each key press because reading
         * the editor per keystroke would be a blocking IPC. Destructive edits were already
         * taught not to trust it -- the suggestion strip sizes its replacement with
         * `currentWordSpan(controller)`, which re-reads the field. Learning was the other half
         * of the same gap. Every caller now derives the word from the editor with
         * `currentWordText(controller)`, so the mirror is no longer an input to learning at
         * all: a field that rejects a character cannot teach it.
         */
        fun onWordFinished(word: String) {
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

        /**
         * The keyboard's running copy of the word being typed. **Never use this for learning
         * or for sizing a destructive edit** -- see [onWordFinished]. It exists so suggestions
         * can be looked up without an IPC per keystroke, and it is correct only while this
         * keyboard is the sole editor.
         */
        fun getCurrentWord(): String = currentWord

        private fun updateSuggestions() {
            // A secret is never looked up and never displayed. Note this is a *read* gate, so
            // it cannot be folded into incognito: incognito deliberately suspends writes only,
            // leaving suggestions working, which is right for a field the host merely asked
            // not to learn from and wrong for a password. Drawing dictionary matches for a
            // password prefix in a strip above the keyboard is a shoulder-surfing hazard on
            // its own, before any of it reaches storage.
            if (currentWord.isBlank() || _fieldKind.value.isSensitive) {
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
 * How close together two taps must be to count as one gesture.
 *
 * `ViewConfiguration.getDoubleTapTimeout()` is 300ms on every Android build and is what the
 * platform itself uses, so this matches every other double tap on the device. Named once here
 * because the caps-lock retap will want the same number, and two constants that mean "a double
 * tap" is exactly how they drift apart.
 */
internal const val DOUBLE_TAP_WINDOW_MS = 300L

/**
 * What a second space should replace, or null when it should just be a space.
 *
 * Pure, and that is the whole defence against the plausible-looking version of this feature.
 * The naive implementation watches for two taps inside a time window and inserts a period
 * without ever reading the text. It demonstrates perfectly and then produces ".. " after a
 * sentence that already ended, ". " at the start of an empty field, and a stray period after
 * a line break -- none of which show up until someone is actually writing something.
 *
 * [textBeforeCursor] is the text *including* the first space, as it stands when the second
 * space arrives. Returns the replacement for that single trailing space.
 */
internal fun doubleSpaceReplacement(textBeforeCursor: String): String? {
    // The first space must actually be there. If it is not, the two taps were not consecutive
    // in the text even if they were consecutive in time -- something else was committed in
    // between, and that something is not ours to overwrite.
    if (!textBeforeCursor.endsWith(" ")) return null

    val beforeSpace = textBeforeCursor.dropLast(1)
    if (beforeSpace.isEmpty()) return null

    val previous = beforeSpace.last()
    // Only ever after something a sentence can end on. A letter or a digit qualifies; a
    // closing bracket or quote does too, since "(like this) " is a real sentence ending.
    val endsAClause = previous.isLetterOrDigit() || previous in ")]}\"'"
    if (!endsAClause) return null

    return ". "
}

/**
 * How much text is read back when re-deriving the word under the caret.
 *
 * One constant, referenced by both the IME service and the typing view, because the two sides
 * must agree: the service uses it to resync the tracker and the view uses it to size a
 * replacement, and a mismatch would silently truncate long words on one path only. Small on
 * purpose -- this crosses a binder transaction, and only the current word is wanted.
 */
internal const val WORD_CONTEXT_CHARS = 48
