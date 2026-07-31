// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysColors
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

/** Height of the revealed row. Matches the suggestion strip so the panel grows predictably. */
internal val QUICK_ROW_HEIGHT = 44.dp

/**
 * One entry in the quick-access row.
 *
 * [comingSoon] is carried on the action rather than checked at the call site, so an
 * unimplemented feature cannot be wired to a real handler by accident -- the stub is a
 * property of the action, not of the code path that happens to invoke it.
 */
internal data class QuickAction(
    val id: String,
    @DrawableRes val icon: Int,
    val label: String,
    val comingSoon: Boolean = false,
)

/**
 * The row revealed by the chevron.
 *
 * Mic is deliberately absent here and sits at the top-right of the strip instead: it is the
 * one action a user reaches for mid-sentence, and burying it behind a disclosure toggle
 * would cost two taps every time. Grammar sits here because, unlike the reference's pencil,
 * it is a real planned feature.
 */
internal val quickActions =
    listOf(
        QuickAction("grid", R.drawable.ic_quick_grid, "Stickers and emoji"),
        QuickAction("translate", R.drawable.ic_quick_translate, "Translate", comingSoon = true),
        QuickAction("grammar", R.drawable.ic_quick_grammar, "Grammar check", comingSoon = true),
        QuickAction("clipboard", R.drawable.ic_key_clipboard, "Clipboard history"),
        QuickAction("textedit", R.drawable.ic_quick_text_edit, "Text editing"),
        QuickAction("switchime", R.drawable.ic_quick_switch_keyboard, "Switch keyboard"),
    )

/** The disclosure chevron that sits at the left end of the suggestion strip. */
@Composable
internal fun QuickAccessToggle(
    expanded: Boolean,
    palette: StickyKeysColors,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One asset rotated, rather than an up and a down drawable that can drift apart.
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "quick-chevron")
    Box(
        modifier =
            modifier
                .size(32.dp)
                // Drawn small enough for the 40dp strip, touchable at the 48dp minimum.
                // Every small control on the keyboard is standardized this way rather than
                // each picking its own compromise between fitting and being hittable.
                .minimumInteractiveComponentSize()
                .background(palette.surfaceVariant, RoundedCornerShape(16.dp))
                .clickable(onClick = onToggle)
                .semantics {
                    role = Role.Button
                    contentDescription = "Quick actions"
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_quick_arrow),
            contentDescription = null,
            tint = palette.onSurfaceVariant,
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
    }
}

/** The revealed icon row. Collapses to zero height so it costs nothing when closed. */
@Composable
internal fun QuickAccessRow(
    expanded: Boolean,
    palette: StickyKeysColors,
    onAction: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(QUICK_ROW_HEIGHT)
                    .background(palette.surface),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            quickActions.forEach { action ->
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .clickable { onAction(action) }
                            .semantics {
                                role = Role.Button
                                contentDescription =
                                    if (action.comingSoon) {
                                        "${action.label}, coming soon"
                                    } else {
                                        action.label
                                    }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(action.icon),
                        contentDescription = null,
                        tint = palette.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * The "coming soon" notice, drawn inside the keyboard.
 *
 * Not an `AlertDialog`: a dialog is a separate window, and a separate window raised from an
 * IME either steals focus from the field being typed into or is placed behind the keyboard,
 * depending on the OEM. An in-panel card cannot do either.
 */
@Composable
internal fun ComingSoonNotice(
    label: String,
    palette: StickyKeysColors,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .background(palette.surfaceVariant, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = "$label: this feature is coming soon!",
                color = palette.onSurfaceVariant,
                style = StickyKeysTheme.typography.labelLarge,
            )
        }
    }
}
