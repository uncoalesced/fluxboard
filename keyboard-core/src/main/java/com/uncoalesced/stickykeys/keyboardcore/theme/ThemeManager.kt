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
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyboardPreferences: KeyboardPreferences
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
        keyboardPreferences.activeThemeId.onEach { themeId ->
            val theme = _availableThemes.value.find { it.id == themeId }
            if (theme != null) {
                _activeTheme.value = theme
            }
        }.launchIn(coroutineScope)

        _availableThemes.onEach { themes ->
            val theme = themes.find { it.id == keyboardPreferences.activeThemeId.value }
            if (theme != null) {
                _activeTheme.value = theme
            }
        }.launchIn(coroutineScope)
    }

    private suspend fun loadThemes() {
        val themes = mutableListOf<KeyboardTheme>()
        
        // 1. Load built-ins from assets
        withContext(Dispatchers.IO) {
            try {
                val assetNames = context.assets.list("themes") ?: emptyArray()
                for (assetName in assetNames) {
                    if (assetName.endsWith(".json")) {
                        val jsonStr = context.assets.open("themes/$assetName").bufferedReader().use { it.readText() }
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
                    }
                }
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

    suspend fun saveThemeBackgroundImage(inputStream: java.io.InputStream, themeId: String): String {
        return withContext(Dispatchers.IO) {
            val bgFile = File(backgroundsDir, "$themeId.png")
            bgFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            bgFile.absolutePath
        }
    }
}
