// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.layout.CONTROL_OUTPUTS
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutValidationResult
import com.uncoalesced.stickykeys.keyboardcore.layout.REMAPPABLE_ACTIONS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** What happened to a remap attempt, so the dialog can say why rather than just closing. */
sealed interface RemapResult {
    data object Applied : RemapResult

    data object Blank : RemapResult

    /** [output] collides with a command the keyboard reads rather than types. */
    data class Reserved(
        val output: String,
    ) : RemapResult
}

/** Page indices, matching the order [KeyboardLayoutConfig.pages] returns them in. */
const val LETTERS_PAGE = 0
const val SYMBOLS_PAGE = 1
const val SYMBOLS_SHIFTED_PAGE = 2

@HiltViewModel
class LayoutEditorViewModel
    @Inject
    constructor(
        private val layoutManager: LayoutManager,
        private val keyboardPreferences: KeyboardPreferences,
    ) : ViewModel() {
        val availableLayouts =
            layoutManager.availableLayouts.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )

        private val _editingLayout = MutableStateFlow(LayoutManager.buildDefaultLayout())
        val editingLayout: StateFlow<KeyboardLayoutConfig> = _editingLayout.asStateFlow()

        private val _selectedKeyId = MutableStateFlow<String?>(null)
        val selectedKeyId: StateFlow<String?> = _selectedKeyId.asStateFlow()

        private val _validationErrors = MutableStateFlow<List<String>>(emptyList())
        val validationErrors: StateFlow<List<String>> = _validationErrors.asStateFlow()

        private val _saveSuccess = MutableStateFlow(false)
        val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

        init {
            viewModelScope.launch {
                layoutManager.activeLayout.collect { layout ->
                    _editingLayout.value = layout
                }
            }
        }

        fun selectKey(keyId: String?) {
            _selectedKeyId.value = keyId
        }

        /**
         * Which page of the layout is being edited.
         *
         * The symbol pages became part of [KeyboardLayoutConfig] in v0.1.4 specifically so they
         * could be edited, and then nothing ever edited them -- the editor only ever rendered
         * `rows`. Every mutation below takes the page rather than assuming the letters plane.
         */
        private val _editingPage = MutableStateFlow(LETTERS_PAGE)
        val editingPage: StateFlow<Int> = _editingPage.asStateFlow()

        fun selectPage(page: Int) {
            _editingPage.value = page
            // Key ids are unique within a page, not across them, so a selection carried over
            // would point at nothing or, worse, at a key on the page the user just left.
            _selectedKeyId.value = null
        }

        private fun updateCurrentPage(
            transform: (List<List<KeyDefinition>>) -> List<List<KeyDefinition>>,
        ) {
            val current = _editingLayout.value
            _editingLayout.value =
                when (_editingPage.value) {
                    SYMBOLS_PAGE -> current.copy(symbolRows = transform(current.symbolRows))
                    SYMBOLS_SHIFTED_PAGE ->
                        current.copy(symbolShiftedRows = transform(current.symbolShiftedRows))
                    else -> current.copy(rows = transform(current.rows))
                }
        }

        fun swapKeys(
            rowIndex: Int,
            fromIndex: Int,
            toIndex: Int,
        ) {
            updateCurrentPage { rows ->
                val row = rows.getOrNull(rowIndex)?.toMutableList() ?: return@updateCurrentPage rows
                if (fromIndex !in row.indices || toIndex !in row.indices) {
                    return@updateCurrentPage rows
                }
                val temp = row[fromIndex]
                row[fromIndex] = row[toIndex]
                row[toIndex] = temp
                rows.toMutableList().also { it[rowIndex] = row }
            }
        }

        fun adjustWeight(
            keyId: String,
            delta: Float,
        ) {
            updateCurrentPage { rows ->
                rows.map { row ->
                    row.map { key ->
                        if (key.id == keyId) {
                            val newWeight = (key.weight + delta).coerceIn(0.5f, 6.0f)
                            key.copy(weight = newWeight)
                        } else {
                            key
                        }
                    }
                }
            }
        }

        /**
         * Repoints a key at a new output, or reports why it cannot be.
         *
         * The dialog used to accept anything non-blank, which let a user map a key to the
         * literal string "SPACE" -- the keyboard reads that as the space *command*, so the key
         * drew as a blank and typed nothing, with no way to tell from the editor that anything
         * was wrong. Rejecting the control vocabulary is the whole of the fix; free text is
         * still allowed, because remapping a key to a personal shorthand is a real use.
         *
         * [REMAPPABLE_ACTIONS] are the exception, and they are the reason key remapping could
         * not reach emoji at all: blanket-rejecting the control vocabulary also rejected the
         * three tokens that are destinations rather than structure. They are normalized to the
         * canonical upper-case token, so a user who types "emoji" into the free-text field gets
         * the emoji key rather than a key that types the five letters -- the keyboard matches
         * these exactly, and a lower-case near-miss is precisely the silent failure this
         * function exists to prevent.
         */
        fun remapKey(
            keyId: String,
            newOutput: String,
        ): RemapResult {
            val trimmed = newOutput.trim()
            if (trimmed.isEmpty()) return RemapResult.Blank
            val upper = trimmed.uppercase()
            val output =
                when {
                    upper in REMAPPABLE_ACTIONS -> upper
                    upper in CONTROL_OUTPUTS -> return RemapResult.Reserved(trimmed)
                    else -> trimmed
                }
            updateCurrentPage { rows ->
                rows.map { row ->
                    row.map { key ->
                        if (key.id == keyId) key.copy(output = output) else key
                    }
                }
            }
            _selectedKeyId.value = null
            return RemapResult.Applied
        }

        fun saveLayout() {
            viewModelScope.launch {
                val layout = _editingLayout.value
                // If editing a preset, save as a new custom layout
                val toSave =
                    if (layout.id.startsWith("preset_")) {
                        layout.copy(
                            id = "custom_" + UUID.randomUUID().toString().take(8),
                            name = "Custom ${layout.name}",
                        )
                    } else {
                        layout
                    }

                val result = layoutManager.saveCustomLayout(toSave)
                when (result) {
                    is LayoutValidationResult.Valid -> {
                        _validationErrors.value = emptyList()
                        _saveSuccess.value = true
                    }
                    is LayoutValidationResult.Invalid -> {
                        _validationErrors.value = result.reasons
                        _saveSuccess.value = false
                    }
                }
            }
        }

        fun resetToDefault() {
            _editingLayout.value = LayoutManager.buildDefaultLayout()
            _validationErrors.value = emptyList()
            _saveSuccess.value = false
        }

        fun setActiveLayout(layoutId: String) {
            keyboardPreferences.setActiveLayoutId(layoutId)
        }

        fun clearSaveSuccess() {
            _saveSuccess.value = false
        }
    }
