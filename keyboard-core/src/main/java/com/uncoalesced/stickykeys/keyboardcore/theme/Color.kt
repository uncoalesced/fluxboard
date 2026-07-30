// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.graphics.Color

// --- Brand palette (confirmed, "sticky3" board) -----------------------------
// Dark mode is the DEFAULT experience (see StickyKeysTheme). These five values
// are the source of truth; everything below maps them into semantic slots.
//
// Ink    #3D3B30  primary surface/background -- the dark-mode base
// Blue   #5C80BC  primary accent -- buttons, active states, keyboard accent key
// Slate  #4D5061  secondary neutral -- muted surfaces, secondary buttons
// Yellow #E7E247  secondary/highlight ONLY -- sparse; never a default button/
//                 active-state color (see BrandYellow, kept out of the semantic
//                 slots on purpose so it can't become a default accent)
// Cream  #E9EDDE  primary text/foreground on the dark base; light-mode bg
val Ink = Color(0xFF3D3B30)
val Blue = Color(0xFF5C80BC)
val Slate = Color(0xFF4D5061)
val Cream = Color(0xFFE9EDDE)

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
// BlueDark is a darker accent that passes AA for normal-size text on-accent
// (white 6.73:1, cream 5.65:1), where brand Blue alone reaches only ~3.4-4.0:1.
val BlueDark = Color(0xFF3F5C8C)
val InkSurfaceVariant = Color(0xFF43454F) // muted dark surface (Cream on it 8.0:1)
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

// Contrast against the dark base (WCAG AA, measured):
//   Cream on TrueBlack 17.9 (was 9.45 on Ink)  Cream on Slate 6.69  Cream on InkSurfaceVariant 8.00
//     -> text is legible everywhere, and the base change only improved it
//   onPrimary (Cream) on Blue 3.35 -> primary labels must be large/bold (>=18sp or bold 14sp); use primaryVariant (BlueDark) for normal-size text on-accent
//   Blue fill vs Ink 2.82, Slate vs Ink 1.41 -> filled elements share low luminance with the base; component edges rely on Material elevation + labels, not fill-vs-bg contrast. This is an accepted property of the brand palette, flagged rather than hidden.
fun darkStickyKeysColors() =
    StickyKeysColors(
        primary = Blue,
        primaryVariant = BlueDark,
        secondary = Slate,
        background = TrueBlack,
        surface = Slate,
        surfaceVariant = InkSurfaceVariant,
        error = ErrorRedDark,
        onPrimary = Cream,
        onSecondary = Cream,
        onBackground = Cream,
        onSurface = Cream,
        onSurfaceVariant = Cream,
        onError = Color.White,
        isLight = false,
    )

fun lightStickyKeysColors() =
    StickyKeysColors(
        primary = Blue,
        primaryVariant = BlueDark,
        secondary = Slate,
        background = Cream,
        surface = CreamSurface,
        surfaceVariant = CreamSurfaceVariant,
        error = ErrorRed,
        onPrimary = Color.White,
        onSecondary = Cream,
        onBackground = Ink,
        onSurface = Ink,
        onSurfaceVariant = Ink,
        onError = Color.White,
        isLight = true,
    )
