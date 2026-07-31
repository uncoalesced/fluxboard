// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysColors
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

/**
 * Cursor movement, selection and clipboard actions, without leaving the keyboard.
 *
 * Clipboard actions go through the host editor via `performContextMenuAction`, so its own undo
 * stack, rich text and any overridden paste behaviour keep working.
 *
 * Movement deliberately does **not** go through arrow key events. A DPAD key event that the
 * editor cannot consume -- caret already at the end, or a field that ignores arrows -- falls
 * through to the host window's focus search and moves focus out of the text field entirely.
 * [KeyboardController.moveCursor] addresses the InputConnection instead, which cannot reach
 * outside the editor it belongs to.
 */
@Composable
internal fun TextEditPanel(
    controller: KeyboardController,
    palette: StickyKeysColors,
    onBackToKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Latching Select rather than requiring a second finger: the arrows and the modifier are
    // on the same surface, so a chord is not physically available the way it is on hardware.
    var selecting by remember { mutableStateOf(false) }

    fun move(direction: CursorMove) = controller.moveCursor(direction, extend = selecting)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(palette.background)
                .padding(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditChip(
                label = "Back",
                palette = palette,
                onClick = onBackToKeyboard,
                modifier = Modifier.weight(1f),
            )
            EditChip(
                label = if (selecting) "Select: on" else "Select",
                palette = palette,
                active = selecting,
                stateLabel = if (selecting) "On" else "Off",
                onClick = { selecting = !selecting },
                modifier = Modifier.weight(1f),
            )
            EditChip(
                label = "Select all",
                palette = palette,
                onClick = { controller.performEditAction(android.R.id.selectAll) },
                modifier = Modifier.weight(1f),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Movement cluster. Up and down are included because a multi-line field is
            // exactly where reaching in with a fingertip is worst.
            Column(modifier = Modifier.weight(2f)) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    EditChip("Start", palette, Modifier.weight(1f)) {
                        move(CursorMove.DOC_START)
                    }
                    EditChip("Up", palette, Modifier.weight(1f)) {
                        move(CursorMove.UP)
                    }
                    EditChip("End", palette, Modifier.weight(1f)) {
                        move(CursorMove.DOC_END)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    EditChip("Left", palette, Modifier.weight(1f)) {
                        move(CursorMove.LEFT)
                    }
                    EditChip("Down", palette, Modifier.weight(1f)) {
                        move(CursorMove.DOWN)
                    }
                    EditChip("Right", palette, Modifier.weight(1f)) {
                        move(CursorMove.RIGHT)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                EditChip("Copy", palette, Modifier.fillMaxWidth().weight(1f)) {
                    controller.performEditAction(android.R.id.copy)
                }
                EditChip("Paste", palette, Modifier.fillMaxWidth().weight(1f)) {
                    controller.performEditAction(android.R.id.paste)
                }
            }
        }
    }
}

@Composable
private fun EditChip(
    label: String,
    palette: StickyKeysColors,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    stateLabel: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .padding(2.dp)
                .fillMaxHeight()
                .background(
                    if (active) palette.primary else palette.surfaceVariant,
                    RoundedCornerShape(6.dp),
                ).clickable(onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    stateLabel?.let { stateDescription = it }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) palette.onPrimary else palette.onSurfaceVariant,
            maxLines = 1,
            style = StickyKeysTheme.typography.labelLarge,
        )
    }
}

/** Overload used by the movement grid, where the trailing lambda reads better last. */
@Composable
private fun EditChip(
    label: String,
    palette: StickyKeysColors,
    modifier: Modifier,
    onClick: () -> Unit,
) = EditChip(
    label = label,
    palette = palette,
    modifier = modifier,
    active = false,
    stateLabel = null,
    onClick = onClick,
)
