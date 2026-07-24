// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.uncoalesced.stickykeys.data.local.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.uncoalesced.stickykeys.ui.components.LoadingScreen

sealed interface AppSettingsUiState {
    data object Loading : AppSettingsUiState
    data object Success : AppSettingsUiState
}

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppSettingsUiState>(AppSettingsUiState.Success)
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    val defaultExportFormat = appPreferences.defaultExportFormat

    fun setDefaultExportFormat(format: String) {
        appPreferences.setDefaultExportFormat(format)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel = hiltViewModel(),
    onNavigateToManageCategories: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    
    when (state) {
        is AppSettingsUiState.Loading -> LoadingScreen()
        is AppSettingsUiState.Success -> {
            val currentExportFormat by viewModel.defaultExportFormat.collectAsState()
            var formatDropdownExpanded by remember { mutableStateOf(false) }

            val exportFormats = listOf("image/webp" to "Animated WebP", "image/gif" to "Standard GIF")
            val currentFormatLabel = exportFormats.find { it.first == currentExportFormat }?.second ?: "Animated WebP"

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "App Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Category Management Entry
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToManageCategories() }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.text_manage_categories), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.text_add_rename_or_delete_your_sticker_categories), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Default Export Format
                Text(stringResource(R.string.text_default_export_format), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = formatDropdownExpanded,
                    onExpandedChange = { formatDropdownExpanded = !formatDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = currentFormatLabel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = formatDropdownExpanded,
                        onDismissRequest = { formatDropdownExpanded = false }
                    ) {
                        exportFormats.forEach { (format, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setDefaultExportFormat(format)
                                    formatDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This format is used when converting videos to stickers.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
