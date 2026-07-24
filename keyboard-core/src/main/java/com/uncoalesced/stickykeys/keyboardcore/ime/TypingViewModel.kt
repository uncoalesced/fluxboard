// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyboardTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager

data class UndoAction(val original: String, val corrected: String)

@HiltViewModel
class TypingViewModel @Inject constructor(
    private val predictionEngine: PredictionEngine,
    private val keyboardPreferences: KeyboardPreferences,
    private val themeManager: ThemeManager,
    private val layoutManager: LayoutManager,
    private val hapticsManager: HapticsManager
) : ViewModel() {
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions
    
    private val _undoState = MutableStateFlow<UndoAction?>(null)
    val undoState: StateFlow<UndoAction?> = _undoState

    private val _shouldAutoCapitalize = MutableStateFlow(false)
    val shouldAutoCapitalize: StateFlow<Boolean> = _shouldAutoCapitalize

    val activeTheme: StateFlow<KeyboardTheme?> = themeManager.activeTheme

    val activeLayout: StateFlow<KeyboardLayoutConfig> = layoutManager.activeLayout

    private var currentWord = ""
    
    init {
        viewModelScope.launch {
            predictionEngine.initialize()
        }
    }
    
    fun onCursorCapsModeChanged(shouldCapitalize: Boolean) {
        _shouldAutoCapitalize.value = shouldCapitalize && keyboardPreferences.autoCapitalizeEnabled.value
    }

    fun performKeyPressHaptic() {
        hapticsManager.performKeyPressHaptic()
    }

    fun onKeyPressed(char: String) {
        currentWord += char
        _undoState.value = null // Typing clears undo state
        updateSuggestions()
    }
    
    fun onDelete(): Boolean {
        if (currentWord.isNotEmpty()) {
            currentWord = currentWord.dropLast(1)
            updateSuggestions()
            return true // handled internally
        }
        return false // let controller handle delete
    }
    
    suspend fun getAutoCorrection(): String? {
        if (currentWord.isBlank() || !keyboardPreferences.autoCorrectEnabled.value) return null
        return predictionEngine.getAutoCorrection(currentWord)
    }

    fun onAutoCorrected(original: String, corrected: String) {
        _undoState.value = UndoAction(original, corrected)
        currentWord = ""
        _suggestions.value = emptyList()
        viewModelScope.launch {
            predictionEngine.learnWord(corrected)
        }
    }

    fun clearUndoState() {
        _undoState.value = null
    }

    fun onUndoApplied() {
        _undoState.value = null
    }

    fun onWordFinished() {
        val word = currentWord
        currentWord = ""
        _suggestions.value = emptyList()
        if (word.isNotBlank()) {
            viewModelScope.launch {
                predictionEngine.learnWord(word)
            }
        }
    }
    
    fun onSuggestionSelected(suggestion: String) {
        currentWord = ""
        _suggestions.value = emptyList()
        viewModelScope.launch {
            predictionEngine.learnWord(suggestion)
        }
    }
    
    fun getCurrentWord(): String = currentWord
    
    private fun updateSuggestions() {
        if (currentWord.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            _suggestions.value = predictionEngine.getSuggestions(currentWord)
        }
    }
}
