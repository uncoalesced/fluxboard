// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "keyboard_preferences",
        Context.MODE_PRIVATE
    )

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "auto_capitalize" -> _autoCapitalizeEnabled.value = prefs.getBoolean("auto_capitalize", true)
            "auto_correct" -> _autoCorrectEnabled.value = prefs.getBoolean("auto_correct", true)
            "active_theme_id" -> _activeThemeId.value = prefs.getString("active_theme_id", "preset_default_dark") ?: "preset_default_dark"
            "active_layout_id" -> _activeLayoutId.value = prefs.getString("active_layout_id", "preset_qwerty") ?: "preset_qwerty"
            "haptics_enabled" -> _hapticsEnabled.value = prefs.getBoolean("haptics_enabled", true)
            "haptics_intensity" -> _hapticsIntensity.value = prefs.getInt("haptics_intensity", 50)
        }
    }

    private val _autoCapitalizeEnabled = MutableStateFlow(prefs.getBoolean("auto_capitalize", true))
    val autoCapitalizeEnabled: StateFlow<Boolean> = _autoCapitalizeEnabled.asStateFlow()

    private val _autoCorrectEnabled = MutableStateFlow(prefs.getBoolean("auto_correct", true))
    val autoCorrectEnabled: StateFlow<Boolean> = _autoCorrectEnabled.asStateFlow()

    private val _activeThemeId = MutableStateFlow(prefs.getString("active_theme_id", "preset_default_dark") ?: "preset_default_dark")
    val activeThemeId: StateFlow<String> = _activeThemeId.asStateFlow()

    private val _activeLayoutId = MutableStateFlow(prefs.getString("active_layout_id", "preset_qwerty") ?: "preset_qwerty")
    val activeLayoutId: StateFlow<String> = _activeLayoutId.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean("haptics_enabled", true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _hapticsIntensity = MutableStateFlow(prefs.getInt("haptics_intensity", 50))
    val hapticsIntensity: StateFlow<Int> = _hapticsIntensity.asStateFlow()

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

    fun setHapticsIntensity(intensity: Int) {
        prefs.edit().putInt("haptics_intensity", intensity.coerceIn(1, 255)).apply()
    }
}
