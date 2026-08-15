// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
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
        val mediaMetadata = preferences.mediaMetadataEnabled

        fun setKeySizePercent(percent: Int) = preferences.setKeySizePercent(percent)

        fun setDoubleSpacePeriod(enabled: Boolean) = preferences.setDoubleSpacePeriod(enabled)

        /** Mirrors the toggle in the keyboard's own quick-access row; one stored flag. */
        fun setPrivateMode(enabled: Boolean) = preferences.setPrivateMode(enabled)

        fun setShowNumberRow(show: Boolean) = preferences.setShowNumberRow(show)

        /** Shows or hides the corner symbols. Long-press still types them either way. */
        fun setShowKeyHints(show: Boolean) = preferences.setShowKeyHints(show)

        /** Turns swipe-to-type on or off. */
        fun setGlideTyping(enabled: Boolean) = preferences.setGlideTyping(enabled)

        fun setMediaMetadata(enabled: Boolean) = preferences.setMediaMetadataEnabled(enabled)

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
            val mediaMetadata by viewModel.mediaMetadata.collectAsState()
            var showMediaConsent by remember { mutableStateOf(false) }
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

            // Restructured (2c): a flat list of ~15 rows made "which setting was that" a
            // scroll-and-scan problem. Grouped cards give the screen landmarks; the search
            // bar exists for the same reason -- so a returning user does not have to
            // remember which group a setting landed in. Same items as before, same
            // ViewModel calls -- this is a layout change, not a behaviour change.
            var searchQuery by rememberSaveable { mutableStateOf("") }

            val labelAutoCap = stringResource(R.string.text_auto_capitalization)
            val labelAutoCorrect = stringResource(R.string.text_auto_correction)
            val labelGlideTyping = stringResource(R.string.text_glide_typing)
            val summaryGlideTyping = stringResource(R.string.text_glide_typing_summary)
            val labelMediaInfo = stringResource(R.string.text_media_info)
            val summaryMediaInfo = stringResource(R.string.text_media_info_summary)
            val labelDoubleSpace = stringResource(R.string.text_double_space_period)
            val summaryDoubleSpace = stringResource(R.string.text_double_space_period_summary)

            val labelHeight = stringResource(R.string.text_keyboard_height)
            val summaryHeight = stringResource(R.string.text_keyboard_height_summary)
            val labelKeySize = stringResource(R.string.text_key_size)
            val summaryKeySize = stringResource(R.string.text_key_size_summary)
            val labelVibration = stringResource(R.string.text_vibration_feedback)
            val labelVibrationStrength = stringResource(R.string.text_vibration_strength)
            val labelBottomPadding = stringResource(R.string.text_keyboard_bottom_padding)
            val summaryBottomPadding = stringResource(R.string.text_keyboard_bottom_padding_summary)

            val labelTheme = stringResource(R.string.text_theme)
            val labelLayout = stringResource(R.string.text_layout)
            val labelNumberRow = stringResource(R.string.text_number_row)
            val summaryNumberRow = stringResource(R.string.text_number_row_summary)
            val labelKeyHints = stringResource(R.string.text_key_hints)
            val summaryKeyHints = stringResource(R.string.text_key_hints_summary)

            val labelPrivateMode = stringResource(R.string.text_private_mode)
            val summaryPrivateMode = stringResource(R.string.text_private_mode_summary)
            val labelClearClipboard = stringResource(R.string.text_clear_clipboard_history)

            val labelResetAppearance = stringResource(R.string.text_reset_keyboard_appearance)
            val summaryResetAppearance = stringResource(R.string.text_reset_appearance_summary)

            // Blank query matches everything; otherwise a plain case-insensitive substring
            // test against whichever labels the caller passes. No fuzzy matching, no
            // indexing -- the whole screen is under 30 items, so a linear scan per
            // recomposition costs nothing worth optimizing for.
            fun matches(vararg labels: String) =
                searchQuery.isBlank() || labels.any { it.contains(searchQuery, ignoreCase = true) }

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
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search settings") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )

                // Hides itself once the keyboard is both enabled and selected.
                com.uncoalesced.stickykeys.ui.components
                    .KeyboardSetupCard()

                // One-time nudge, due only after the keyboard is enabled and the user has
                // been into a customiser. Shows itself at most once, ever.
                com.uncoalesced.stickykeys.ui.components
                    .DefaultKeyboardPrompt(viewModel.appPreferences)

                // TYPING
                if (matches(labelAutoCap, labelAutoCorrect, labelGlideTyping, labelDoubleSpace)) {
                    SettingsGroup(title = "Typing") {
                        if (matches(labelAutoCap)) {
                            SettingsSwitchRow(
                                title = labelAutoCap,
                                checked = autoCap,
                                onCheckedChange = { viewModel.setAutoCapitalize(it) },
                            )
                        }
                        if (matches(labelAutoCorrect)) {
                            SettingsSwitchRow(
                                title = labelAutoCorrect,
                                checked = autoCorrect,
                                onCheckedChange = { viewModel.setAutoCorrect(it) },
                            )
                        }
                        if (matches(labelGlideTyping, summaryGlideTyping)) {
                            SettingsSwitchRow(
                                title = labelGlideTyping,
                                summary = summaryGlideTyping,
                                checked = glideTyping,
                                onCheckedChange = { viewModel.setGlideTyping(it) },
                            )
                        }
                        if (matches(labelDoubleSpace, summaryDoubleSpace)) {
                            SettingsSwitchRow(
                                title = labelDoubleSpace,
                                summary = summaryDoubleSpace,
                                checked = doubleSpacePeriod,
                                onCheckedChange = { viewModel.setDoubleSpacePeriod(it) },
                            )
                        }
                        if (matches(labelMediaInfo, summaryMediaInfo)) {
                            // Switching on opens the explanation first, never the OS screen
                            // directly. Switching off needs neither: it is the safe direction,
                            // and the row simply stops showing anything.
                            SettingsSwitchRow(
                                title = labelMediaInfo,
                                summary = summaryMediaInfo,
                                checked = mediaMetadata,
                                onCheckedChange = { wanted ->
                                    if (wanted) {
                                        showMediaConsent = true
                                    } else {
                                        viewModel.setMediaMetadata(false)
                                    }
                                },
                            )
                        }
                    }
                }

                // SIZE & FEEL
                if (matches(labelHeight, labelKeySize, labelVibration, labelBottomPadding)) {
                    SettingsGroup(title = "Size & feel") {
                        if (matches(labelHeight, summaryHeight)) {
                            SettingsSliderRow(
                                title = labelHeight,
                                summary = summaryHeight,
                                valueLabel = "$keyboardHeight%",
                                value = keyboardHeight.toFloat(),
                                onValueChange = { viewModel.setKeyboardHeightPercent(it.toInt()) },
                                valueRange =
                                    percentRange(
                                        KeyboardPreferences.MIN_KEYBOARD_HEIGHT_PERCENT,
                                        KeyboardPreferences.MAX_KEYBOARD_HEIGHT_PERCENT,
                                    ),
                                steps =
                                    (
                                        KeyboardPreferences.MAX_KEYBOARD_HEIGHT_PERCENT -
                                            KeyboardPreferences.MIN_KEYBOARD_HEIGHT_PERCENT
                                    ) / 5 - 1,
                            )
                        }
                        if (matches(labelKeySize, summaryKeySize)) {
                            SettingsSliderRow(
                                title = labelKeySize,
                                summary = summaryKeySize,
                                valueLabel = "$keySizePercent%",
                                value = keySizePercent.toFloat(),
                                onValueChange = { viewModel.setKeySizePercent(it.toInt()) },
                                valueRange =
                                    percentRange(
                                        KeyboardPreferences.MIN_KEY_SIZE_PERCENT,
                                        KeyboardPreferences.MAX_KEY_SIZE_PERCENT,
                                    ),
                                steps =
                                    (
                                        KeyboardPreferences.MAX_KEY_SIZE_PERCENT -
                                            KeyboardPreferences.MIN_KEY_SIZE_PERCENT
                                    ) / 5 - 1,
                            )
                        }
                        if (matches(labelBottomPadding, summaryBottomPadding)) {
                            SettingsSliderRow(
                                title = labelBottomPadding,
                                summary = summaryBottomPadding,
                                valueLabel = "${keyboardBottomPadding}dp",
                                value = keyboardBottomPadding.toFloat(),
                                onValueChange = {
                                    viewModel.setKeyboardBottomPaddingDp(it.toInt())
                                },
                                valueRange =
                                    percentRange(0, KeyboardPreferences.MAX_BOTTOM_PADDING_DP),
                                steps = KeyboardPreferences.MAX_BOTTOM_PADDING_DP / 2 - 1,
                            )
                        }
                        if (matches(labelHeight, labelBottomPadding)) {
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
                        }
                        if (matches(labelVibration)) {
                            SettingsSwitchRow(
                                title = labelVibration,
                                checked = hapticsEnabled,
                                onCheckedChange = { viewModel.setHapticsEnabled(it) },
                            )
                            if (hapticsEnabled) {
                                SettingsSliderRow(
                                    title = labelVibrationStrength,
                                    valueLabel = "$hapticsIntensity%",
                                    value = hapticsIntensity.toFloat(),
                                    // 0-100 percent, not a raw motor amplitude. The old 1..255
                                    // range was the value handed straight to the vibrator, so
                                    // it meant different strengths on different phones and
                                    // could never be turned down to nothing.
                                    onValueChange = { viewModel.setHapticsIntensity(it.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 99,
                                )
                            }
                        }
                    }
                }

                // APPEARANCE
                if (matches(labelTheme, labelLayout, labelNumberRow, labelKeyHints)) {
                    SettingsGroup(title = "Appearance") {
                        var themeDropdownExpanded by remember { mutableStateOf(false) }
                        var layoutDropdownExpanded by remember { mutableStateOf(false) }

                        if (matches(labelTheme)) {
                            val currentThemeName =
                                availableThemes.find { it.id == activeThemeId }?.name
                                    ?: "Default Theme"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    labelTheme,
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
                                onExpandedChange = {
                                    themeDropdownExpanded = !themeDropdownExpanded
                                },
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
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (matches(labelLayout)) {
                            val currentLayoutName =
                                availableLayouts.find { it.id == activeLayoutId }?.name
                                    ?: "QWERTY"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    labelLayout,
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
                                onExpandedChange = {
                                    layoutDropdownExpanded = !layoutDropdownExpanded
                                },
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
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (matches(labelNumberRow, summaryNumberRow)) {
                            SettingsSwitchRow(
                                title = labelNumberRow,
                                summary = summaryNumberRow,
                                checked = showNumberRow,
                                onCheckedChange = { viewModel.setShowNumberRow(it) },
                            )
                        }
                        // Directly under the number row, because the two answer the same
                        // question -- what is printed on the keys -- and a user hunting for
                        // one will look here for the other.
                        if (matches(labelKeyHints, summaryKeyHints)) {
                            SettingsSwitchRow(
                                title = labelKeyHints,
                                summary = summaryKeyHints,
                                checked = showKeyHints,
                                onCheckedChange = { viewModel.setShowKeyHints(it) },
                            )
                        }
                    }
                }

                // PRIVACY
                if (matches(labelPrivateMode, labelClearClipboard)) {
                    SettingsGroup(title = "Privacy") {
                        if (matches(labelPrivateMode, summaryPrivateMode)) {
                            SettingsSwitchRow(
                                title = labelPrivateMode,
                                summary = summaryPrivateMode,
                                checked = privateMode,
                                onCheckedChange = { viewModel.setPrivateMode(it) },
                            )
                        }
                        if (matches(labelClearClipboard)) {
                            Button(
                                onClick = { showClearClipboardDialog = true },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                    ),
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                Text(labelClearClipboard)
                            }
                        }
                    }
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

                // Resolved in composable scope rather than with context.getString inside the
                // click handler: a LocalContext read is not invalidated by a Configuration
                // change, so the toast could show a stale-locale string after the user
                // switches language.
                val appearanceResetMessage = stringResource(R.string.text_appearance_reset)

                // IF SOMETHING BREAKS
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
                if (matches(labelResetAppearance, summaryResetAppearance)) {
                    SettingsGroup(title = "If something breaks") {
                        Text(
                            summaryResetAppearance,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { showResetAppearanceDialog = true }) {
                            Text(labelResetAppearance)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

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
                    SettingsGroup(title = "Diagnostics") {
                        Text(
                            "This build keeps a local file of session lengths and key counts. " +
                                "No typed text is recorded, and nothing is ever sent anywhere " +
                                "unless you share it yourself below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
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
                                    // The chooser is the point: the destination is the
                                    // tester's choice every single time, and FluxBoard
                                    // never has one.
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
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Consent, before the OS screen and before the preference is written.
            //
            // The wording names what the *permission* grants, not what the feature does. There
            // is no runtime-permission dialog for a notification listener -- it is a Settings
            // screen grant -- so this is the only place FluxBoard can say, in its own words,
            // that the key to the media session is also the key to every notification on the
            // device. Saying only "shows the track name" would be true and dishonest.
            if (showMediaConsent) {
                AlertDialog(
                    onDismissRequest = { showMediaConsent = false },
                    title = { Text(labelMediaInfo) },
                    text = {
                        Column {
                            Text(stringResource(R.string.text_media_info_consent_grant))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(stringResource(R.string.text_media_info_consent_scope))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(stringResource(R.string.text_media_info_consent_offline))
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            // The preference records the intent; the OS screen is where the
                            // grant is actually made. If the user backs out there, the reader
                            // asks the OS and finds nothing, and the row stays as it was.
                            viewModel.setMediaMetadata(true)
                            showMediaConsent = false
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) {
                            Text(stringResource(R.string.text_media_info_consent_continue))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMediaConsent = false }) {
                            Text(stringResource(R.string.text_cancel))
                        }
                    },
                )
            }
        }
    }
}

/**
 * The grouping unit for 2c's restructure: an 11px uppercase label over a rounded card.
 *
 * Deliberately not exported beyond this file -- nothing else uses it yet, and a settings
 * screen elsewhere reaching for the same look is a real future use, not a speculative one,
 * so promoting it to a shared component is better done when that use actually shows up.
 */
@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            letterSpacing = 0.6.sp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Card(
            shape = StickyKeysTheme.shapes.large,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                content = content,
            )
        }
    }
}

/** One title/switch row, with an optional summary line -- the repeated shape this screen had. */
@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A percent slider's range, built from the two preference bounds.
 *
 * Exists for the formatter rather than for the reader: `..` may not have a line break beside
 * it, and at this screen's nesting depth two fully-qualified `KeyboardPreferences` constants
 * either side of one do not fit in 100 columns. A call wraps where an operator cannot.
 */
private fun percentRange(
    min: Int,
    max: Int,
): ClosedFloatingPointRange<Float> = min.toFloat()..max.toFloat()

/** One title/value/slider block, with an optional summary line. */
@Composable
private fun SettingsSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    summary: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                valueLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}
