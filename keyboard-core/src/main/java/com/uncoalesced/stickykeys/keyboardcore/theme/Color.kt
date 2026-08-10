// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.graphics.Color

// --- Brand palette (confirmed, "sticky6" board) -----------------------------
// Dark mode is the DEFAULT experience (see StickyKeysTheme). These five values
// are the source of truth; everything below maps them into semantic slots.
//
// Indigo #394053  darkest neutral -- the muted/special-key surface
// Iris   #4E4A59  neutral surface -- ordinary key fill
// Taupe  #6E6362  warm neutral -- secondary
// Sage   #839073  muted accent -- the locked/latched state
// Fern   #7CAE7A  the accent -- active states, selection, the accent key
//
// Contrast measured against the dark base (#000000), not estimated:
//   Fern 8.19  Sage 6.20  Taupe 3.62  Iris 2.45  Indigo 2.03
//
// Fern takes the accent role sticky3's yellow used to hold. It is the highest
// contrast of the five and the only one that reads as an accent rather than a
// neutral -- but it is NOT a like-for-like replacement: the old yellow measured
// 15.37 against black, so every accent on this board is roughly half as loud as
// it used to be. That is a property of the palette, flagged rather than hidden.
//
// The one forced consequence: Cream on Fern is 2.15, which is unreadable. Text
// on the accent is therefore near-black now (8.19) where it used to be Cream.
val Indigo = Color(0xFF394053)
val Iris = Color(0xFF4E4A59)
val Taupe = Color(0xFF6E6362)
val Sage = Color(0xFF839073)
val Fern = Color(0xFF7CAE7A)

/**
 * The foreground, which sticky6 does not supply.
 *
 * Every one of the five is a mid-tone, so a legible light foreground has to come
 * from outside the board. Cream is carried over from sticky3 for exactly that
 * reason: 8.68 on Indigo, 7.20 on Iris, 17.9 on the black base.
 */
val Cream = Color(0xFFE9EDDE)

/** Kept for the light palette's foreground and for [OnBrandYellow]. */
val Ink = Color(0xFF3D3B30)

/**
 * The dark-mode base.
 *
 * Pure black rather than [Ink]. Ink is a warm near-black that reads as washed-out grey next
 * to the keys drawn on it, and on the OLED panels this app's audience mostly carries it
 * costs power for a colour nobody asked for. Ink is kept, still named, and still used for
 * `OnBrandYellow` and the light palette's foreground -- this changes the base surface only,
 * not the brand.
 */
val TrueBlack = Color(0xFF000000)

/**
 * Highlight accent. Intentionally NOT wired into [StickyKeysColors]' interactive
 * slots so it can never become a default button/active color. Reference it
 * directly, sparingly (a single call-to-action, a "new"/unread indicator).
 * Always pair with [OnBrandYellow] for text -- Cream on Yellow fails contrast.
 */
val BrandYellow = Color(0xFFE7E247)
val OnBrandYellow = Ink // Ink-on-Yellow = 8.23:1 (Cream-on-Yellow would be 1.15:1)

// Derived tones (not new brand colors -- tonal variants for elevation/contrast):
// FernDeep exists only because the light palette needs an accent that white text
// can sit on: white on Fern is 2.15, white on FernDeep is 6.21. Same role BlueDark
// played for sticky3's Blue, and derived the same way.
val FernDeep = Color(0xFF3F6B3D)
val CreamSurface = Color(0xFFF3F5EC) // light-preset elevated surface
val CreamSurfaceVariant = Color(0xFFDCE0D2) // light-preset muted surface

val ErrorRed = Color(0xFFEF4444)
val ErrorRedDark = Color(0xFFDC2626)

/**
 * Plain Kotlin data class for StickyKeys colors.
 * This can be used outside of a Compose context if needed,
 * and is wrapped by ProvidableCompositionLocal for Compose UI.
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
)

// Contrast against the dark base (WCAG AA, measured not estimated):
//   Cream on TrueBlack 17.9  Cream on Iris 7.20  Cream on Indigo 8.68
//     -> text is legible on every surface this palette draws
//   onPrimary is near-black, not Cream: Cream on Fern is 2.15 and unreadable,
//     black on Fern is 8.19. This is the one slot sticky6 forced to change sign.
//   Fern vs TrueBlack 8.19, Sage vs TrueBlack 6.20 -> the accent and the latched
//     state are distinguishable from each other and from the base, which is what
//     makes caps lock visible.
fun darkStickyKeysColors() =
    StickyKeysColors(
        primary = Fern,
        primaryVariant = Sage,
        secondary = Taupe,
        background = TrueBlack,
        surface = Iris,
        surfaceVariant = Indigo,
        error = ErrorRedDark,
        // Near-black rather than Cream. See the note above -- this is not a
        // stylistic preference, Cream on Fern cannot be read.
        onPrimary = TrueBlack,
        onSecondary = Cream,
        onBackground = Cream,
        onSurface = Cream,
        onSurfaceVariant = Cream,
        onError = Color.White,
        isLight = false,
    )

fun lightStickyKeysColors() =
    StickyKeysColors(
        // FernDeep rather than Fern: the light palette puts white on the accent,
        // and white on Fern is 2.15 against 6.21 on FernDeep.
        primary = FernDeep,
        primaryVariant = Sage,
        secondary = Taupe,
        background = Cream,
        surface = CreamSurface,
        surfaceVariant = CreamSurfaceVariant,
        error = ErrorRed,
        onPrimary = Color.White,
        onSecondary = Cream,
        // Indigo is sticky6's darkest tone and reads 8.68 on Cream, so the light
        // palette's foreground comes from the board rather than from Ink.
        onBackground = Indigo,
        onSurface = Indigo,
        onSurfaceVariant = Indigo,
        onError = Color.White,
        isLight = true,
    )
