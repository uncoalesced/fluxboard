// Engineered by uncoalesced
package com.uncoalesced.stickykeys.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(
                "app_preferences",
                Context.MODE_PRIVATE,
            )

        // No OnSharedPreferenceChangeListener, for the reason given at length in
        // KeyboardPreferences: SharedPreferences holds listeners weakly, R8 removes the field
        // that was the only strong reference to ours, and the listener is then collected in
        // release builds only. Each setter publishes to its own flow instead.

        private val _defaultExportFormat =
            MutableStateFlow(
                prefs.getString("default_export_format", "image/webp") ?: "image/webp",
            )
        val defaultExportFormat: StateFlow<String> = _defaultExportFormat.asStateFlow()

        fun setDefaultExportFormat(format: String) {
            prefs.edit().putString("default_export_format", format).apply()
            _defaultExportFormat.value = format
        }

        private fun readThemeMode(): ThemeMode =
            ThemeMode.fromStored(prefs.getString("theme_mode", null))

        private val _themeMode = MutableStateFlow(readThemeMode())

        /**
         * Which palette the app's own UI uses. The light palette existed but nothing ever
         * selected it -- the whole app was hardcoded dark with no way to change it and
         * nothing persisted.
         */
        val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

        fun setThemeMode(mode: ThemeMode) {
            prefs.edit().putString("theme_mode", mode.name).apply()
            _themeMode.value = mode
        }

        /**
         * True once the user has opened the theme or layout editor at least once.
         *
         * The trigger for the "make FluxBoard your default" prompt: asking the moment the
         * keyboard is merely enabled is asking before the user has any reason to say yes,
         * so the prompt waits until they have actually engaged with customisation.
         */
        val hasVisitedCustomizer: Boolean
            get() = prefs.getBoolean("visited_customizer", false)

        fun markCustomizerVisited() {
            prefs.edit().putBoolean("visited_customizer", true).apply()
        }

        /** True once the default-keyboard prompt has been shown, whatever the answer was. */
        val hasShownDefaultKeyboardPrompt: Boolean
            get() = prefs.getBoolean("shown_default_kb_prompt", false)

        /** Recorded when the prompt is displayed, not when it is accepted, so it never
         *  reappears regardless of what the user chose. */
        fun markDefaultKeyboardPromptShown() {
            prefs.edit().putBoolean("shown_default_kb_prompt", true).apply()
        }
    }

/** App-level theme selection, persisted across launches. */
enum class ThemeMode {
    /** Follow the OS light/dark setting. */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /** Defaults to [DARK], which is what the app shipped as before this was settable. */
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: DARK
    }
}
