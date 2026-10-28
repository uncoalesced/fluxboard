// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.graphics.Color

// --- Brand palette: Watermelon on Graphite and Porcelain ----------------------
// Replaces the earlier "sticky6" board. One accent hue, two grounds, and the
// neutrals each theme needs. Source: the FluxBoard brand kit (DESIGN.md and
// Colour/tokens.json in the design folder).
//
// Contrast, measured with the WCAG relative-luminance formula, not estimated:
//
//   Watermelon on dark bg #262626      3.73   fills, icons, focus only
//   Watermelon on surface #333333      3.12   fills, icons only
//   Watermelon on Porcelain            4.03   fills, icons only
//   Tint #F47790 on dark bg            5.68   accent text, dark mode
//   Tint on surface #333333            4.75   accent text on a card, dark mode
//   Shade #AD2F4A on Porcelain         6.36   accent text, light mode
//   Shade on light surface #F1F3F0     5.73   accent text on a card, light mode
//   Black on Watermelon                5.18   label on an accent fill (both modes)
//   Porcelain on Watermelon            4.03   large or bold text only, so not used
//   Black on Tint                      7.89   label on a dark-mode accent-text fill
//   Porcelain on Shade                 6.36   label on a light-mode accent-text fill
//   Porcelain on #262626 / #333333    15.05 / 12.57   body text, dark
//   Muted #B4B5B3 on #262626 / #333333 7.35 / 6.14    secondary text, dark
//   Graphite on Porcelain / #F1F3F0   12.57 / 11.32   body text, light
//   Muted #5C5E5C on Porcelain / #F1F3F0 6.51 / 5.86  secondary text, light
//
// The rule that follows from the first three lines: Watermelon is never a text
// colour. Anything that is read uses the accent-text tone for its mode.

/** Surfaces on dark, light-mode ink, the icon ground. */
val Graphite = Color(0xFF333333)

/** Light ground and dark-mode ink. */
val Porcelain = Color(0xFFFDFFFC)

/** The accent, as a fill: selected state, switches, sliders, indicators, the lifted key. */
val Watermelon = Color(0xFFEF2D56)

/** Pressed accent, and the accent text colour in light mode. */
val WatermelonShade = Color(0xFFAD2F4A)

/** Accent text in dark mode, and a secondary highlight. */
val WatermelonTint = Color(0xFFF47790)

// Theme neutrals. Not brand colours, the grounds and lines each mode needs.
val DarkBackground = Color(0xFF262626)
val DarkBorder = Color(0xFF4B4B4B)
val DarkInkMuted = Color(0xFFB4B5B3)
val LightSurface = Color(0xFFF1F3F0)
val LightBorder = Color(0xFFDFE0DE)
val LightInkMuted = Color(0xFF5C5E5C)

/** Label colour on an accent fill. 5.18 on Watermelon, where Porcelain manages 4.03. */
val OnAccent = Color(0xFF000000)

val ErrorRed = Color(0xFFEF4444)
val ErrorRedDark = Color(0xFFDC2626)

/**
 * Plain Kotlin data class for StickyKeys colors.
 * This can be used outside of a Compose context if needed,
 * and is wrapped by ProvidableCompositionLocal for Compose UI.
 *
 * [primary] is the accent *fill*. [accentText] is the accent as text, which is a different
 * colour under this palette because the fill does not reach 4.5:1 on either ground.
 * [onAccentText] is the label colour when [accentText] is itself used as a fill, which is what
 * Material's own buttons do with their primary slot. [outline] is the 1dp border on flat
 * surfaces. All three default to the older single-accent behaviour, so a saved keyboard theme
 * written before they existed loads exactly as it did.
 */
data class StickyKeysColors(
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val error: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val onError: Color,
    val isLight: Boolean,
    val accentText: Color = primary,
    val onAccentText: Color = onPrimary,
    val outline: Color = secondary,
)

fun darkStickyKeysColors() =
    StickyKeysColors(
        primary = Watermelon,
        // Pressed or latched accent. Black glyphs on it measure 3.28, which is enough for
        // the shift icon it carries but is why no text is ever drawn on it.
        primaryVariant = WatermelonShade,
        secondary = DarkBorder,
        background = DarkBackground,
        surface = Graphite,
        // Special keys and the suggestion strip: one step up from the key fill.
        surfaceVariant = DarkBorder,
        error = ErrorRedDark,
        onPrimary = OnAccent,
        onSecondary = Porcelain,
        onBackground = Porcelain,
        onSurface = Porcelain,
        // Muted ink: 6.14 on a card, 4.24 on a special key, where it draws icons.
        onSurfaceVariant = DarkInkMuted,
        onError = Color.White,
        isLight = false,
        accentText = WatermelonTint,
        onAccentText = OnAccent,
        outline = DarkBorder,
    )

fun lightStickyKeysColors() =
    StickyKeysColors(
        primary = Watermelon,
        primaryVariant = WatermelonShade,
        secondary = LightBorder,
        background = Porcelain,
        surface = LightSurface,
        surfaceVariant = LightBorder,
        error = ErrorRed,
        onPrimary = OnAccent,
        onSecondary = Graphite,
        onBackground = Graphite,
        onSurface = Graphite,
        onSurfaceVariant = LightInkMuted,
        onError = Color.White,
        isLight = true,
        accentText = WatermelonShade,
        onAccentText = Porcelain,
        outline = LightBorder,
    )
