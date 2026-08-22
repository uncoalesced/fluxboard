// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

/** Height of the bar carrying Reset, the grab handle and Done. */
internal val RESIZE_BAR_HEIGHT = 48.dp

/** Travel that changes the height by one percent. */
private val HEIGHT_STEP: Dp = 2.dp

/**
 * The keyboard's own resize mode: drag the edges, then Done.
 *
 * ## What this is, and what it deliberately is not
 *
 * This is the resize mode Gboard ships on phones -- the keyboard stays docked to the bottom of
 * the window and its top edge is dragged to change how much room it takes. Height only: making
 * a keyboard narrower is a different feature (one-handed reach) wearing the same word, and
 * resizing means taller or shorter. It is also **not** a floating keyboard dropped anywhere on
 * screen. That is a genuinely different
 * feature: a free-floating IME needs an overlay window rather than the framework's
 * input-method window, which puts it in SYSTEM_ALERT_WINDOW territory. This project's position
 * is the narrowest permission set that does the job, so a floating board is a decision to take
 * deliberately rather than somewhere to end up by generalising a resize handle.
 *
 * ## Why the window does not track the drag
 *
 * An IME window is WRAP_CONTENT, so its height *is* its content's height: every change is a
 * measure, a push to WindowManager, and a relayout inside the host app being typed into. This
 * codebase already records that as the cause of a visible stutter when a toolbar animated its
 * own height -- about fifteen cross-process relayouts for one chevron tap. A drag would be one
 * per frame for as long as the finger moves.
 *
 * So while resize mode is open the space above the panel is padded out by `rememberResizeHeadroom`
 * to exactly what the panel is missing from its tallest possible size. The headroom shrinks by
 * whatever the panel grows, so their sum -- and therefore the window -- is constant for the whole
 * drag, while the keyboard underneath really does change size. The window is measured once
 * entering and once on Done.
 *
 * Pinning the panel itself to the maximum would have been the obvious way to hold the window
 * still, and it is wrong: it pins what is *drawn* too, so the handle moves and the keyboard
 * never does. That was built, tested on a phone, and observed doing nothing.
 */
@Composable
internal fun KeyboardResizeBar(
    onHeightDelta: (Int) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deltas rather than absolutes, so the gesture never has to know the current percentage
    // and cannot act on a stale copy of it.
    val currentHeightDelta by rememberUpdatedState(onHeightDelta)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(RESIZE_BAR_HEIGHT)
                .background(StickyKeysTheme.colors.surface),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ResizeTextButton(
            label = RESET_LABEL,
            description = "Reset keyboard size and position",
            onClick = onReset,
        )

        // The grab handle. Vertical drag is height, and the sign is the reason it reads as a
        // handle rather than a slider: it sits above the keys, so dragging it away from them
        // has to grow the thing it is attached to.
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        role = Role.Image
                        contentDescription = "Drag up or down to change the keyboard height"
                    }.pointerInput(Unit) {
                        // Keyed on Unit and read through rememberUpdatedState, because a
                        // pointerInput block keyed on the value would either restart the
                        // gesture on every step -- cancelling the drag under the finger -- or,
                        // keyed on nothing, keep calling the first lambda it was given with
                        // the percentage that was current when the finger went down.
                        val stepPx = HEIGHT_STEP.toPx()
                        // Travel is accumulated across the whole gesture rather than rounded
                        // per event. Pointer events arrive a few pixels apart, and a few
                        // pixels is less than one step, so rounding each one independently
                        // discards almost all of a slow drag -- measured on device as a handle
                        // that barely moved.
                        var travel = 0f
                        detectDragGestures(
                            onDragStart = { travel = 0f },
                            onDragCancel = { travel = 0f },
                            onDragEnd = { travel = 0f },
                        ) { change, drag ->
                            change.consume()
                            if (stepPx <= 0f) return@detectDragGestures
                            // Up is taller.
                            travel -= drag.y
                            val steps = (travel / stepPx).toInt()
                            if (steps != 0) {
                                travel -= steps * stepPx
                                currentHeightDelta(steps)
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(64.dp)
                        .height(4.dp)
                        .background(
                            StickyKeysTheme.colors.onSurfaceVariant,
                            StickyKeysTheme.shapes.small,
                        ),
            )
        }

        ResizeTextButton(
            label = DONE_LABEL,
            description = "Finish resizing the keyboard",
            onClick = onDone,
        )
    }
}

@Composable
private fun ResizeTextButton(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .padding(horizontal = 8.dp)
                .width(88.dp)
                .height(36.dp)
                .background(
                    StickyKeysTheme.colors.surfaceVariant,
                    StickyKeysTheme.shapes.small,
                ).clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = StickyKeysTheme.colors.onSurfaceVariant,
            style = StickyKeysTheme.typography.labelMedium,
        )
    }
}

internal const val RESET_LABEL = "Reset"

internal const val DONE_LABEL = "Done"
