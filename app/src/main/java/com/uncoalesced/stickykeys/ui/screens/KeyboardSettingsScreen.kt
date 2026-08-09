// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.R
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
class KeyboardSettingsViewModel
    @Inject
    constructor(
        private val preferences: KeyboardPreferences,
        val themeManager: ThemeManager,
        val layoutManager: LayoutManager,
        private val clipboardDao: ClipboardDao,
        val appPreferences: com.uncoalesced.stickykeys.data.local.AppPreferences,
        private val usageLog: com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder,
    ) : ViewModel() {
        /** Opening either editor is what arms the default-keyboard prompt. */
        fun onCustomizerOpened() {
            appPreferences.markCustomizerVisited()
        }

        private val _uiState =
            MutableStateFlow<KeyboardSettingsUiState>(KeyboardSettingsUiState.Success)
        val uiState: StateFlow<KeyboardSettingsUiState> = _uiState.asStateFlow()

        val autoCapitalizeEnabled = preferences.autoCapitalizeEnabled
        val autoCorrectEnabled = preferences.autoCorrectEnabled

        val activeThemeId = preferences.activeThemeId
        val activeLayoutId = preferences.activeLayoutId

        val hapticsEnabled = preferences.hapticsEnabled
        val hapticsIntensity = preferences.hapticsIntensity

        val showNumberRow = preferences.showNumberRow
        val keySizePercent = preferences.keySizePercent
        val doubleSpacePeriod = preferences.doubleSpacePeriodEnabled
        val privateMode = preferences.privateModeEnabled
        val showKeyHints = preferences.showKeyHints
        val glideTyping = preferences.glideTypingEnabled

        fun setKeySizePercent(percent: Int) = preferences.setKeySizePercent(percent)

        fun setDoubleSpacePeriod(enabled: Boolean) = preferences.setDoubleSpacePeriod(enabled)

        /** Mirrors the toggle in the keyboard's own quick-access row; one stored flag. */
        fun setPrivateMode(enabled: Boolean) = preferences.setPrivateMode(enabled)

        fun setShowNumberRow(show: Boolean) = preferences.setShowNumberRow(show)

        /** Shows or hides the corner symbols. Long-press still types them either way. */
        fun setShowKeyHints(show: Boolean) = preferences.setShowKeyHints(show)

        /** Turns swipe-to-type on or off. */
        fun setGlideTyping(enabled: Boolean) = preferences.setGlideTyping(enabled)

        val keyboardHeightPercent = preferences.keyboardHeightPercent
        val keyboardBottomPaddingDp = preferences.keyboardBottomPaddingDp

        fun setKeyboardHeightPercent(percent: Int) = preferences.setKeyboardHeightPercent(percent)

        fun setKeyboardBottomPaddingDp(dp: Int) = preferences.setKeyboardBottomPaddingDp(dp)

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

        /**
         * The escape hatch from a keyboard that has rendered itself unusable.
         *
         * Both halves in one action on purpose: a user in this state cannot read their
         * keyboard, so they cannot be asked to work out whether it was the theme or the
         * layout that did it. Preferences that cannot blank a keyboard -- height, bottom
         * padding, haptics, the number row -- are deliberately left alone, so this is not a
         * general "reset all settings" that quietly throws away unrelated choices.
         */
        fun resetKeyboardAppearance() {
            viewModelScope.launch {
                themeManager.resetToDefaults()
                layoutManager.resetToDefaults()
            }
        }

        /** Compile-time false in release; the settings section is omitted entirely then. */
        val usageLoggingEnabled: Boolean get() = usageLog.enabled

        fun usageLogUri() = usageLog.shareIntentFile()

        fun clearUsageLog() = usageLog.clear()
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSettingsScreen(
    viewModel: KeyboardSettingsViewModel = hiltViewModel(),
    onNavigateToThemeEditor: () -> Unit = {},
    onNavigateToLayoutEditor: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    when (state) {
        is KeyboardSettingsUiState.Loading -> LoadingScreen()
        is KeyboardSettingsUiState.Success -> {
            val autoCap by viewModel.autoCapitalizeEnabled.collectAsState()
            val autoCorrect by viewModel.autoCorrectEnabled.collectAsState()
            val showNumberRow by viewModel.showNumberRow.collectAsState()
            val keySizePercent by viewModel.keySizePercent.collectAsState()
            val doubleSpacePeriod by viewModel.doubleSpacePeriod.collectAsState()
            val privateMode by viewModel.privateMode.collectAsState()
            val showKeyHints by viewModel.showKeyHints.collectAsState()
            val glideTyping by viewModel.glideTyping.collectAsState()
            val keyboardHeight by viewModel.keyboardHeightPercent.collectAsState()
            val keyboardBottomPadding by viewModel.keyboardBottomPaddingDp.collectAsState()

            val activeThemeId by viewModel.activeThemeId.collectAsState()
            val activeLayoutId by viewModel.activeLayoutId.collectAsState()

            val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
            val hapticsIntensity by viewModel.hapticsIntensity.collectAsState()

            val availableThemes by viewModel.themeManager.availableThemes.collectAsState()
            val availableLayouts by viewModel.layoutManager.availableLayouts.collectAsState()

            var showClearClipboardDialog by remember { mutableStateOf(false) }
            var showResetAppearanceDialog by remember { mutableStateOf(false) }
            val context = LocalContext.current

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Keyboard Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 24.dp),
                )

                // Hides itself once the keyboard is both enabled and selected.
                com.uncoalesced.stickykeys.ui.components
                    .KeyboardSetupCard()

                // One-time nudge, due only after the keyboard is enabled and the user has
                // been into a customiser. Shows itself at most once, ever.
                com.uncoalesced.stickykeys.ui.components
                    .DefaultKeyboardPrompt(viewModel.appPreferences)

                // Typing Assistance
                Text(
                    stringResource(R.string.text_typing_assistance),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.text_auto_capitalization),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = autoCap, onCheckedChange = { viewModel.setAutoCapitalize(it) })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.text_auto_correction),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = autoCorrect,
                        onCheckedChange = { viewModel.setAutoCorrect(it) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_glide_typing),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.text_glide_typing_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = glideTyping,
                        onCheckedChange = { viewModel.setGlideTyping(it) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_number_row),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.text_number_row_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showNumberRow,
                        onCheckedChange = { viewModel.setShowNumberRow(it) },
                    )
                }

                // Directly under the number row, because the two answer the same question --
                // what is printed on the keys -- and a user hunting for one will look here for
                // the other.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_key_hints),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.text_key_hints_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showKeyHints,
                        onCheckedChange = { viewModel.setShowKeyHints(it) },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Keyboard size
                Text(
                    stringResource(R.string.text_keyboard_size),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_keyboard_height),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.text_keyboard_height_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "$keyboardHeight%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val minHeight = KeyboardPreferences.MIN_KEYBOARD_HEIGHT_PERCENT
                val maxHeight = KeyboardPreferences.MAX_KEYBOARD_HEIGHT_PERCENT
                Slider(
                    value = keyboardHeight.toFloat(),
                    onValueChange = { viewModel.setKeyboardHeightPercent(it.toInt()) },
                    valueRange = minHeight.toFloat()..maxHeight.toFloat(),
                    // Five-point steps: fine enough to find a comfortable height, coarse
                    // enough that the slider lands on a round number every time.
                    steps = (maxHeight - minHeight) / 5 - 1,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )

                // Key size, deliberately its own control rather than a second name for the
                // height slider. Height decides how much screen the keyboard occupies; this
                // decides how much of that space is key rather than gap. Someone who wants a
                // tall keyboard with generous gaps and someone who wants a short one with fat
                // keys are asking for different things, and one slider cannot serve both.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_key_size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.text_key_size_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "$keySizePercent%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val minKey = KeyboardPreferences.MIN_KEY_SIZE_PERCENT
                val maxKey = KeyboardPreferences.MAX_KEY_SIZE_PERCENT
                Slider(
                    value = keySizePercent.toFloat(),
                    onValueChange = { viewModel.setKeySizePercent(it.toInt()) },
                    valueRange = minKey.toFloat()..maxKey.toFloat(),
                    steps = (maxKey - minKey) / 5 - 1,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_double_space_period),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.text_double_space_period_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = doubleSpacePeriod,
                        onCheckedChange = { viewModel.setDoubleSpacePeriod(it) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_private_mode),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.text_private_mode_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = privateMode,
                        onCheckedChange = { viewModel.setPrivateMode(it) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_keyboard_bottom_padding),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.text_keyboard_bottom_padding_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${keyboardBottomPadding}dp",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = keyboardBottomPadding.toFloat(),
                    onValueChange = { viewModel.setKeyboardBottomPaddingDp(it.toInt()) },
                    valueRange = 0f..KeyboardPreferences.MAX_BOTTOM_PADDING_DP.toFloat(),
                    steps = KeyboardPreferences.MAX_BOTTOM_PADDING_DP / 2 - 1,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )

                TextButton(
                    onClick = {
                        viewModel.setKeyboardHeightPercent(
                            KeyboardPreferences.DEFAULT_KEYBOARD_HEIGHT_PERCENT,
                        )
                        viewModel.setKeyboardBottomPaddingDp(
                            KeyboardPreferences.DEFAULT_BOTTOM_PADDING_DP,
                        )
                    },
                ) {
                    Text(stringResource(R.string.text_reset_to_default))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Appearance
                Text(
                    stringResource(R.string.text_appearance),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Theme Picker & Editor
                var themeDropdownExpanded by remember { mutableStateOf(false) }
                val currentThemeName =
                    availableThemes.find { it.id == activeThemeId }?.name ?: "Default Theme"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.text_theme),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        viewModel.onCustomizerOpened()
                        onNavigateToThemeEditor()
                    }) {
                        Text(stringResource(R.string.text_customize_theme))
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = themeDropdownExpanded,
                    onExpandedChange = { themeDropdownExpanded = !themeDropdownExpanded },
                ) {
                    OutlinedTextField(
                        value = currentThemeName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = themeDropdownExpanded,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = themeDropdownExpanded,
                        onDismissRequest = { themeDropdownExpanded = false },
                    ) {
                        availableThemes.forEach { theme ->
                            DropdownMenuItem(
                                text = { Text(theme.name) },
                                onClick = {
                                    viewModel.setActiveTheme(theme.id)
                                    themeDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Layout Picker & Editor
                var layoutDropdownExpanded by remember { mutableStateOf(false) }
                val currentLayoutName =
                    availableLayouts.find { it.id == activeLayoutId }?.name ?: "QWERTY"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.text_layout),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        viewModel.onCustomizerOpened()
                        onNavigateToLayoutEditor()
                    }) {
                        Text(stringResource(R.string.text_customize_layout))
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = layoutDropdownExpanded,
                    onExpandedChange = { layoutDropdownExpanded = !layoutDropdownExpanded },
                ) {
                    OutlinedTextField(
                        value = currentLayoutName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = layoutDropdownExpanded,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = layoutDropdownExpanded,
                        onDismissRequest = { layoutDropdownExpanded = false },
                    ) {
                        availableLayouts.forEach { layout ->
                            DropdownMenuItem(
                                text = { Text(layout.name) },
                                onClick = {
                                    viewModel.setActiveLayout(layout.id)
                                    layoutDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Haptics
                Text(
                    stringResource(R.string.text_haptics),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.text_vibration_feedback),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) },
                    )
                }
                if (hapticsEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.text_vibration_strength),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$hapticsIntensity%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 0-100 percent, not a raw motor amplitude. The old 1..255 range was the
                    // value handed straight to the vibrator, so it meant different strengths
                    // on different phones and could never be turned down to nothing.
                    Slider(
                        value = hapticsIntensity.toFloat(),
                        onValueChange = { viewModel.setHapticsIntensity(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 99,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Privacy / Data
                Text(
                    stringResource(R.string.text_privacy_data),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showClearClipboardDialog = true },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(R.string.text_clear_clipboard_history))
                }

                if (showClearClipboardDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearClipboardDialog = false },
                        title = { Text(stringResource(R.string.text_clear_clipboard_history)) },
                        text = { Text(stringResource(R.string.text_clear_clipboard_confirm)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.clearClipboardHistory()
                                    showClearClipboardDialog = false
                                },
                            ) {
                                Text(
                                    stringResource(R.string.text_clear),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearClipboardDialog = false }) {
                                Text(stringResource(R.string.text_cancel))
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Troubleshooting
                //
                // This exists because of a specific reported failure: a keyboard whose keys
                // stopped being drawn while still responding to touch, with no way back short
                // of uninstalling and reinstalling. The active theme id and layout id live in
                // SharedPreferences and the files themselves in filesDir, so that state
                // survives force-stop, cache clearing and reboot -- and the user is left with
                // a keyboard they cannot read and no control they can find to fix it, because
                // every control for fixing it is on a keyboard they cannot read.
                //
                // Deliberately not gated on diagnosing the cause. Whatever a saved theme or
                // layout does to the renderer, throwing both away returns the user to a
                // keyboard that is known to draw.
                Text(
                    stringResource(R.string.text_troubleshooting),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.text_reset_appearance_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showResetAppearanceDialog = true }) {
                    Text(stringResource(R.string.text_reset_keyboard_appearance))
                }

                // Resolved in composable scope rather than with context.getString inside the
                // click handler: a LocalContext read is not invalidated by a Configuration
                // change, so the toast could show a stale-locale string after the user
                // switches language.
                val appearanceResetMessage = stringResource(R.string.text_appearance_reset)

                if (showResetAppearanceDialog) {
                    AlertDialog(
                        onDismissRequest = { showResetAppearanceDialog = false },
                        title = {
                            Text(stringResource(R.string.text_reset_keyboard_appearance))
                        },
                        text = {
                            Text(stringResource(R.string.text_reset_appearance_confirm))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.resetKeyboardAppearance()
                                    showResetAppearanceDialog = false
                                    Toast
                                        .makeText(
                                            context,
                                            appearanceResetMessage,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                            ) {
                                Text(
                                    stringResource(R.string.text_reset),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetAppearanceDialog = false }) {
                                Text(stringResource(R.string.text_cancel))
                            }
                        },
                    )
                }

                // Absent entirely from a stable or F-Droid build: USAGE_LOGGING is a
                // compile-time false there, so this whole block folds away rather than
                // showing a control for a file that is never written.
                if (viewModel.usageLoggingEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        "Tester diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This build keeps a local file of session lengths and key counts. " +
                            "No typed text is recorded, and nothing is ever sent anywhere " +
                            "unless you share it yourself below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val uri = viewModel.usageLogUri()
                            if (uri == null) {
                                Toast
                                    .makeText(
                                        context,
                                        "No usage recorded yet",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            } else {
                                val send =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/markdown"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                // The chooser is the point: the destination is the tester's
                                // choice every single time, and FluxBoard never has one.
                                context.startActivity(
                                    Intent.createChooser(send, "Share usage log"),
                                )
                            }
                        }) {
                            Text("Share usage log")
                        }
                        OutlinedButton(onClick = { viewModel.clearUsageLog() }) {
                            Text("Clear")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
