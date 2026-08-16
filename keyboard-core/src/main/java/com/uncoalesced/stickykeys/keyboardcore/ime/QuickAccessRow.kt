// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.theme.PANEL_FADE_MS
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysColors
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 *
 * Seven is the ceiling, and it is arithmetic rather than taste. Every small control here is
 * expanded to the 48dp accessibility minimum, so seven fills 336dp of a 360dp phone and an
 * eighth would have to overlap its neighbour's touch target. That is the reason the media
 * transport is its own row above rather than three more icons in this one.
 */
internal val quickActions =
    listOf(
        QuickAction("grid", R.drawable.ic_quick_grid, "Stickers and emoji"),
        QuickAction("translate", R.drawable.ic_quick_translate, "Translate", comingSoon = true),
        QuickAction("grammar", R.drawable.ic_quick_grammar, "Grammar check", comingSoon = true),
        QuickAction("clipboard", R.drawable.ic_key_clipboard, "Clipboard history"),
        QuickAction("textedit", R.drawable.ic_quick_text_edit, "Text editing"),
        QuickAction("private", R.drawable.ic_quick_private, "Private mode"),
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
                .background(palette.surfaceVariant, StickyKeysTheme.shapes.pill)
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

/**
 * The revealed rows. Collapses to zero height so they cost nothing when closed.
 *
 * Two rows, not one: media transport above, actions below. Both live outside the fixed-height
 * panel and grow the window upward, so neither ever takes height from the keys.
 *
 * [privateMode] tints the privacy action instead of swapping its icon. A padlock that opens
 * and closes would be a second, quieter statement of the same fact the lit indicator in the
 * suggestion strip already makes, and the two are easy to leave disagreeing.
 */
@Composable
internal fun QuickAccessRow(
    expanded: Boolean,
    palette: StickyKeysColors,
    privateMode: Boolean,
    onAction: (QuickAction) -> Unit,
    onMedia: (MediaTransport.Action) -> Unit,
    isMediaPlaying: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = expanded,
        // Fade only. The height deliberately does **not** animate, and that is the opposite of
        // what it looks like it should be.
        //
        // This row lives outside the fixed-height panel and grows the window upward, which is
        // the whole reason it never steals height from the keys. But an IME window is
        // WRAP_CONTENT: its height *is* this content's height, measured by the framework and
        // pushed across to WindowManager and into the host app's own layout pass. So
        // `expandVertically` did not animate a view inside a stable window -- it resized the
        // IME window, and with it relaid out the app being typed into, once per frame for the
        // length of the animation. That is a cross-process relayout roughly fifteen times for
        // one chevron tap, and it is why the expand visibly stepped through its frames instead
        // of sliding. Measured elsewhere in this file's own history: a height change here moves
        // the window by exactly the row height, 830px to 956px.
        //
        // One resize, then a fade over it. The window reaches its final size in a single layout
        // pass and the icons arrive over the space that is already there, so the motion the eye
        // follows costs nothing to draw.
        enter = fadeIn(tween(PANEL_FADE_MS)),
        exit = fadeOut(tween(PANEL_FADE_MS)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MediaRow(
                palette = palette,
                onMedia = onMedia,
                isMediaPlaying = isMediaPlaying,
            )
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
                    val on = action.id == "private" && privateMode
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .minimumInteractiveComponentSize()
                                .background(
                                    if (on) palette.primary else Color.Transparent,
                                    StickyKeysTheme.shapes.pill,
                                ).clickable { onAction(action) }
                                .semantics {
                                    role = Role.Button
                                    contentDescription =
                                        if (action.comingSoon) {
                                            "${action.label}, coming soon"
                                        } else {
                                            action.label
                                        }
                                    if (action.id == "private") {
                                        // Announced, not merely coloured. A toggle whose only
                                        // state cue is a fill is invisible to a screen reader,
                                        // and this is the one control here where not knowing
                                        // its state is a privacy question rather than a
                                        // cosmetic one.
                                        stateDescription = if (on) "On" else "Off"
                                    }
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(action.icon),
                            contentDescription = null,
                            tint = if (on) palette.onPrimary else palette.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Play/pause and skip for whatever the platform considers the active media session.
 *
 * Always drawn while the toolbar is open, never gated on something already playing. Gating it
 * looked tidier and breaks the most ordinary case there is: pause a track, and the row that
 * would let you resume it has disappeared, because the signal it was gated on is exactly the
 * thing pausing turned off.
 *
 * Three buttons at 44dp rather than seven at 36dp, because there is room for them and skip is
 * a control people hit while walking.
 */
@Composable
private fun MediaRow(
    palette: StickyKeysColors,
    onMedia: (MediaTransport.Action) -> Unit,
    isMediaPlaying: () -> Boolean,
) {
    // Re-read on each open rather than polled. There is no callback to subscribe to without
    // the notification-listener permission -- which this app does not declare, because Play
    // Protect blocks the install of any sideloaded app that does -- and a timer ticking inside
    // an IME to keep a glyph fresh would cost battery in every session for a row most of them
    // never open.
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Off the main thread. `isMusicActive` is a binder call into AudioService, and this
        // effect fires on the frame the toolbar opens -- the one frame in the interaction that
        // has no time to spare. The answer only picks a glyph, so arriving a frame late costs
        // nothing and blocking for it would be felt.
        playing = withContext(Dispatchers.IO) { isMediaPlaying() }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(QUICK_ROW_HEIGHT)
                .background(palette.surfaceVariant),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mediaButtons.forEach { (action, label) ->
            val isPlayPause = action == MediaTransport.Action.PLAY_PAUSE
            val icon =
                when {
                    !isPlayPause && action == MediaTransport.Action.PREVIOUS ->
                        R.drawable.ic_media_previous
                    !isPlayPause -> R.drawable.ic_media_next
                    playing -> R.drawable.ic_media_pause
                    else -> R.drawable.ic_media_play
                }
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .clickable {
                            onMedia(action)
                            // The player needs a moment to react before isMusicActive changes,
                            // so flip the glyph on the press. If the guess is wrong the next
                            // open of the toolbar re-reads and corrects it.
                            if (isPlayPause) playing = !playing
                        }.semantics {
                            role = Role.Button
                            contentDescription =
                                if (isPlayPause && playing) "Pause" else label
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = palette.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Ordered as they are drawn: back, play/pause, forward. */
private val mediaButtons =
    listOf(
        MediaTransport.Action.PREVIOUS to "Previous track",
        MediaTransport.Action.PLAY_PAUSE to "Play",
        MediaTransport.Action.NEXT to "Next track",
    )

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
                    .background(palette.surfaceVariant, StickyKeysTheme.shapes.medium)
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
