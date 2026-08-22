// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.view.inputmethod.EditorInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.TypingStatsStore
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
import kotlinx.coroutines.flow.asStateFlow
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
        private val typingStats: TypingStatsStore,
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

        /** Whether keys draw their corner symbol. Presentation only; long-press is unaffected. */
        val showKeyHints: StateFlow<Boolean> = keyboardPreferences.showKeyHints

        /** Whether swiping across the letters decodes into a word. */
        val glideTypingEnabled: StateFlow<Boolean> = keyboardPreferences.glideTypingEnabled

        /** Panel sizing, chosen by the user rather than fixed. See `ImePanelHeight`. */
        val keyboardHeightPercent: StateFlow<Int> = keyboardPreferences.keyboardHeightPercent
        val keyboardBottomPaddingDp: StateFlow<Int> = keyboardPreferences.keyboardBottomPaddingDp

        /** How large a key is drawn inside its cell. Independent of the panel height. */
        val keySizePercent: StateFlow<Int> = keyboardPreferences.keySizePercent

        /** Whether a second quick space becomes a full stop. */
        val doubleSpacePeriodEnabled: StateFlow<Boolean> =
            keyboardPreferences.doubleSpacePeriodEnabled

        private var currentWord = ""

        /**
         * The word a run of backspaces is eating, kept so it can be offered back.
         *
         * A backspace is the one edit with no undo of its own, and the two ways of losing a
         * whole word are the two where the strip goes empty and leaves nothing to tap: undoing
         * a glide, which removes the entire word in one press, and deleting a typed word down
         * to its last character. Retyping is the only recovery, which for a glide means typing
         * out by hand the word the gesture existed to avoid typing.
         *
         * Captured at the *start* of a run rather than per press: by the time the word is gone
         * the mirror has already shrunk to nothing, so the last thing it held is a single
         * letter rather than the word that was lost.
         */
        private var backspacedWord: String? = null

        /**
         * The word the user last finished, used to rank what comes next.
         *
         * Exactly the same tier as [currentWord]: in memory, in this ViewModel, for this input
         * session. Never persisted, never in a backup scope, never anywhere a dictionary write
         * would go -- it is one word of sentence context, and the moment it outlives the
         * session it becomes a record of what was typed.
         *
         * Set only from [learn], which is the single write gate into the personal dictionary.
         * That placement is the whole safety argument: on a password field, or in incognito,
         * or with the manual privacy switch on, [learn] returns before assigning, so context
         * tracking inherits every existing gate without adding a check that could disagree
         * with them later.
         */
        private var previousWord: String? = null

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

        /** What [atSentenceStart] was before the press currently under the finger. */
        private var atSentenceStartBeforeKey = true

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
            enterAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
        ) {
            backspacedWord = null
            currentWord = ""
            // A different field is a different sentence, in a different app. Carrying the last
            // word over would rank the first suggestion of a new message off whatever the user
            // happened to be writing somewhere else.
            previousWord = null
            _suggestions.value = emptyList()
            _undoState.value = null
            generation++
            detectedFieldKind = fieldKind
            hostNoLearning = noPersonalizedLearning
            enterInsertsNewline = enterIsNewline
            _enterAction.value = enterAction
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

        /**
         * The action this field declared, straight from `EditorInfo.IME_MASK_ACTION`.
         *
         * Drives the Enter key's artwork only; what the key *does* is still decided in
         * `StickyKeysIME.sendEnter` from the same EditorInfo, so the two cannot disagree
         * about the field. Published per session because that is when it can change.
         */
        private val _enterAction = MutableStateFlow(EditorInfo.IME_ACTION_UNSPECIFIED)
        val enterAction: StateFlow<Int> = _enterAction.asStateFlow()

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

        /**
         * Decodes a finished glide, or null if nothing plausible came out of it.
         *
         * Suppressed entirely on a secret, for the same two reasons the suggestion strip is:
         * the decode is a dictionary lookup over what is being typed, and the result would be
         * committed into a field the user cannot read back to check.
         */
        suspend fun decodeGlide(
            stroke: com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlideStroke,
        ): List<String> {
            if (_fieldKind.value.isSensitive) return emptyList()
            return predictionEngine.decodeGlide(stroke, previousWord)
        }

        /**
         * A glided word was committed.
         *
         * Learned like any other finished word, because a glide the user accepted is evidence
         * of how they write. Routed through the same gate, so incognito and private mode
         * suppress it without needing to know glide exists.
         */
        fun onGlideCommitted(
            word: String,
            alternatives: List<String>,
        ) {
            currentWord = ""
            _undoState.value = null
            generation++
            atSentenceStart = false
            publishAutoCapitalize()
            learn(word)
            // The readings that lost stay in the strip.
            //
            // A glide is wrong more often than a tap -- several real words are usually valid
            // readings of the same path, and the engine picks one. Leaving the runners-up
            // where the user is already looking turns a wrong guess into one tap instead of
            // deleting a word and gliding again. This is what the decoder keeps five
            // candidates *for*; without it they were computed and thrown away.
            _suggestions.value = alternatives.filter { it != word }.take(MAX_STRIP_SUGGESTIONS)
            lastGlideCommit = word
            lastGlideGeneration = generation
        }

        private var lastGlideCommit: String? = null
        private var lastGlideGeneration = -1

        /**
         * The word a glide just committed, if the text has not moved since.
         *
         * A glide commits "word " including the trailing space, so the caret sits *after* a
         * space and `wordUnderCaret` correctly reports nothing. Tapping an alternative would
         * then insert rather than replace, leaving both readings in the text. The span has to
         * come from what the glide wrote.
         *
         * Gated on the generation token rather than cleared from every path that could
         * invalidate it. Everything that changes the text bumps that token -- a keystroke, a
         * delete, a space, punctuation, an edit made outside this keyboard -- so one check
         * covers all of them, where a list of explicit clears would silently miss whichever
         * path was added next.
         */
        fun consumeGlideCommit(): String? {
            val word = lastGlideCommit ?: return null
            lastGlideCommit = null
            return if (lastGlideGeneration == generation) word else null
        }

        /** When the shift key was last tapped, for the caps-lock double-tap window. */
        private var lastShiftTapAt = 0L

        /**
         * How long since the previous shift tap, recording this one.
         *
         * Lives here rather than in the view because the view's mode state is rebuilt on every
         * input session, and a timestamp that resets with it would make the first shift tap in
         * a new field behave differently from every other one.
         *
         * Returns [Long.MAX_VALUE] when there is no previous tap, so the first tap of a session
         * can never be read as the second half of a double tap.
         */
        fun consumeShiftTapGap(): Long {
            val now = System.currentTimeMillis()
            val gap = if (lastShiftTapAt == 0L) Long.MAX_VALUE else now - lastShiftTapAt
            lastShiftTapAt = now
            return gap
        }

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
         *
         * Also where [previousWord] is set, deliberately after the incognito gate rather than
         * beside it. Every path that finishes a word already routes through here, so context
         * tracking picks up all of them at once and none of them can grow a second, divergent
         * privacy check later.
         */
        private fun learn(word: String) {
            if (incognitoState.active.value) return
            previousWord = word.lowercase()
            viewModelScope.launch {
                predictionEngine.learnWord(word)
            }
        }

        fun performKeyPressHaptic() {
            hapticsManager.performKeyPressHaptic()
            // Counted here rather than in onKeyPressed: this fires for every key including
            // shift, symbols and enter, which is what "keystrokes" means to a tester.
            usageLog.onKeystroke()
            // The same event, counted a second time for the user's own stats. Two recorders
            // rather than one because only one of them exists in a release build.
            typingStats.recordKeystroke()
        }

        fun onKeyPressed(char: String) {
            // Saved because the key that was just pressed may turn out not to be a keystroke
            // at all -- see onKeyRevoked. One level deep is all that is ever needed: only the
            // press still under the finger can be taken back.
            atSentenceStartBeforeKey = atSentenceStart
            // Typing again means the delete was meant. Holding the word past this point would
            // put a word the user has moved on from at the front of the strip.
            backspacedWord = null
            currentWord += char
            _undoState.value = null // Typing clears undo state
            generation++
            // A letter was typed, so we are no longer at a sentence boundary.
            atSentenceStart = false
            publishAutoCapitalize()
            updateSuggestions()
        }

        /**
         * The letter [onKeyPressed] just committed was not a keystroke after all.
         *
         * Letters now reach the screen when the finger lands rather than when it lifts, which
         * is what removed the dwell-shaped lag from the press-to-letter path. The cost is that
         * two gestures only reveal themselves later: a press that leaves the key is the start
         * of a glide, and one that stays past the long-press window is a hold that commits an
         * alternate instead. Both take the letter back through here.
         *
         * The mirror is un-appended rather than rebuilt, and [atSentenceStart] is restored
         * rather than recomputed, so the state is exactly what it was before the press. The
         * generation still moves: any suggestion lookup dispatched for the letter that is now
         * gone must not be allowed to land.
         *
         * The caller deletes the character from the editor. Nothing is learned or unlearned --
         * a letter that was never a keystroke never reached a word boundary.
         */
        fun onKeyRevoked(char: String) {
            currentWord = currentWord.dropLast(char.length)
            atSentenceStart = atSentenceStartBeforeKey
            generation++
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

        /**
         * How many characters this backspace should remove.
         *
         * Normally 1, meaning an ordinary single-character delete. Immediately after a glide
         * it is the whole committed word plus the space the glide wrote, so one gesture is
         * undone by one press -- a glide put the word there in a single movement and taking
         * it back letter by letter is eight presses to undo one.
         *
         * The count is returned rather than issued here because a ViewModel holds no
         * controller. A caller seeing 1 must still go through `sendDelete`, which sizes the
         * delete against the text itself so a surrogate pair does not lose half of itself.
         *
         * [textBeforeCursor] is the editor's text as it stands **before** this delete lands,
         * with the characters about to be removed still present -- the same shape
         * [onEditorContextChanged] takes, and read by the caller for the same reason.
         *
         * Roadmap 4G.7: every branch below used to guess `atSentenceStart` from [currentWord],
         * a local mirror that is always empty straight after punctuation was committed. So
         * deleting *any* punctuation mark read as landing on a sentence start, and the next
         * letter capitalized mid-word. Whether the caret ends up at a boundary is a property
         * of the text, and only the editor knows it.
         *
         * Defaults to `""` for callers with no editor to read. That resolves to
         * `startsNewSentence("")`, which is `true` -- exactly what all three branches used to
         * hard-code, so a caller that only wants the returned count is unaffected.
         */
        fun onDelete(textBeforeCursor: String = ""): Int {
            usageLog.onBackspace()
            typingStats.recordBackspace()
            // Read before the token moves. `consumeGlideCommit` is valid only while the
            // generation still matches the one the glide committed under, so bumping first
            // would make this return null every time -- and the whole feature silently
            // degrade to an ordinary backspace with nothing looking broken.
            val glided = consumeGlideCommit()
            generation++
            if (glided != null) {
                // A glide clears currentWord and commits its own trailing space, so there is
                // no partial word here to fall through to. Whether the word being undone sat
                // at a sentence start is a property of the text in front of it, not of the
                // glide -- so drop the word and its trailing space and ask about the rest.
                atSentenceStart =
                    startsNewSentence(textBeforeCursor.dropLast(glided.length + 1))
                publishAutoCapitalize()
                // One press removed a whole word, so one tap has to be able to put it back.
                backspacedWord = glided
                _suggestions.value = listOf(glided)
                return glided.length + 1
            }
            if (currentWord.isNotEmpty()) {
                // The first backspace of a run is the only moment the whole word is still
                // known. Later presses must not overwrite it with the shrinking remainder.
                if (backspacedWord == null) backspacedWord = currentWord
                currentWord = currentWord.dropLast(1)
                // Deleting back to nothing puts the caret where a sentence would start again,
                // so capitalization has to come back with it. Forcing this false
                // unconditionally meant clearing a message and retyping it produced a
                // lower-case first letter every time -- but an empty mirror is not the same
                // fact as an empty field, which is what asking the text settles.
                atSentenceStart = startsNewSentence(textBeforeCursor.dropLast(1))
                publishAutoCapitalize()
                if (currentWord.isEmpty()) {
                    // Nothing left to predict from, so the strip would otherwise be empty.
                    // While the word was still shrinking the ordinary prefix suggestions cover
                    // this already -- "hello" is among the completions of "hell".
                    _suggestions.value = listOfNotNull(backspacedWord)
                } else {
                    updateSuggestions()
                }
                return 1
            }
            // The mirror was already empty going in; the common case is straight after
            // punctuation committed and cleared it. Nothing about the character being removed
            // is decidable from here, which is the whole of 4G.7.
            atSentenceStart = startsNewSentence(textBeforeCursor.dropLast(1))
            publishAutoCapitalize()
            return 1
        }

        /**
         * Clears the in-progress word for a space press and returns the input token to
         * validate against later. Deliberately does NOT learn the word: the caller
         * decides that once the async autocorrect check resolves.
         */
        fun onSpacePressed(): Int {
            backspacedWord = null
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
            // The last word of the previous sentence does not predict the first word of the
            // next one -- a bigram across a full stop is two unrelated words that happened to
            // be adjacent, which is exactly the pairing the corpus counts and the user did not
            // mean.
            previousWord = null
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
                // The word just became the context, so ask what usually follows it. This is the
                // exact moment issue #17 describes: a word finished, space pressed, and a strip
                // that had nothing to say because the completion path short-circuits on an empty
                // prefix before it ever consults the context.
                //
                // Here rather than inside `learn` on purpose. A glide also learns, and it fills
                // the strip with its own losing readings straight afterwards -- a refresh hidden
                // in `learn` would dispatch under the same generation token and overwrite them.
                updateSuggestions()
            }
        }

        /** Autocorrect lookup against an explicit word, so it cannot race [currentWord]. */
        suspend fun getAutoCorrectionFor(word: String): String? {
            // Never on a secret, for two separate reasons: the lookup itself puts the password
            // through the dictionary, and a correction that fired would silently rewrite what
            // the user typed into a field where they cannot see it to check.
            if (_fieldKind.value.isSensitive) return null
            if (word.isBlank() || !keyboardPreferences.autoCorrectEnabled.value) return null
            return predictionEngine.getAutoCorrection(word, previousWord)
        }

        fun onAutoCorrected(
            original: String,
            corrected: String,
        ) {
            usageLog.onAutocorrectAccepted()
            typingStats.recordAutocorrectAccepted()
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
            typingStats.recordAutocorrectUndone()
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
            // Re-derived from the editor for the same reason [currentWord] is: after a caret
            // tap or a paste, whatever this keyboard last learned describes a sentence the
            // caret is no longer in, and ranking the next word off it is worse than having no
            // context at all.
            previousWord = precedingWord(textBeforeCursor)
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
            // The tap wrote the word and its trailing space, so the caret is where it would be
            // after a space bar press and the strip should say the same thing it would there.
            // Without this, taking a suggestion left the strip permanently blank until the next
            // keystroke -- which reads as the tap having broken something.
            updateSuggestions()
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
            if (_fieldKind.value.isSensitive) {
                _suggestions.value = emptyList()
                return
            }
            // Captured before the lookup is dispatched and re-checked before the result is
            // published. Two of these can be in flight at once -- a next-word lookup fired when
            // the space committed, and a completion lookup for the letter typed a moment later
            // -- and without this the slower one wins whichever it is, so the strip can fill
            // with predictions for a word the user has already started typing past.
            val token = generation
            val prefix = currentWord
            viewModelScope.launch {
                val results =
                    if (prefix.isBlank()) {
                        // Nothing typed: predict rather than complete. Suppressed at a sentence
                        // boundary, where the previous word is on the far side of a full stop --
                        // the corpus counts that pair because they were adjacent, not because
                        // one follows the other, and it is the same reason `onSentenceStarted`
                        // drops the context outright.
                        if (atSentenceStart) {
                            emptyList()
                        } else {
                            predictionEngine.getNextWordSuggestions(previousWord)
                        }
                    } else {
                        predictionEngine.getSuggestions(prefix, previousWord)
                    }
                if (token == generation) {
                    _suggestions.value = results
                }
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
 * How many characters a word-wise backspace should remove, given the text before the caret.
 *
 * Trailing whitespace first, then the run of non-whitespace before it -- so a caret just after
 * "hello world " takes back "world " whole, and one sitting mid-word takes back the part that
 * has been typed. That is what ctrl-backspace does in every editor, and matching it is the
 * point: the gesture is new, what counts as a word should not be.
 *
 * Sized from text read at the moment of the edit rather than from `currentWord`. That mirror is
 * built by appending on each key press and is correct only while this keyboard is the sole
 * editor -- a caret tap, a paste or a host-side edit leaves it describing text that is gone, and
 * a destructive edit trusting it would delete the wrong span.
 *
 * Whitespace rather than letters decides the boundary, unlike [wordUnderCaret]. A backspace
 * swipe over "don't" or "e-mail" should take the whole thing, and over ", and" should take the
 * punctuation with it, because that is what the finger passed over.
 *
 * Pure, so the boundary walk is assertable without an InputConnection.
 */
internal fun wordDeleteLength(textBeforeCursor: String): Int {
    val trailingSpace = textBeforeCursor.takeLastWhile { it.isWhitespace() }.length
    val word =
        textBeforeCursor
            .dropLast(trailingSpace)
            .takeLastWhile { !it.isWhitespace() }
            .length
    return trailingSpace + word
}

/**
 * The finished word before the one the caret is in, or null when there is none.
 *
 * Pure and separate for the same reason [wordUnderCaret] is, though for a gentler failure: an
 * off-by-one here mis-ranks a suggestion rather than eating a character. Null is returned
 * across a sentence boundary as well as at the start of the text, because a word on the far
 * side of a full stop is not context for this one -- see the reset in `onSentenceStarted`,
 * which this has to agree with or a caret tap would resurrect context that typing had dropped.
 */
internal fun precedingWord(textBeforeCursor: String): String? {
    val beforeCurrent = textBeforeCursor.dropLast(wordUnderCaret(textBeforeCursor).length)
    val trimmed = beforeCurrent.trimEnd { it == ' ' }
    if (trimmed.isEmpty()) return null
    // Checked before any further trimming, or the terminator would be stripped as ordinary
    // punctuation and the sentence boundary would vanish along with it.
    if (trimmed.last() in SENTENCE_ENDINGS || trimmed.last() == '\n') return null
    // A comma, a bracket or a quote ends a word without ending a sentence, so the word it
    // follows is still context. wordUnderCaret stops at the first non-letter, so without this
    // "well, th" would report no previous word at all.
    val word = wordUnderCaret(trimmed.trimEnd { !it.isLetter() && it != '\'' })
    return word.ifEmpty { null }?.lowercase()
}

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

/** How many losing glide readings the suggestion strip holds. */
private const val MAX_STRIP_SUGGESTIONS = 3

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
