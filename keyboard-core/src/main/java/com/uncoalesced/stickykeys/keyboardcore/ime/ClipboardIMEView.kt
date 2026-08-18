// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.ClipboardEntryEntity
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

/** Matches the emoji picker's tab strip, so switching panels does not move the toolbar. */
private val CLIPBOARD_BAR_HEIGHT = 40.dp

/**
 * The clipboard history panel.
 *
 * Rebuilt on the keyboard's own tokens rather than on Material defaults. It was the last IME
 * surface still drawing `Card`, `IconButton` and `TextButton`, which meant it ignored the active
 * theme's palette and shapes entirely: a user on a custom theme got their colours everywhere
 * except here. The three controls were also bare text -- "Back", "Clear all", "DEL" -- where the
 * rest of the keyboard uses icons, and "DEL" beside every row read as the word rather than as a
 * delete affordance.
 *
 * Timestamps are shown because a clipboard kept until the user clears it is a list that grows
 * without bound, and two similar entries with no way to tell which is the recent one is the
 * state it spends most of its life in.
 */
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
                .background(StickyKeysTheme.colors.background),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CLIPBOARD_BAR_HEIGHT)
                    .background(StickyKeysTheme.colors.surface)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        // The bar is 40dp tall, so the drawn size cannot be 48. This expands
                        // the *touch* area to the minimum without changing the layout, which is
                        // what the guideline actually asks for.
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button, onClick = onBackToKeyboard)
                        .semantics {
                            contentDescription = "Back to keyboard"
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_quick_arrow),
                    contentDescription = null,
                    tint = StickyKeysTheme.colors.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(R.string.clipboard_title),
                style = StickyKeysTheme.typography.labelLarge,
                color = StickyKeysTheme.colors.onSurface,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            // Hidden rather than disabled when the history is empty. A greyed control on a
            // 40dp bar is a smaller target for the eye than no control at all, and there is
            // nothing here for it to explain.
            if (entries.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .background(
                                StickyKeysTheme.colors.surfaceVariant,
                                StickyKeysTheme.shapes.small,
                            ).clickable(role = Role.Button) { viewModel.clearAll() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .semantics {
                                contentDescription =
                                    "Clear all ${entries.size} clipboard entries"
                            },
                ) {
                    Text(
                        text = stringResource(R.string.text_clear_all),
                        style = StickyKeysTheme.typography.labelMedium,
                        color = StickyKeysTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // Says why it is empty as well as that it is. The history only ever fills
                    // from copies made while this keyboard is installed, so a first-time user
                    // seeing "Clipboard is empty" had no way to know whether it was broken.
                    text = stringResource(R.string.clipboard_empty),
                    style = StickyKeysTheme.typography.bodyMedium,
                    color = StickyKeysTheme.colors.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    StickyKeysTheme.colors.surfaceVariant,
                    StickyKeysTheme.shapes.medium,
                ).clickable(onClick = onClick, role = Role.Button)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp)
                .semantics(mergeDescendants = true) {
                    // Without this the row announces only its truncated text, giving no
                    // clue that activating it pastes.
                    contentDescription = "Paste: ${entry.text}"
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.text,
                style = StickyKeysTheme.typography.bodyMedium,
                color = StickyKeysTheme.colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeAge(System.currentTimeMillis() - entry.timestamp),
                style = StickyKeysTheme.typography.labelMedium,
                color = StickyKeysTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onDelete)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Delete clipboard entry"
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_key_search_close),
                contentDescription = null,
                tint = StickyKeysTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * How long ago, in the shortest form that is still unambiguous.
 *
 * Pure so the boundaries can be asserted without a clock. Deliberately coarse: the question a
 * clipboard row answers is "is this the one I just copied", not what time it was, and a
 * clipboard kept forever will mostly hold entries whose exact minute stopped mattering days ago.
 */
internal fun relativeAge(millis: Long): String {
    val seconds = millis / 1000
    return when {
        // Negative means the row is dated in the future, which a device clock change can do.
        // Reporting "now" is the only honest answer available and beats a negative count.
        seconds < 60 -> "Just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        seconds < 604_800 -> "${seconds / 86_400}d ago"
        else -> "${seconds / 604_800}w ago"
    }
}
