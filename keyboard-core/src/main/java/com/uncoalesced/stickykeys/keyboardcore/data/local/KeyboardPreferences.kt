// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local

import android.content.Context
import android.content.SharedPreferences
import com.uncoalesced.stickykeys.keyboardcore.haptics.DEFAULT_HAPTICS_PERCENT
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(
                "keyboard_preferences",
                Context.MODE_PRIVATE,
            )

        // There is deliberately no OnSharedPreferenceChangeListener here. Every setter below
        // publishes to its own flow directly, which is the only mechanism.
        //
        // There used to be one, and in a release build it silently did nothing.
        // SharedPreferences keeps its listeners in a WeakHashMap, so the `private val listener`
        // field was the only strong reference to ours -- and R8, seeing a field written once and
        // read once, inlined the field away. Nothing then held the listener, the first GC
        // collected it, and from that moment no preference change reached any flow in the
        // process. Nothing looked broken: writes still landed on disk and were picked up on the
        // next launch, so every toggle in Settings appeared dead until the app was restarted,
        // and the privacy switch in the quick-access row could be turned on and never off.
        // Confirmed against outputs/mapping/release/mapping.txt, which lists `prefs` and every
        // flow field for this class and no `listener` field at all.
        //
        // This class is a @Singleton and every write in the app goes through it (checked, not
        // assumed), so a listener was never doing anything a setter could not. Publishing from
        // the setter cannot be optimized away, because the flow it writes is read elsewhere.

        private val _autoCapitalizeEnabled =
            MutableStateFlow(prefs.getBoolean("auto_capitalize", true))
        val autoCapitalizeEnabled: StateFlow<Boolean> = _autoCapitalizeEnabled.asStateFlow()

        private val _autoCorrectEnabled = MutableStateFlow(prefs.getBoolean("auto_correct", true))
        val autoCorrectEnabled: StateFlow<Boolean> = _autoCorrectEnabled.asStateFlow()

        private val _activeThemeId =
            MutableStateFlow(
                prefs.getString("active_theme_id", "preset_default_dark") ?: "preset_default_dark",
            )
        val activeThemeId: StateFlow<String> = _activeThemeId.asStateFlow()

        private val _activeLayoutId =
            MutableStateFlow(
                prefs.getString("active_layout_id", "preset_qwerty") ?: "preset_qwerty",
            )
        val activeLayoutId: StateFlow<String> = _activeLayoutId.asStateFlow()

        private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean("haptics_enabled", true))
        val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

        /**
         * Haptic strength as a slider percentage, 0-100.
         *
         * This used to be a raw motor amplitude in 1..255 written straight into
         * `VibrationEffect.createOneShot`, which made the setting device-dependent and made
         * zero unreachable -- the slider could not turn haptics off. Values left over from
         * that scale are clamped rather than rescaled: the only way to hold one is to have
         * moved the old slider, and clamping is both cheap and monotonic.
         */
        private val _hapticsIntensity = MutableStateFlow(readHapticsPercent())
        val hapticsIntensity: StateFlow<Int> = _hapticsIntensity.asStateFlow()

        private fun readHapticsPercent(): Int =
            prefs
                .getInt("haptics_intensity", DEFAULT_HAPTICS_PERCENT)
                .coerceIn(0, 100)

        /**
         * Whether the always-visible 1-0 row is shown above the letters.
         *
         * Defaults on, matching the reference layout. Independent of the corner hints, which
         * carry symbols rather than digits -- turning this off does not put numbers back
         * within reach of a long press, so the two settings are not substitutes.
         */
        private val _showNumberRow = MutableStateFlow(prefs.getBoolean("show_number_row", true))
        val showNumberRow: StateFlow<Boolean> = _showNumberRow.asStateFlow()

        /**
         * Whether two quick spaces become a full stop and a space.
         *
         * A setting rather than a constant because some hosts and OEM text fields already do
         * this themselves, which produces a doubled period, and an IME cannot detect that.
         */
        private val _doubleSpacePeriodEnabled =
            MutableStateFlow(prefs.getBoolean("double_space_period", true))
        val doubleSpacePeriodEnabled: StateFlow<Boolean> =
            _doubleSpacePeriodEnabled.asStateFlow()

        /**
         * How large each key is drawn inside the cell the layout gives it, as a percentage.
         *
         * Independent of [keyboardHeightPercent] on purpose: that one changes how much of the
         * screen the keyboard occupies, this one changes how much of that space is key rather
         * than gap. A user who wants a big keyboard with generous gaps and one who wants a
         * compact keyboard with fat keys are asking for different things.
         */
        private val _keySizePercent = MutableStateFlow(readKeySizePercent())
        val keySizePercent: StateFlow<Int> = _keySizePercent.asStateFlow()

        /**
         * Overall keyboard height, as a percentage of the shipped default.
         *
         * The panel used to be one fixed dimension for everyone, which made the number row a
         * forced trade: turning it on divided the same height across five rows instead of
         * four, so every key lost a fifth of its height and the board became noticeably harder
         * to hit. Thumb length and screen size vary far too much for one number to be right,
         * so this is the user's call rather than a compromise chosen for them.
         */
        private val _keyboardHeightPercent = MutableStateFlow(readHeightPercent())
        val keyboardHeightPercent: StateFlow<Int> = _keyboardHeightPercent.asStateFlow()

        /**
         * Extra space between the bottom key row and the gesture bar, in dp.
         *
         * Grows the window rather than shrinking the panel, so raising it never costs key
         * height. The default matches the value tuned on device.
         */
        private val _keyboardBottomPaddingDp = MutableStateFlow(readBottomPadding())
        val keyboardBottomPaddingDp: StateFlow<Int> = _keyboardBottomPaddingDp.asStateFlow()

        /**
         * The user's manual privacy switch. Off by default.
         *
         * This is the half of the privacy story that no `EditorInfo` signal can reach. A
         * password field announces itself through `inputType`, and a host that sets
         * IME_FLAG_NO_PERSONALIZED_LEARNING announces itself through `imeOptions` -- but an
         * ordinary message box holding a recovery phrase, a diagnosis or somebody else's
         * address looks exactly like every other message box. There is no signal to read, so
         * there is no honest automatic answer, and the switch is deliberately manual rather
         * than a heuristic on package names dressed up as detection.
         *
         * Persisted rather than session-scoped, and that direction is chosen on purpose: a
         * privacy switch that turns itself off when the user taps into the next field fails
         * *open*, which is the failure that leaks. Leaving it on costs suggestions until it is
         * turned off, and the lock in the suggestion strip stays lit the whole time saying so.
         */
        private val _privateModeEnabled =
            MutableStateFlow(prefs.getBoolean("private_mode", false))
        val privateModeEnabled: StateFlow<Boolean> = _privateModeEnabled.asStateFlow()

        /**
         * Whether letter keys show their symbol in the top-left corner.
         *
         * On by default: the corner symbols are how the punctuation on this keyboard is found
         * at all, and a board that hides them by default would look cleaner and type worse.
         * Off is for people who already know where things are and want the quieter board.
         *
         * Presentation only. Long-press still produces the symbol whether or not it is drawn.
         */
        private val _showKeyHints = MutableStateFlow(prefs.getBoolean("show_key_hints", true))
        val showKeyHints: StateFlow<Boolean> = _showKeyHints.asStateFlow()

        /**
         * Whether swiping across the letters types a word.
         *
         * On by default. Off restores the previous pointer handling exactly rather than
         * running the gesture and discarding it, which matters for anyone whose grip drags
         * across keys on the way to a tap.
         */
        private val _glideTypingEnabled =
            MutableStateFlow(prefs.getBoolean("glide_typing", true))
        val glideTypingEnabled: StateFlow<Boolean> = _glideTypingEnabled.asStateFlow()

        // The "media_metadata" key is deliberately not read or written any more. It backed a
        // v0.1.6-BETA opt-in for showing the playing track, which needed a
        // NotificationListenerService -- and Play Protect blocks the install of any sideloaded
        // app that declares one. The service is gone (see keyboard-core's manifest), so the
        // preference has nothing left to enable. Any stored value is simply ignored.

        /** Turns glide typing on or off. */
        fun setGlideTyping(enabled: Boolean) {
            prefs.edit().putBoolean("glide_typing", enabled).apply()
            _glideTypingEnabled.value = enabled
        }

        private fun readKeySizePercent(): Int =
            prefs
                .getInt("key_size_percent", DEFAULT_KEY_SIZE_PERCENT)
                .coerceIn(MIN_KEY_SIZE_PERCENT, MAX_KEY_SIZE_PERCENT)

        private fun readHeightPercent(): Int =
            prefs
                .getInt("keyboard_height_percent", DEFAULT_KEYBOARD_HEIGHT_PERCENT)
                .coerceIn(MIN_KEYBOARD_HEIGHT_PERCENT, MAX_KEYBOARD_HEIGHT_PERCENT)

        private fun readBottomPadding(): Int =
            prefs
                .getInt("keyboard_bottom_padding_dp", DEFAULT_BOTTOM_PADDING_DP)
                .coerceIn(0, MAX_BOTTOM_PADDING_DP)

        fun setAutoCapitalize(enabled: Boolean) {
            prefs.edit().putBoolean("auto_capitalize", enabled).apply()
            _autoCapitalizeEnabled.value = enabled
        }

        fun setAutoCorrect(enabled: Boolean) {
            prefs.edit().putBoolean("auto_correct", enabled).apply()
            _autoCorrectEnabled.value = enabled
        }

        fun setActiveThemeId(themeId: String) {
            prefs.edit().putString("active_theme_id", themeId).apply()
            _activeThemeId.value = themeId
        }

        fun setActiveLayoutId(layoutId: String) {
            prefs.edit().putString("active_layout_id", layoutId).apply()
            _activeLayoutId.value = layoutId
        }

        fun setHapticsEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("haptics_enabled", enabled).apply()
            _hapticsEnabled.value = enabled
        }

        /**
         * The emoji the user has actually sent, most recent first.
         *
         * Kept in SharedPreferences rather than Room because it is a short, ordered,
         * whole-value list that is rewritten on every use -- a table with one row per glyph
         * would be a migration and a DAO for something that is always read in full.
         *
         * Not gated on incognito: an emoji is not text the user typed, it is a picker choice,
         * and the same reasoning that keeps clipboard capture out of a password field does not
         * apply to tapping a smiley. Flagged here rather than assumed -- say so if that is
         * the wrong call.
         *
         * The order returned is deliberately held still for [EMOJI_PROMOTION_DELAY_MS] after a
         * use. Promoting on every tap moved the grid out from under the finger mid-burst, and
         * a burst is the common case -- reacting with three emoji in a row meant the second
         * and third taps landed on a strip that had just reflowed. Only the *read* is held
         * back; see [recordEmojiUse] for why the write is not.
         */
        fun recentEmoji(): List<String> {
            val frozen = frozenRecentOrder ?: return storedRecentEmoji()
            if (clock() - frozenRecentAt >= EMOJI_PROMOTION_DELAY_MS) {
                frozenRecentOrder = null
                return storedRecentEmoji()
            }
            // A glyph used during the freeze is already stored and appears the moment the
            // freeze lifts. It is deliberately not spliced in here: inserting it would move
            // every other cell, which is the exact thing the freeze exists to prevent.
            return frozen
        }

        /**
         * Records a use immediately, and freezes the order [recentEmoji] reports.
         *
         * The write is not deferred, which is a deliberate departure from the original sketch
         * for this feature -- that one held the promotion in memory and applied it after the
         * delay had passed. An IME is a service the system kills freely, and the most common
         * emoji flow is tap-emoji-then-send, which closes the keyboard within a second, so a
         * deferred write would have dropped most emoji out of Recents altogether. Freezing the
         * read gives the same steady grid with nothing at risk.
         */
        fun recordEmojiUse(glyph: String) {
            if (glyph.isEmpty()) return
            val now = clock()
            // Snapshot once per burst, before the write, so the held order is the one the
            // finger was aiming at rather than one a previous tap had already moved.
            if (frozenRecentOrder == null || now - frozenRecentAt >= EMOJI_PROMOTION_DELAY_MS) {
                frozenRecentOrder = storedRecentEmoji()
            }
            // Each further tap restarts the window, so a burst settles once, after it ends.
            frozenRecentAt = now
            val updated =
                (listOf(glyph) + storedRecentEmoji().filterNot { it == glyph })
                    .take(MAX_RECENT_EMOJI)
            prefs
                .edit()
                .putString(KEY_RECENT_EMOJI, updated.joinToString(EMOJI_SEPARATOR))
                .apply()
        }

        /**
         * Drops the held order, so the next [recentEmoji] reports what is actually stored.
         *
         * The freeze lapses on a clock, but nothing in the picker re-reads on a clock -- it
         * reads once when it opens and again on each use. So a promotion the freeze deferred
         * stayed invisible while the keyboard was closed, survived into the next session as a
         * stale grid, and then landed all at once under the user's next tap. Every cell moving
         * at the moment a finger comes down, once per session and never again in that session,
         * is what the report "the emoji randomly shuffle" describes.
         *
         * Reopening the picker is the moment there is no burst left to protect, so it is the
         * moment to settle. The freeze is about a finger already in flight, not about the
         * order being sticky in general.
         */
        fun settleRecentEmoji() {
            frozenRecentOrder = null
        }

        private var frozenRecentOrder: List<String>? = null
        private var frozenRecentAt: Long = 0L

        /** Swappable so the freeze window is testable without sleeping in a unit test. */
        internal var clock: () -> Long = System::currentTimeMillis

        private fun storedRecentEmoji(): List<String> =
            prefs
                .getString(KEY_RECENT_EMOJI, "")
                .orEmpty()
                .split(EMOJI_SEPARATOR)
                .filter { it.isNotEmpty() }

        fun setDoubleSpacePeriod(enabled: Boolean) {
            prefs.edit().putBoolean("double_space_period", enabled).apply()
            _doubleSpacePeriodEnabled.value = enabled
        }

        // The clamped value is computed once and used for both the write and the flow. Clamping
        // twice from two expressions is how the stored number and the published one drift.

        /** [percent] scales the key inside its cell. 100 is the shipped size. */
        fun setKeySizePercent(percent: Int) {
            val clamped = percent.coerceIn(MIN_KEY_SIZE_PERCENT, MAX_KEY_SIZE_PERCENT)
            prefs.edit().putInt("key_size_percent", clamped).apply()
            _keySizePercent.value = clamped
        }

        fun setShowNumberRow(show: Boolean) {
            prefs.edit().putBoolean("show_number_row", show).apply()
            _showNumberRow.value = show
        }

        /** [intensity] is a slider percentage, 0-100. Zero is valid and means silent. */
        fun setHapticsIntensity(intensity: Int) {
            val clamped = intensity.coerceIn(0, 100)
            prefs.edit().putInt("haptics_intensity", clamped).apply()
            _hapticsIntensity.value = clamped
        }

        /** [percent] scales the whole panel. 100 is the shipped height. */
        fun setKeyboardHeightPercent(percent: Int) {
            val clamped =
                percent.coerceIn(MIN_KEYBOARD_HEIGHT_PERCENT, MAX_KEYBOARD_HEIGHT_PERCENT)
            prefs.edit().putInt("keyboard_height_percent", clamped).apply()
            _keyboardHeightPercent.value = clamped
        }

        /** Shows or hides the corner symbols. Does not change what long-press types. */
        fun setShowKeyHints(show: Boolean) {
            prefs.edit().putBoolean("show_key_hints", show).apply()
            _showKeyHints.value = show
        }

        /** Turns the manual privacy switch on or off. See [privateModeEnabled]. */
        fun setPrivateMode(enabled: Boolean) {
            prefs.edit().putBoolean("private_mode", enabled).apply()
            _privateModeEnabled.value = enabled
        }

        /** [dp] is the gap held below the bottom key row, above the gesture bar. */
        fun setKeyboardBottomPaddingDp(dp: Int) {
            val clamped = dp.coerceIn(0, MAX_BOTTOM_PADDING_DP)
            prefs.edit().putInt("keyboard_bottom_padding_dp", clamped).apply()
            _keyboardBottomPaddingDp.value = clamped
        }

        companion object {
            /** One screenful of the picker grid; beyond that the tab stops being "recent". */
            const val MAX_RECENT_EMOJI = 32

            /**
             * How long the Recents grid holds its order after a use.
             *
             * Long enough to cover a multi-emoji reaction typed at speed, short enough
             * that the grid has settled by the next time the picker is opened.
             */
            const val EMOJI_PROMOTION_DELAY_MS = 3_000L

            /**
             * ASCII unit separator. No emoji, ZWJ sequence or skin-tone modifier can
             * contain it, so a glyph can never be split across two entries. Written as
             * an escape rather than the literal control byte it replaces, which was
             * invisible in a diff and in most editors.
             */
            private const val EMOJI_SEPARATOR = "\u001F"

            private const val KEY_RECENT_EMOJI = "recent_emoji"

            const val DEFAULT_KEY_SIZE_PERCENT = 100

            /**
             * Below this the gaps swallow the key; above it neighbouring keys touch and the
             * boundary between them stops being visible, which costs accuracy rather than
             * adding it.
             */
            const val MIN_KEY_SIZE_PERCENT = 70
            const val MAX_KEY_SIZE_PERCENT = 130

            const val DEFAULT_KEYBOARD_HEIGHT_PERCENT = 100

            /** Below this the keys are too short to hit; above it the host app disappears. */
            const val MIN_KEYBOARD_HEIGHT_PERCENT = 70
            const val MAX_KEYBOARD_HEIGHT_PERCENT = 150

            /** Tuned on device; the gesture-bar inset alone leaves the bottom row flush. */
            const val DEFAULT_BOTTOM_PADDING_DP = 12
            const val MAX_BOTTOM_PADDING_DP = 48
        }
    }
