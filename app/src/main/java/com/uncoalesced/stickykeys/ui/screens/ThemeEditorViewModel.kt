// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyStyle
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyboardTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ThemeEditorViewModel
    @Inject
    constructor(
        private val themeManager: ThemeManager,
        private val keyboardPreferences: KeyboardPreferences,
    ) : ViewModel() {
        val availableThemes =
            themeManager.availableThemes.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )
        val activeTheme =
            themeManager.activeTheme.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null,
            )

        fun setActiveTheme(themeId: String) {
            viewModelScope.launch {
                keyboardPreferences.setActiveThemeId(themeId)
            }
        }

        fun saveTheme(theme: KeyboardTheme) {
            viewModelScope.launch {
                themeManager.saveCustomTheme(theme)
            }
        }

        fun updateActiveThemeBackgroundImage(
            uri: Uri,
            context: Context,
        ) {
            val theme = activeTheme.value ?: return
            viewModelScope.launch {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val targetThemeId =
                        if (theme.id.startsWith("preset_")) {
                            "custom_" + UUID.randomUUID().toString()
                        } else {
                            theme.id
                        }
                    val imagePath =
                        themeManager.saveThemeBackgroundImage(
                            inputStream,
                            targetThemeId,
                        )
                    val updatedTheme =
                        theme.copy(
                            id = targetThemeId,
                            name =
                                if (theme.id.startsWith(
                                        "preset_",
                                    )
                                ) {
                                    "Custom ${theme.name}"
                                } else {
                                    theme.name
                                },
                            backgroundImagePath = imagePath,
                        )
                    themeManager.saveCustomTheme(updatedTheme)
                }
            }
        }

        /**
         * Applies an edit to the active theme, forking a preset into a custom copy first.
         *
         * The fork id is derived from the preset rather than random. A random UUID per call
         * looked fine for a one-shot edit but is wrong for anything continuous: dragging a
         * slider fires this on every frame, and `activeTheme` only becomes the new custom
         * theme once the manager has reloaded and the preference has propagated -- so every
         * intervening frame still saw a preset and forked *again*. One drag could leave a
         * dozen near-identical "Custom Ink" themes in the list. A derived id makes repeated
         * edits converge on the same theme, which is what the user meant by dragging.
         */
        private fun mutateActiveTheme(transform: (KeyboardTheme) -> KeyboardTheme) {
            val theme = activeTheme.value ?: return
            viewModelScope.launch {
                val isPreset = theme.id.startsWith("preset_")
                val base =
                    if (isPreset) {
                        theme.copy(
                            id = "custom_${theme.id}",
                            name = "Custom ${theme.name}",
                        )
                    } else {
                        theme
                    }
                themeManager.saveCustomTheme(transform(base))
            }
        }

        fun updateActiveThemeOverlayOpacity(opacity: Float) =
            mutateActiveTheme { it.copy(imageOverlayOpacity = opacity) }

        /** Per-key styling edits all route through here, so the fork rule lives in one place. */
        fun updateKeyStyle(transform: (KeyStyle) -> KeyStyle) =
            mutateActiveTheme { it.copy(keyStyle = transform(it.keyStyle)) }

        fun removeActiveThemeBackgroundImage() {
            val theme = activeTheme.value ?: return
            viewModelScope.launch {
                val updatedTheme = theme.copy(backgroundImagePath = null)
                themeManager.saveCustomTheme(updatedTheme)
            }
        }
    }
