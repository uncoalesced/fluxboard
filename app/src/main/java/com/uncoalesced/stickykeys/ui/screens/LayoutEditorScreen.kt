// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutValidationResult
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LayoutEditorViewModel @Inject constructor(
    private val layoutManager: LayoutManager,
    private val keyboardPreferences: KeyboardPreferences
) : ViewModel() {
    val availableLayouts = layoutManager.availableLayouts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
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

    fun swapKeys(rowIndex: Int, fromIndex: Int, toIndex: Int) {
        val current = _editingLayout.value
        val row = current.rows[rowIndex].toMutableList()
        if (fromIndex !in row.indices || toIndex !in row.indices) return
        val temp = row[fromIndex]
        row[fromIndex] = row[toIndex]
        row[toIndex] = temp
        val newRows = current.rows.toMutableList()
        newRows[rowIndex] = row
        _editingLayout.value = current.copy(rows = newRows)
    }

    fun adjustWeight(keyId: String, delta: Float) {
        val current = _editingLayout.value
        val newRows = current.rows.map { row ->
            row.map { key ->
                if (key.id == keyId) {
                    val newWeight = (key.weight + delta).coerceIn(0.5f, 6.0f)
                    key.copy(weight = newWeight)
                } else {
                    key
                }
            }
        }
        _editingLayout.value = current.copy(rows = newRows)
    }

    fun remapKey(keyId: String, newOutput: String) {
        val current = _editingLayout.value
        val newRows = current.rows.map { row ->
            row.map { key ->
                if (key.id == keyId) {
                    key.copy(output = newOutput)
                } else {
                    key
                }
            }
        }
        _editingLayout.value = current.copy(rows = newRows)
        _selectedKeyId.value = null
    }

    fun saveLayout() {
        viewModelScope.launch {
            val layout = _editingLayout.value
            // If editing a preset, save as a new custom layout
            val toSave = if (layout.id.startsWith("preset_")) {
                layout.copy(
                    id = "custom_" + UUID.randomUUID().toString().take(8),
                    name = "Custom ${layout.name}"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: LayoutEditorViewModel = hiltViewModel()
) {
    val editingLayout by viewModel.editingLayout.collectAsState()
    val selectedKeyId by viewModel.selectedKeyId.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val availableLayouts by viewModel.availableLayouts.collectAsState()

    var showRemapDialog by remember { mutableStateOf(false) }
    var remapTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.text_layout_editor)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Layout Selector
            Text(
                text = "Saved Layouts",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableLayouts.forEach { layout ->
                    FilterChip(
                        selected = layout.id == editingLayout.id,
                        onClick = { viewModel.setActiveLayout(layout.id) },
                        label = { Text(layout.name, fontSize = 12.sp) }
                    )
                }
            }

            Divider()

            // Visual Layout Editor
            Text(
                text = "Editing: ${editingLayout.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Keyboard Preview / Editor
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(4.dp)
            ) {
                editingLayout.rows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEachIndexed { keyIndex, keyDef ->
                            val isSelected = keyDef.id == selectedKeyId
                            Box(
                                modifier = Modifier
                                    .weight(keyDef.weight)
                                    .padding(2.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { viewModel.selectKey(keyDef.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                val label = keyDef.displayLabel ?: when (keyDef.output) {
                                    "SHIFT" -> "Sh"
                                    "DEL" -> "Del"
                                    "SYMBOLS" -> "?12"
                                    "STICKERS" -> "St"
                                    "ENTER" -> "Ent"
                                    "SPACE" -> "___"
                                    else -> keyDef.output
                                }
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Controls for Selected Key
            val selectedKey = editingLayout.rows.flatten().find { it.id == selectedKeyId }
            if (selectedKey != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Selected: ${selectedKey.output} (weight: ${"%.1f".format(selectedKey.weight)})",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Swap controls
                        val rowIndex = editingLayout.rows.indexOfFirst { row -> row.any { it.id == selectedKey.id } }
                        val keyIndex = editingLayout.rows.getOrNull(rowIndex)?.indexOfFirst { it.id == selectedKey.id } ?: -1

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.swapKeys(rowIndex, keyIndex, keyIndex - 1) },
                                enabled = keyIndex > 0
                            ) { Text(stringResource(R.string.text_move_left)) }

                            OutlinedButton(
                                onClick = { viewModel.swapKeys(rowIndex, keyIndex, keyIndex + 1) },
                                enabled = keyIndex < (editingLayout.rows.getOrNull(rowIndex)?.size ?: 0) - 1
                            ) { Text(stringResource(R.string.text_move_right)) }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Resize controls
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.adjustWeight(selectedKey.id, -0.5f) }) {
                                Text(stringResource(R.string.text_shrink))
                            }
                            OutlinedButton(onClick = { viewModel.adjustWeight(selectedKey.id, 0.5f) }) {
                                Text(stringResource(R.string.text_grow))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Remap
                        OutlinedButton(onClick = {
                            remapTarget = selectedKey.id
                            showRemapDialog = true
                        }) {
                            Text(stringResource(R.string.text_remap_output))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Validation Errors
            if (validationErrors.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        validationErrors.forEach { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (saveSuccess) {
                Text(
                    text = "Layout saved and applied.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                LaunchedEffect(saveSuccess) {
                    kotlinx.coroutines.delay(2000)
                    viewModel.clearSaveSuccess()
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.saveLayout() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.text_save_layout))
                }
                OutlinedButton(
                    onClick = { viewModel.resetToDefault() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.text_reset_to_qwerty))
                }
            }
        }
    }

    // Remap Dialog
    if (showRemapDialog && remapTarget != null) {
        var newOutput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRemapDialog = false },
            title = { Text(stringResource(R.string.text_remap_key)) },
            text = {
                Column {
                    Text(stringResource(R.string.text_enter_the_new_output_character_or_label))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newOutput,
                        onValueChange = { newOutput = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newOutput.isNotBlank()) {
                        viewModel.remapKey(remapTarget!!, newOutput)
                    }
                    showRemapDialog = false
                }) {
                    Text(stringResource(R.string.text_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemapDialog = false }) {
                    Text(stringResource(R.string.text_cancel))
                }
            }
        )
    }
}
