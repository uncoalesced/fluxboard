// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import android.content.Context
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.ime.KeyboardLayouts
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
class LayoutManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyboardPreferences: KeyboardPreferences
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val customLayoutsDir = File(context.filesDir, "layouts")

    private val _availableLayouts = MutableStateFlow<List<KeyboardLayoutConfig>>(emptyList())
    val availableLayouts: StateFlow<List<KeyboardLayoutConfig>> = _availableLayouts.asStateFlow()

    private val _activeLayout = MutableStateFlow(buildDefaultLayout())
    val activeLayout: StateFlow<KeyboardLayoutConfig> = _activeLayout.asStateFlow()

    init {
        if (!customLayoutsDir.exists()) {
            customLayoutsDir.mkdirs()
        }

        coroutineScope.launch { loadLayouts() }

        keyboardPreferences.activeLayoutId.onEach { layoutId ->
            val layout = _availableLayouts.value.find { it.id == layoutId }
            if (layout != null) {
                _activeLayout.value = layout
            }
        }.launchIn(coroutineScope)

        _availableLayouts.onEach { layouts ->
            val layout = layouts.find { it.id == keyboardPreferences.activeLayoutId.value }
            if (layout != null) {
                _activeLayout.value = layout
            }
        }.launchIn(coroutineScope)
    }

    private suspend fun loadLayouts() {
        val layouts = mutableListOf<KeyboardLayoutConfig>()

        // Built-in default
        layouts.add(buildDefaultLayout())

        // Asset presets
        withContext(Dispatchers.IO) {
            try {
                val assetNames = context.assets.list("layouts") ?: emptyArray()
                for (assetName in assetNames) {
                    if (assetName.endsWith(".json")) {
                        val jsonStr = context.assets.open("layouts/$assetName")
                            .bufferedReader().use { it.readText() }
                        try {
                            val loaded = KeyboardLayoutConfig.fromJson(jsonStr)
                            // Avoid duplicating the hardcoded default if the asset has the same id
                            if (layouts.none { it.id == loaded.id }) {
                                layouts.add(loaded)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Custom layouts from filesDir
            val files = customLayoutsDir.listFiles() ?: emptyArray()
            for (file in files) {
                if (file.name.endsWith(".json")) {
                    try {
                        val jsonStr = file.readText()
                        layouts.add(KeyboardLayoutConfig.fromJson(jsonStr))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        _availableLayouts.value = layouts
    }

    /** Save a custom layout after validation. Returns the validation result. */
    suspend fun saveCustomLayout(config: KeyboardLayoutConfig): LayoutValidationResult {
        val result = LayoutValidator.validate(config)
        if (result is LayoutValidationResult.Valid) {
            withContext(Dispatchers.IO) {
                val file = File(customLayoutsDir, "${config.id}.json")
                file.writeText(config.toJson().toString(2))
                loadLayouts()
                keyboardPreferences.setActiveLayoutId(config.id)
            }
        }
        return result
    }

    suspend fun deleteCustomLayout(layoutId: String) {
        if (layoutId == DEFAULT_LAYOUT_ID) return // never delete the built-in
        withContext(Dispatchers.IO) {
            val file = File(customLayoutsDir, "$layoutId.json")
            if (file.exists()) {
                file.delete()
                if (keyboardPreferences.activeLayoutId.value == layoutId) {
                    keyboardPreferences.setActiveLayoutId(DEFAULT_LAYOUT_ID)
                }
                loadLayouts()
            }
        }
    }

    companion object {
        const val DEFAULT_LAYOUT_ID = "preset_qwerty"

        fun buildDefaultLayout(): KeyboardLayoutConfig {
            return KeyboardLayoutConfig.fromLegacyLayout(
                id = DEFAULT_LAYOUT_ID,
                name = "QWERTY",
                legacyRows = KeyboardLayouts.qwertyLettersLower
            )
        }
    }
}
