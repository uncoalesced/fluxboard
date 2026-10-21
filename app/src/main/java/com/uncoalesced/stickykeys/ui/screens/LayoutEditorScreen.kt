// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyGlyph
import com.uncoalesced.stickykeys.keyboardcore.layout.REMAPPABLE_ACTIONS
import com.uncoalesced.stickykeys.keyboardcore.layout.keyGlyph
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: LayoutEditorViewModel = hiltViewModel(),
) {
    val editingLayout by viewModel.editingLayout.collectAsState()
    val selectedKeyId by viewModel.selectedKeyId.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()
    val editingPage by viewModel.editingPage.collectAsState()
    val pageRows = editingLayout.pages()[editingPage]
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val availableLayouts by viewModel.availableLayouts.collectAsState()

    var showRemapDialog by remember { mutableStateOf(false) }
    var remapTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.text_layout_editor)) })
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(8.dp),
        ) {
            // Layout Selector
            Text(
                text = "Saved Layouts",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableLayouts.forEach { layout ->
                    FilterChip(
                        selected = layout.id == editingLayout.id,
                        onClick = { viewModel.setActiveLayout(layout.id) },
                        label = { Text(layout.name, fontSize = 12.sp) },
                    )
                }
            }

            Divider()

            // Visual Layout Editor
            Text(
                text = "Editing: ${editingLayout.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // Which plane is being edited. The symbol pages have been part of the saved
            // layout since v0.1.4 and were never reachable here, so a user could remap the
            // letters and nothing else.
            //
            // The number row is deliberately absent: it is generated at render time from
            // KeyboardLayouts.numberRow rather than stored in the layout, so there is nothing
            // here to edit. Offering a tab for it would be a control that silently discards
            // what the user did.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PAGE_LABELS.forEachIndexed { index, label ->
                    FilterChip(
                        selected = index == editingPage,
                        onClick = { viewModel.selectPage(index) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }

            // Keyboard Preview / Editor
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            StickyKeysTheme.shapes.medium,
                        ).padding(4.dp),
            ) {
                pageRows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEachIndexed { keyIndex, keyDef ->
                            val isSelected = keyDef.id == selectedKeyId
                            // Hoisted: inline it and the border line runs past 100 chars
                            // purely from Compose nesting depth.
                            val selectionBorder =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .weight(keyDef.weight)
                                        .padding(2.dp)
                                        .height(44.dp)
                                        .clip(StickyKeysTheme.shapes.small)
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                        ).border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = selectionBorder,
                                            shape = StickyKeysTheme.shapes.small,
                                        ).clickable { viewModel.selectKey(keyDef.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                // Same table the live keyboard draws from. This used to be its
                                // own "Sh"/"Del"/"Ent" abbreviations, so a layout being edited
                                // never looked like the keyboard it was producing.
                                val tint =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                when (val glyph = keyGlyph(keyDef.output, keyDef.displayLabel)) {
                                    is KeyGlyph.Icon ->
                                        Icon(
                                            painter = painterResource(glyph.res),
                                            contentDescription = glyph.description,
                                            tint = tint,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    is KeyGlyph.Label ->
                                        Text(
                                            text = glyph.text,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center,
                                            color = tint,
                                        )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Controls for Selected Key
            val selectedKey = pageRows.flatten().find { it.id == selectedKeyId }
            if (selectedKey != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Selected: ${selectedKey.output} (weight: ${"%.1f".format(
                                selectedKey.weight,
                            )})",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Swap controls
                        val rowIndex =
                            pageRows.indexOfFirst { row ->
                                row.any {
                                    it.id ==
                                        selectedKey.id
                                }
                            }
                        val keyIndex =
                            pageRows.getOrNull(rowIndex)?.indexOfFirst {
                                it.id ==
                                    selectedKey.id
                            }
                                ?: -1

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.swapKeys(rowIndex, keyIndex, keyIndex - 1) },
                                enabled = keyIndex > 0,
                            ) { Text(stringResource(R.string.text_move_left)) }

                            OutlinedButton(
                                onClick = { viewModel.swapKeys(rowIndex, keyIndex, keyIndex + 1) },
                                enabled =
                                    keyIndex <
                                        (pageRows.getOrNull(rowIndex)?.size ?: 0) - 1,
                            ) { Text(stringResource(R.string.text_move_right)) }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Resize controls
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.adjustWeight(selectedKey.id, -0.5f) },
                            ) {
                                Text(stringResource(R.string.text_shrink))
                            }
                            OutlinedButton(
                                onClick = { viewModel.adjustWeight(selectedKey.id, 0.5f) },
                            ) {
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
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        validationErrors.forEach { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.bodyMedium,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.saveLayout() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.text_save_layout))
                }
                OutlinedButton(
                    onClick = { viewModel.resetToDefault() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.text_reset_to_qwerty))
                }
            }
        }
    }

    // Remap Dialog
    if (showRemapDialog && remapTarget != null) {
        var newOutput by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var custom by remember { mutableStateOf(false) }

        fun apply(output: String) {
            when (val result = viewModel.remapKey(remapTarget!!, output)) {
                is RemapResult.Applied -> showRemapDialog = false
                is RemapResult.Blank -> error = "Enter something for the key to type."
                is RemapResult.Reserved ->
                    error =
                        "\"${result.output}\" is a command this keyboard reads rather than " +
                        "types, so a key set to it would do nothing. Pick another."
            }
        }

        AlertDialog(
            onDismissRequest = { showRemapDialog = false },
            title = { Text(stringResource(R.string.text_remap_key)) },
            text = {
                Column {
                    // A picker rather than a bare text field. Almost every remap is one of a
                    // few dozen characters, and typing one into a box is both slower and the
                    // only way to reach the states the validator has to reject.
                    Text("Pick what this key types:")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(REMAP_PRESETS.size) { index ->
                            val option = REMAP_PRESETS[index]
                            FilterChip(
                                selected = false,
                                onClick = { apply(option) },
                                label = { Text(option, fontSize = 14.sp) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Destinations, listed apart from the characters because they are a
                    // different kind of thing: these keys open a panel rather than type. Until
                    // now the dialog rejected every control token without distinguishing the
                    // structural ones from these, which is why no key could be pointed at emoji.
                    Text("...or jump straight to:")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(REMAPPABLE_ACTIONS.size) { index ->
                            val action = REMAPPABLE_ACTIONS[index]
                            FilterChip(
                                selected = false,
                                onClick = { apply(action) },
                                label = {
                                    Text(ACTION_LABELS[action] ?: action, fontSize = 14.sp)
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Kept, because remapping a key to a personal shorthand string is a real
                    // and already-supported use that must not regress.
                    if (custom) {
                        OutlinedTextField(
                            value = newOutput,
                            onValueChange = {
                                newOutput = it
                                error = null
                            },
                            singleLine = true,
                            isError = error != null,
                        )
                    } else {
                        TextButton(onClick = { custom = true }) { Text("Custom text...") }
                    }
                    error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { apply(newOutput) },
                    enabled = custom,
                ) {
                    Text(stringResource(R.string.text_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemapDialog = false }) {
                    Text(stringResource(R.string.text_cancel))
                }
            },
        )
    }
}

/** Editable planes, in the order KeyboardLayoutConfig.pages() returns them. */
private val PAGE_LABELS = listOf("Letters", "Symbols 1", "Symbols 2")

/**
 * Human names for the action tokens.
 *
 * The tokens themselves are what the keyboard reads, and showing "EMOJI" in a chip would make
 * the dialog look like it wanted the user to know the vocabulary.
 */
private val ACTION_LABELS =
    mapOf(
        "EMOJI" to "Emoji",
        "STICKERS" to "Stickers",
        "CLIPBOARD" to "Clipboard",
    )

/**
 * The curated remap vocabulary.
 *
 * A plain list of values, not an enum with behaviour: these *are* the outputs, and a type
 * wrapping each one would add a layer whose only job is to hand back the string it was named
 * after. Control tokens are deliberately absent -- see CONTROL_OUTPUTS, which is what rejects
 * them if someone types one into the custom field.
 */
private val REMAP_PRESETS =
    listOf(
        ".",
        ",",
        "?",
        "!",
        "'",
        "\"",
        ":",
        ";",
        "-",
        "_",
        "/",
        "@",
        "#",
        "&",
        "*",
        "+",
        "=",
        "%",
        "$",
        "(",
        ")",
        "[",
        "]",
        "{",
        "}",
        "<",
        ">",
        "~",
        "^",
        "|",
        "\\",
        "0",
        "1",
        "2",
        "3",
        "4",
        "5",
        "6",
        "7",
        "8",
        "9",
    )
