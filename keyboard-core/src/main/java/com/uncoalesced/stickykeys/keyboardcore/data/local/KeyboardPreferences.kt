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

        private val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    "auto_capitalize" ->
                        _autoCapitalizeEnabled.value =
                            prefs.getBoolean("auto_capitalize", true)
                    "auto_correct" ->
                        _autoCorrectEnabled.value =
                            prefs.getBoolean("auto_correct", true)
                    "active_theme_id" ->
                        _activeThemeId.value =
                            prefs.getString("active_theme_id", "preset_default_dark")
                                ?: "preset_default_dark"
                    "active_layout_id" ->
                        _activeLayoutId.value =
                            prefs.getString("active_layout_id", "preset_qwerty") ?: "preset_qwerty"
                    "haptics_enabled" ->
                        _hapticsEnabled.value =
                            prefs.getBoolean("haptics_enabled", true)
                    "haptics_intensity" ->
                        _hapticsIntensity.value = readHapticsPercent()
                    "show_number_row" ->
                        _showNumberRow.value =
                            prefs.getBoolean("show_number_row", true)
                    "keyboard_height_percent" ->
                        _keyboardHeightPercent.value = readHeightPercent()
                    "keyboard_bottom_padding_dp" ->
                        _keyboardBottomPaddingDp.value = readBottomPadding()
                }
            }

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

        private fun readHeightPercent(): Int =
            prefs
                .getInt("keyboard_height_percent", DEFAULT_KEYBOARD_HEIGHT_PERCENT)
                .coerceIn(MIN_KEYBOARD_HEIGHT_PERCENT, MAX_KEYBOARD_HEIGHT_PERCENT)

        private fun readBottomPadding(): Int =
            prefs
                .getInt("keyboard_bottom_padding_dp", DEFAULT_BOTTOM_PADDING_DP)
                .coerceIn(0, MAX_BOTTOM_PADDING_DP)

        init {
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }

        fun setAutoCapitalize(enabled: Boolean) {
            prefs.edit().putBoolean("auto_capitalize", enabled).apply()
        }

        fun setAutoCorrect(enabled: Boolean) {
            prefs.edit().putBoolean("auto_correct", enabled).apply()
        }

        fun setActiveThemeId(themeId: String) {
            prefs.edit().putString("active_theme_id", themeId).apply()
        }

        fun setActiveLayoutId(layoutId: String) {
            prefs.edit().putString("active_layout_id", layoutId).apply()
        }

        fun setHapticsEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("haptics_enabled", enabled).apply()
        }

        fun setShowNumberRow(show: Boolean) {
            prefs.edit().putBoolean("show_number_row", show).apply()
        }

        /** [intensity] is a slider percentage, 0-100. Zero is valid and means silent. */
        fun setHapticsIntensity(intensity: Int) {
            prefs.edit().putInt("haptics_intensity", intensity.coerceIn(0, 100)).apply()
        }

        /** [percent] scales the whole panel. 100 is the shipped height. */
        fun setKeyboardHeightPercent(percent: Int) {
            prefs
                .edit()
                .putInt(
                    "keyboard_height_percent",
                    percent.coerceIn(MIN_KEYBOARD_HEIGHT_PERCENT, MAX_KEYBOARD_HEIGHT_PERCENT),
                ).apply()
        }

        /** [dp] is the gap held below the bottom key row, above the gesture bar. */
        fun setKeyboardBottomPaddingDp(dp: Int) {
            prefs
                .edit()
                .putInt("keyboard_bottom_padding_dp", dp.coerceIn(0, MAX_BOTTOM_PADDING_DP))
                .apply()
        }

        companion object {
            const val DEFAULT_KEYBOARD_HEIGHT_PERCENT = 100

            /** Below this the keys are too short to hit; above it the host app disappears. */
            const val MIN_KEYBOARD_HEIGHT_PERCENT = 70
            const val MAX_KEYBOARD_HEIGHT_PERCENT = 150

            /** Tuned on device; the gesture-bar inset alone leaves the bottom row flush. */
            const val DEFAULT_BOTTOM_PADDING_DP = 12
            const val MAX_BOTTOM_PADDING_DP = 48
        }
    }
