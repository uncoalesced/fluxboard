// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.ClipboardEntryEntity
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardIMEView(
    viewModel: ClipboardIMEViewModel,
    onPasteText: (String) -> Unit,
    onBackToKeyboard: () -> Unit,
) {
    val entries by viewModel.clipboardEntries.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                // Clamped against the window: a flat 280dp left almost nothing of the host
                // app visible in a split-screen pane.
                .height(rememberImePanelHeight())
                .background(StickyKeysTheme.colors.surface),
    ) {
        // Toolbar
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(StickyKeysTheme.colors.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBackToKeyboard) {
                Text(
                    stringResource(R.string.text_back),
                    color = StickyKeysTheme.colors.onSurfaceVariant,
                )
            }
            Text(
                text = "Clipboard History",
                style = StickyKeysTheme.typography.titleMedium,
                color = StickyKeysTheme.colors.onSurfaceVariant,
            )
            TextButton(
                onClick = { viewModel.clearAll() },
                enabled = entries.isNotEmpty(),
            ) {
                Text(stringResource(R.string.text_clear_all))
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Clipboard is empty",
                    style = StickyKeysTheme.typography.bodyLarge,
                    color = StickyKeysTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    ClipboardEntryRow(
                        entry = entry,
                        onClick = { onPasteText(entry.text) },
                        onDelete = { viewModel.deleteEntry(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun ClipboardEntryRow(
    entry: ClipboardEntryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick, role = Role.Button)
                .semantics(mergeDescendants = true) {
                    // Without this the row announces only its truncated text, giving no
                    // clue that activating it pastes.
                    contentDescription = "Paste: ${entry.text}"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = StickyKeysTheme.colors.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.text,
                style = StickyKeysTheme.typography.bodyMedium,
                color = StickyKeysTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDelete,
                modifier =
                    Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "Delete clipboard entry" },
            ) {
                Text(
                    stringResource(R.string.text_del),
                    color = StickyKeysTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}
