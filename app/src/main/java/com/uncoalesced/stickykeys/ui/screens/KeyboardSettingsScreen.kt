// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import com.uncoalesced.stickykeys.ui.components.LoadingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface KeyboardSettingsUiState {
    data object Loading : KeyboardSettingsUiState
    data object Success : KeyboardSettingsUiState
}

@HiltViewModel
class KeyboardSettingsViewModel @Inject constructor(
    private val preferences: KeyboardPreferences,
    val themeManager: ThemeManager,
    val layoutManager: LayoutManager,
    private val clipboardDao: ClipboardDao
) : ViewModel() {
    private val _uiState = MutableStateFlow<KeyboardSettingsUiState>(KeyboardSettingsUiState.Success)
    val uiState: StateFlow<KeyboardSettingsUiState> = _uiState.asStateFlow()

    val autoCapitalizeEnabled = preferences.autoCapitalizeEnabled
    val autoCorrectEnabled = preferences.autoCorrectEnabled

    val activeThemeId = preferences.activeThemeId
    val activeLayoutId = preferences.activeLayoutId

    val hapticsEnabled = preferences.hapticsEnabled
    val hapticsIntensity = preferences.hapticsIntensity

    fun setAutoCapitalize(enabled: Boolean) = preferences.setAutoCapitalize(enabled)
    fun setAutoCorrect(enabled: Boolean) = preferences.setAutoCorrect(enabled)
    fun setHapticsEnabled(enabled: Boolean) = preferences.setHapticsEnabled(enabled)
    fun setHapticsIntensity(intensity: Int) = preferences.setHapticsIntensity(intensity)
    fun setActiveTheme(id: String) = preferences.setActiveThemeId(id)
    fun setActiveLayout(id: String) = preferences.setActiveLayoutId(id)

    fun clearClipboardHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            clipboardDao.deleteAll()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSettingsScreen(
    viewModel: KeyboardSettingsViewModel = hiltViewModel(),
    onNavigateToThemeEditor: () -> Unit = {},
    onNavigateToLayoutEditor: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    
    when (state) {
        is KeyboardSettingsUiState.Loading -> LoadingScreen()
        is KeyboardSettingsUiState.Success -> {
            val autoCap by viewModel.autoCapitalizeEnabled.collectAsState()
            val autoCorrect by viewModel.autoCorrectEnabled.collectAsState()

            val activeThemeId by viewModel.activeThemeId.collectAsState()
            val activeLayoutId by viewModel.activeLayoutId.collectAsState()

            val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
            val hapticsIntensity by viewModel.hapticsIntensity.collectAsState()

            val availableThemes by viewModel.themeManager.availableThemes.collectAsState()
            val availableLayouts by viewModel.layoutManager.availableLayouts.collectAsState()

            var showClearClipboardDialog by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Keyboard Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Typing Assistance
                Text(stringResource(R.string.text_typing_assistance), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_auto_capitalization), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = autoCap, onCheckedChange = { viewModel.setAutoCapitalize(it) })
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_auto_correction), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = autoCorrect, onCheckedChange = { viewModel.setAutoCorrect(it) })
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Appearance
                Text(stringResource(R.string.text_appearance), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Theme Picker & Editor
                var themeDropdownExpanded by remember { mutableStateOf(false) }
                val currentThemeName = availableThemes.find { it.id == activeThemeId }?.name ?: "Default Theme"
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_theme), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onNavigateToThemeEditor) {
                        Text(stringResource(R.string.text_customize_theme))
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = themeDropdownExpanded,
                    onExpandedChange = { themeDropdownExpanded = !themeDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = currentThemeName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = themeDropdownExpanded,
                        onDismissRequest = { themeDropdownExpanded = false }
                    ) {
                        availableThemes.forEach { theme ->
                            DropdownMenuItem(
                                text = { Text(theme.name) },
                                onClick = {
                                    viewModel.setActiveTheme(theme.id)
                                    themeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Layout Picker & Editor
                var layoutDropdownExpanded by remember { mutableStateOf(false) }
                val currentLayoutName = availableLayouts.find { it.id == activeLayoutId }?.name ?: "QWERTY"
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_layout), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onNavigateToLayoutEditor) {
                        Text(stringResource(R.string.text_customize_layout))
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = layoutDropdownExpanded,
                    onExpandedChange = { layoutDropdownExpanded = !layoutDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = currentLayoutName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = layoutDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = layoutDropdownExpanded,
                        onDismissRequest = { layoutDropdownExpanded = false }
                    ) {
                        availableLayouts.forEach { layout ->
                            DropdownMenuItem(
                                text = { Text(layout.name) },
                                onClick = {
                                    viewModel.setActiveLayout(layout.id)
                                    layoutDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Haptics
                Text(stringResource(R.string.text_haptics), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_vibration_feedback), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = hapticsEnabled, onCheckedChange = { viewModel.setHapticsEnabled(it) })
                }
                if (hapticsEnabled) {
                    Text(stringResource(R.string.text_vibration_strength), style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = hapticsIntensity.toFloat(),
                        onValueChange = { viewModel.setHapticsIntensity(it.toInt()) },
                        valueRange = 1f..255f,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Privacy / Data
                Text(stringResource(R.string.text_privacy_data), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showClearClipboardDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.text_clear_clipboard_history))
                }

                if (showClearClipboardDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearClipboardDialog = false },
                        title = { Text(stringResource(R.string.text_clear_clipboard_history)) },
                        text = { Text(stringResource(R.string.text_are_you_sure_you_want_to_delete_all_saved_clipboard_history_this_action_is_irreversible)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.clearClipboardHistory()
                                    showClearClipboardDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.text_clear), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearClipboardDialog = false }) {
                                Text(stringResource(R.string.text_cancel))
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
