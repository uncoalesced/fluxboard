// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyStyle
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyboardTheme

/**
 * A small fixed palette plus "use the theme's own colour".
 *
 * A full colour picker is not offered here on purpose. The point of this screen is to make
 * the keyboard feel personal in a few taps; an HSV wheel makes it possible to produce an
 * unreadable keyboard in one gesture and is the sort of control that makes a settings screen
 * feel like work. Null -- inherit from the palette -- is deliberately first and is the
 * default, so the good-looking option is the one already selected.
 */
private val SWATCHES =
    listOf<Color?>(
        null,
        Color(0xFF000000),
        Color(0xFF1C1C1E),
        Color(0xFF3A3A3C),
        Color(0xFF8E8E93),
        Color(0xFFFFFFFF),
        Color(0xFFE8C547),
        Color(0xFF4C8BF5),
        Color(0xFF34C759),
        Color(0xFFFF3B30),
    )

/**
 * The per-key styling panel.
 *
 * Collapsed behind its own disclosure, because it is the deep end of the screen. Someone who
 * never opens it sees the same keyboard they always did -- every control below starts at the
 * value that reproduces the shipped look.
 */
@Composable
fun KeyStyleControls(
    style: KeyStyle,
    onChange: ((KeyStyle) -> KeyStyle) -> Unit,
    hasBackgroundImage: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Key styling"
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Key styling", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Fill, text, borders and glow. Defaults look like the shipped keyboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (expanded) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
        }

        if (!expanded) return@Column

        StyleSection("Key fill") {
            SwatchRow(
                selected = style.fillColor,
                onSelect = { picked -> onChange { it.copy(fillColor = picked) } },
            )
            OpacitySlider(
                label = "Fill opacity",
                value = style.fillOpacity,
                onChange = { v -> onChange { it.copy(fillOpacity = v) } },
            )
        }

        StyleSection("Text") {
            SwatchRow(
                selected = style.textColor,
                onSelect = { picked -> onChange { it.copy(textColor = picked) } },
            )
            OpacitySlider(
                label = "Text opacity",
                value = style.textOpacity,
                onChange = { v -> onChange { it.copy(textOpacity = v) } },
                // Glyphs below this are treated as absent and restored to opaque on save, so
                // the travel below it was only ever a value that snapped back. The fill slider
                // keeps its full range on purpose: a transparent key is a legitimate look.
                minValue = KeyboardTheme.MIN_VISIBLE_ALPHA,
            )
        }

        StyleSection("Borders") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show key borders",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = style.borderEnabled,
                    onCheckedChange = { on -> onChange { it.copy(borderEnabled = on) } },
                )
            }
            // The colour and opacity below do nothing while borders are off, so they are not
            // shown at all rather than shown greyed out and inviting a pointless tap.
            if (style.borderEnabled) {
                SwatchRow(
                    selected = style.borderColor,
                    onSelect = { picked -> onChange { it.copy(borderColor = picked) } },
                )
                OpacitySlider(
                    label = "Border opacity",
                    value = style.borderOpacity,
                    onChange = { v -> onChange { it.copy(borderOpacity = v) } },
                )
                OpacitySlider(
                    label = "Border width",
                    value = style.borderWidth.value / MAX_BORDER_WIDTH_DP,
                    onChange = { v ->
                        onChange { it.copy(borderWidth = (v * MAX_BORDER_WIDTH_DP).dp) }
                    },
                    readout = "${"%.1f".format(style.borderWidth.value)} dp",
                )
            }
        }

        StyleSection("Haze") {
            Text(
                "A soft glow behind each key. Off at zero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwatchRow(
                selected = style.hazeColor,
                onSelect = { picked -> onChange { it.copy(hazeColor = picked) } },
            )
            OpacitySlider(
                label = "Haze strength",
                value = style.hazeOpacity,
                onChange = { v -> onChange { it.copy(hazeOpacity = v) } },
            )
            if (style.hazeOpacity > 0f) {
                OpacitySlider(
                    label = "Haze spread",
                    value = style.hazeRadius.value / MAX_HAZE_RADIUS_DP,
                    onChange = { v ->
                        onChange { it.copy(hazeRadius = (v * MAX_HAZE_RADIUS_DP).dp) }
                    },
                    readout = "${"%.0f".format(style.hazeRadius.value)} dp",
                )
            }
        }

        if (hasBackgroundImage) {
            StyleSection("Background image") {
                OpacitySlider(
                    label = "Image opacity",
                    value = style.backgroundImageOpacity,
                    onChange = { v -> onChange { it.copy(backgroundImageOpacity = v) } },
                )
            }
        }

        TextButton(onClick = { onChange { KeyStyle.Default } }) {
            Text("Reset key styling to defaults")
        }
    }
}

private const val MAX_BORDER_WIDTH_DP = 4f
private const val MAX_HAZE_RADIUS_DP = 24f

@Composable
private fun StyleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(title, style = MaterialTheme.typography.titleSmall)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), content = content)
}

@Composable
private fun SwatchRow(
    selected: Color?,
    onSelect: (Color?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SWATCHES.forEach { swatch ->
            val isSelected = swatch == selected
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(swatch ?: Color.Transparent, CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            shape = CircleShape,
                        ).clickable { onSelect(swatch) }
                        .semantics {
                            role = Role.RadioButton
                            contentDescription =
                                if (swatch == null) "Use theme colour" else "Colour swatch"
                            stateDescription = if (isSelected) "Selected" else "Not selected"
                        },
                contentAlignment = Alignment.Center,
            ) {
                // The inherit swatch needs a mark of its own: an empty circle among coloured
                // ones reads as "no colour", which is the opposite of what it means.
                if (swatch == null) {
                    Text("T", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * @param minValue the lowest value the control may reach. Non-zero only where the value below
 *   it is one `KeyboardTheme.sanitized()` would rewrite -- a slider that can be dragged into a
 *   value that snaps back is indistinguishable from a broken one.
 */
@Composable
private fun OpacitySlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    readout: String? = null,
    minValue: Float = 0f,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                readout ?: "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(minValue, 1f),
            onValueChange = onChange,
            valueRange = minValue..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A single key drawn with the current style, for the swatch previews. */
@Composable
fun KeyStylePreviewChip(
    style: KeyStyle,
    baseFill: Color,
    baseText: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    val border = style.resolveBorder(baseText)
    Box(
        modifier =
            modifier
                .size(width = 44.dp, height = 40.dp)
                .background(style.resolveFill(baseFill), RoundedCornerShape(6.dp))
                .then(
                    border?.let { Modifier.border(style.borderWidth, it, RoundedCornerShape(6.dp)) }
                        ?: Modifier,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = style.resolveText(baseText))
    }
}
