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
class LayoutManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val keyboardPreferences: KeyboardPreferences,
    ) {
        private val coroutineScope = CoroutineScope(Dispatchers.IO)
        private val customLayoutsDir = File(context.filesDir, "layouts")

        private val _availableLayouts = MutableStateFlow<List<KeyboardLayoutConfig>>(emptyList())
        val availableLayouts: StateFlow<List<KeyboardLayoutConfig>> =
            _availableLayouts
                .asStateFlow()

        private val _activeLayout = MutableStateFlow(buildDefaultLayout())
        val activeLayout: StateFlow<KeyboardLayoutConfig> = _activeLayout.asStateFlow()

        init {
            if (!customLayoutsDir.exists()) {
                customLayoutsDir.mkdirs()
            }

            coroutineScope.launch { loadLayouts() }

            keyboardPreferences.activeLayoutId
                .onEach { layoutId -> applyResolution(_availableLayouts.value, layoutId) }
                .launchIn(coroutineScope)

            _availableLayouts
                .onEach { layouts ->
                    applyResolution(layouts, keyboardPreferences.activeLayoutId.value)
                }.launchIn(coroutineScope)
        }

        /**
         * Publishes the active layout, and never leaves a dangling id in place.
         *
         * Both observers used to be `if (layout != null) set it`, which is the exact shape
         * `ThemeManager.resolveActive` was fixed for: an `active_layout_id` that matches nothing
         * was silently ignored, so the stored pointer stayed broken and every launch
         * rediscovered it. It did not blank the keyboard the way the theme version did -- the
         * value left in place is the built-in default rather than null -- but the preference is
         * never repaired, so the user's chosen layout stays silently un-applied forever and
         * nothing says why. See [resolveLayout] for the decision itself.
         */
        private fun applyResolution(
            layouts: List<KeyboardLayoutConfig>,
            requestedId: String,
        ) {
            val resolution = resolveLayout(layouts, requestedId) ?: return
            _activeLayout.value = resolution.layout
            resolution.repairedId?.let { keyboardPreferences.setActiveLayoutId(it) }
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
                            val jsonStr =
                                context.assets
                                    .open("layouts/$assetName")
                                    .bufferedReader()
                                    .use { it.readText() }
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
                    if (!file.name.endsWith(".json")) continue
                    val parsed =
                        try {
                            KeyboardLayoutConfig.fromJson(file.readText())
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    // Quarantined rather than left in place, for the same reason ThemeManager
                    // quarantines unparseable themes: a file that fails to parse will not start
                    // parsing later, so leaving it means re-reading, re-throwing and re-skipping
                    // it on every single load while the active-layout preference may still point
                    // at the id it was supposed to supply. The bytes are kept, not deleted.
                    //
                    // A layout that parses but cannot be typed on is quarantined too.
                    // saveCustomLayout validates before writing, so an invalid file on disk was
                    // either written by an older build or edited by hand -- and one missing
                    // SPACE or SYMBOLS key is enough to strand the user with no way back from
                    // the keyboard itself, which is precisely what LayoutValidator exists to
                    // prevent at the other end.
                    val usable =
                        parsed != null &&
                            LayoutValidator.validate(parsed) is LayoutValidationResult.Valid
                    if (usable) {
                        // Replaces an entry already claiming this id rather than joining it.
                        // The built-in goes into the list first and the asset presets after,
                        // so a custom file carrying a shipped id -- which is what a restored
                        // backup or a device migration hands over -- used to sit behind the
                        // shipped copy, and resolveLayout takes the first match. The user's
                        // file was read, parsed, validated and then never used, with the save
                        // itself having succeeded and nothing reporting a problem.
                        val existing = layouts.indexOfFirst { it.id == parsed!!.id }
                        if (existing >= 0) {
                            layouts[existing] = parsed!!
                        } else {
                            layouts.add(parsed!!)
                        }
                    } else {
                        runCatching {
                            file.renameTo(File(customLayoutsDir, "${file.name}.corrupt"))
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

        /**
         * Discards every custom layout and returns to the shipped QWERTY.
         *
         * Half of the user-facing recovery path for a keyboard that has rendered itself
         * unusable. It deliberately deletes rather than quarantines: the user asked for a
         * clean slate, and leaving files behind that a later load might resurrect would defeat
         * the point. Quarantined `.corrupt` files go too, since they are the most likely thing
         * to have caused the state being escaped from.
         */
        suspend fun resetToDefaults() {
            withContext(Dispatchers.IO) {
                customLayoutsDir.listFiles()?.forEach { runCatching { it.delete() } }
                keyboardPreferences.setActiveLayoutId(DEFAULT_LAYOUT_ID)
                loadLayouts()
            }
        }

        companion object {
            const val DEFAULT_LAYOUT_ID = "preset_qwerty"

            /**
             * The built-in layout, lower case and without the digit row.
             *
             * Case and the digit row are both applied at render time, not baked in here:
             * this config is what gets persisted and diffed against custom layouts, and a
             * saved copy carrying a transient view state would make the number-row toggle
             * silently rewrite the user's layout file.
             */
            fun buildDefaultLayout(): KeyboardLayoutConfig =
                KeyboardLayoutConfig(
                    id = DEFAULT_LAYOUT_ID,
                    name = "QWERTY",
                    rows = KeyboardLayouts.letterRows(upper = false, showNumberRow = false),
                )
        }
    }

/**
 * Which layout to publish, and the id the stored preference should be repaired to.
 *
 * [repairedId] is null when the request resolved cleanly, so the caller writes to
 * SharedPreferences only when something actually needs correcting rather than on every
 * emission.
 */
internal data class LayoutResolution(
    val layout: KeyboardLayoutConfig,
    val repairedId: String?,
)

/**
 * Picks the active layout, falling back rather than ignoring an id that matches nothing.
 *
 * Pure and separate from [LayoutManager] for the same reason `KeyboardTheme.sanitized()` is
 * pure: this is a persisted-state recovery path, so the interesting cases are the ones that
 * only arise from state already on disk, and those are close to impossible to stage against a
 * live manager holding real files and a real SharedPreferences.
 *
 * Returns null when nothing has loaded yet, which is not a failure -- the availableLayouts
 * observer runs again as soon as there is something to resolve against.
 */
internal fun resolveLayout(
    available: List<KeyboardLayoutConfig>,
    requestedId: String,
): LayoutResolution? {
    if (available.isEmpty()) return null

    available.find { it.id == requestedId }?.let { return LayoutResolution(it, null) }

    // The built-in is preferred over "whatever is first" because it is the one layout that
    // cannot itself be missing or malformed -- loadLayouts adds it before touching the disk.
    val replacement =
        available.find { it.id == LayoutManager.DEFAULT_LAYOUT_ID }
            ?: available.first()
    return LayoutResolution(replacement, replacement.id)
}
