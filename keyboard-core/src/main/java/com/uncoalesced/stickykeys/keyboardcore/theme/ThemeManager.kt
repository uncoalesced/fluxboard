// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import android.content.Context
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val keyboardPreferences: KeyboardPreferences,
    ) {
        private val coroutineScope = CoroutineScope(Dispatchers.IO)

        private val _availableThemes = MutableStateFlow<List<KeyboardTheme>>(emptyList())
        val availableThemes: StateFlow<List<KeyboardTheme>> = _availableThemes.asStateFlow()

        private val _activeTheme = MutableStateFlow<KeyboardTheme?>(null)
        val activeTheme: StateFlow<KeyboardTheme?> = _activeTheme.asStateFlow()

        private val customThemesDir = File(context.filesDir, "themes")
        private val backgroundsDir = File(context.filesDir, "theme_backgrounds")

        init {
            if (!customThemesDir.exists()) {
                customThemesDir.mkdirs()
            }
            if (!backgroundsDir.exists()) {
                backgroundsDir.mkdirs()
            }

            // Load all themes on init
            coroutineScope.launch {
                loadThemes()
            }

            // Observe preference changes to update the active theme flow
            keyboardPreferences.activeThemeId
                .onEach { themeId -> resolveActive(_availableThemes.value, themeId) }
                .launchIn(coroutineScope)

            _availableThemes
                .onEach { themes ->
                    resolveActive(themes, keyboardPreferences.activeThemeId.value)
                }.launchIn(coroutineScope)
        }

        /**
         * Publishes the active theme, and never leaves it unresolved.
         *
         * Both observers used to be `if (theme != null) set it` -- so an active-theme id that
         * matched nothing left the previous value in place, and at startup that value is
         * `null`. The id is held in SharedPreferences and the theme files in `filesDir`, so a
         * pointer to a theme that no longer parses (or was deleted outside the app) survives
         * force-stop, cache clearing and reboot, and is cleared only by uninstalling. Every
         * launch then resolved to nothing.
         *
         * Three things happen here that did not before: an unresolvable id falls back rather
         * than being ignored, the stored preference is repaired so the device does not start
         * from the same broken state again, and whatever is resolved is passed through
         * [KeyboardTheme.sanitized] so a theme that loads but paints an unreadable keyboard
         * cannot reach the renderer either.
         */
        private fun resolveActive(
            themes: List<KeyboardTheme>,
            themeId: String,
        ) {
            if (themes.isEmpty()) return // nothing loaded yet; the availableThemes observer retries

            val requested = themes.find { it.id == themeId }
            if (requested != null) {
                _activeTheme.value = requested.sanitized()
                return
            }

            val replacement =
                themes.find { it.id == KeyboardTheme.FALLBACK_DARK_ID }
                    ?: themes.firstOrNull()
                    ?: KeyboardTheme.fallback()
            _activeTheme.value = replacement.sanitized()

            // Repair the stored pointer, so the next launch starts from a valid id instead of
            // rediscovering the same dangling one.
            if (replacement.id != themeId) {
                keyboardPreferences.setActiveThemeId(replacement.id)
            }
        }

        private suspend fun loadThemes() {
            val themes = mutableListOf<KeyboardTheme>()

            // 1. Load built-ins from assets
            withContext(Dispatchers.IO) {
                try {
                    val assetNames = context.assets.list("themes") ?: emptyArray()
                    for (assetName in assetNames) {
                        if (assetName.endsWith(".json")) {
                            val jsonStr =
                                context.assets
                                    .open(
                                        "themes/$assetName",
                                    ).bufferedReader()
                                    .use { it.readText() }
                            try {
                                themes.add(KeyboardTheme.fromJson(jsonStr))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. Load custom themes from filesDir
                val files = customThemesDir.listFiles() ?: emptyArray()
                for (file in files) {
                    if (file.name.endsWith(".json")) {
                        try {
                            val jsonStr = file.readText()
                            themes.add(KeyboardTheme.fromJson(jsonStr))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Moved aside rather than left in place. A file that cannot be
                            // parsed will not start parsing later, and leaving it means every
                            // load re-reads it, re-throws, and re-skips -- while the active
                            // theme preference may still point at the id it was supposed to
                            // provide. Renaming breaks that loop and keeps the bytes around
                            // for anyone who wants to look at them.
                            runCatching {
                                file.renameTo(File(customThemesDir, "${file.name}.corrupt"))
                            }
                        }
                    }
                }

                // Never publish an empty list. If the asset presets failed to load -- a
                // packaging fault, or an OEM asset-manager quirk -- the only theme this
                // keyboard would ever see is one it cannot resolve, and it would render
                // whatever null resolves to on every surface.
                if (themes.isEmpty()) {
                    themes.add(KeyboardTheme.fallback())
                }

                _availableThemes.value = themes
            }
        }

        suspend fun saveCustomTheme(theme: KeyboardTheme) {
            withContext(Dispatchers.IO) {
                val file = File(customThemesDir, "${theme.id}.json")
                file.writeText(theme.toJson().toString(2))

                // Reload
                loadThemes()

                // Set as active
                keyboardPreferences.setActiveThemeId(theme.id)
            }
        }

        suspend fun deleteCustomTheme(themeId: String) {
            withContext(Dispatchers.IO) {
                if (themeId.startsWith("preset_")) return@withContext // Prevent deleting presets

                val file = File(customThemesDir, "$themeId.json")
                if (file.exists()) {
                    file.delete()

                    val bgFile = File(backgroundsDir, "$themeId.png")
                    if (bgFile.exists()) {
                        bgFile.delete()
                    }

                    // If it was active, fallback to default_dark
                    if (keyboardPreferences.activeThemeId.value == themeId) {
                        keyboardPreferences.setActiveThemeId("preset_default_dark")
                    }
                    loadThemes()
                }
            }
        }

        suspend fun saveThemeBackgroundImage(
            inputStream: java.io.InputStream,
            themeId: String,
        ): String =
            withContext(Dispatchers.IO) {
                val bgFile = File(backgroundsDir, "$themeId.png")
                bgFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                bgFile.absolutePath
            }
    }
