// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.uncoalesced.stickykeys.keyboardcore.theme.FluxMono
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

/**
 * A screen's title: mono, in ink, followed by a small Watermelon square.
 *
 * The square is the wordmark's mark, carried onto every top-level screen. It is the accent used
 * as a fill, which is the only way this palette allows it: as text, Watermelon is 3.73 on the
 * dark ground and under the 4.5 normal text needs, so the words themselves are ink.
 */
@Composable
fun ScreenHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier =
                Modifier
                    .padding(top = 6.dp)
                    .size(9.dp)
                    .background(StickyKeysTheme.colors.primary, RoundedCornerShape(2.dp)),
        )
    }
}

/**
 * A labelled card: the grouping unit on the Keyboard and Settings screens.
 *
 * Flat, as the brand kit draws surfaces: a 1dp border on the surface colour and no shadow. The
 * label is an eyebrow in the brand's sense -- mono, 12sp, uppercase, widely tracked, in muted
 * ink -- and the optional icon beside it carries the accent, because an icon is a fill and the
 * label is text.
 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = StickyKeysTheme.colors.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title.uppercase(),
                fontFamily = FluxMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.08.em,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = StickyKeysTheme.shapes.medium,
            color = StickyKeysTheme.colors.surface,
            contentColor = StickyKeysTheme.colors.onSurface,
            border = BorderStroke(1.dp, StickyKeysTheme.colors.outline),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                content = content,
            )
        }
    }
}

/**
 * A row that opens something else: title, optional summary, and a chevron.
 *
 * The chevron is an icon rather than the "->" glyph these rows used to end with, which was text
 * at title size and the only arrow in the app that did not match the system's own.
 */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Switch colours with the accent as the checked track.
 *
 * Material's own default would take the accent *text* tone, because Material's primary slot is
 * mapped there so its text-bearing defaults stay legible. A switch track is a fill, which is
 * what the brand reserves Watermelon for, so it asks for it explicitly. The thumb on it is
 * black: 5.18 against the track.
 */
@Composable
fun brandSwitchColors(): SwitchColors =
    SwitchDefaults.colors(
        checkedTrackColor = StickyKeysTheme.colors.primary,
        checkedThumbColor = StickyKeysTheme.colors.onPrimary,
        checkedBorderColor = StickyKeysTheme.colors.primary,
        uncheckedTrackColor = StickyKeysTheme.colors.background,
        uncheckedBorderColor = StickyKeysTheme.colors.outline,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/** Slider colours with the accent as the thumb and active track. See [brandSwitchColors]. */
@Composable
fun brandSliderColors(): SliderColors =
    SliderDefaults.colors(
        thumbColor = StickyKeysTheme.colors.primary,
        activeTrackColor = StickyKeysTheme.colors.primary,
        activeTickColor = StickyKeysTheme.colors.onPrimary,
        inactiveTrackColor = StickyKeysTheme.colors.outline,
        inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/** Checkbox colours with the accent as the checked fill. See [brandSwitchColors]. */
@Composable
fun brandCheckboxColors(): CheckboxColors =
    CheckboxDefaults.colors(
        checkedColor = StickyKeysTheme.colors.primary,
        checkmarkColor = StickyKeysTheme.colors.onPrimary,
        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
