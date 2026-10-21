// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.PRESS_COLOR_ANIM_MS
import com.uncoalesced.stickykeys.keyboardcore.theme.PRESS_SCALE
import com.uncoalesced.stickykeys.keyboardcore.theme.PressSpring
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.pressedFill

/**
 * One tappable word in the suggestion strip, and the undo affordance that replaces them.
 *
 * Previously a bare `Text` with a `clickable` and some padding: a word with a hit box, drawn
 * with no indication that it was a control at all. The strip is where a wrong autocorrect gets
 * undone and a wrong glide gets fixed in one tap, so being visibly tappable is not decoration
 * there -- it is the difference between a repair the user finds and one they retype around.
 *
 * Deliberately built from the same tokens as a key rather than given a treatment of its own.
 * The strip sits directly above the grid and is touched by the same thumb; two surfaces that
 * respond differently to the same gesture read as two different applications.
 */
@Composable
internal fun StripChip(
    label: String,
    description: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    announce: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (pressed) PRESS_SCALE else 1f,
            animationSpec = PressSpring,
            label = "chip-scale",
        )
    val resting = StickyKeysTheme.colors.surfaceVariant
    val accent = StickyKeysTheme.colors.primary
    val background by
        animateColorAsState(
            if (pressed) pressedFill(resting, accent) else resting,
            animationSpec = tween(if (pressed) 0 else PRESS_COLOR_ANIM_MS),
            label = "chip-background",
        )
    Text(
        text = label,
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(StickyKeysTheme.shapes.pill)
                .background(background)
                // indication = null for the same reason keys carry none: the strip is under
                // the thumb that pressed it, and a ripple animating outward from beneath a
                // fingertip is the feedback most easily hidden by the finger causing it. The
                // scale and fill above replace it rather than merely removing it.
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 12.dp, vertical = 5.dp)
                .semantics {
                    contentDescription = description
                    if (announce) liveRegion = LiveRegionMode.Polite
                },
        color = color,
        style = StickyKeysTheme.typography.labelLarge,
        maxLines = 1,
    )
}
