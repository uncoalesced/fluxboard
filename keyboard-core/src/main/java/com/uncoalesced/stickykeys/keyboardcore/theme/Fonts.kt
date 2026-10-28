// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The two type families, defined once.
 *
 * The brand calls for JetBrains Mono (headings, labels, numerals) and IBM Plex Sans (body). Both
 * are OFL, but neither is bundled yet, so these are the system monospace and sans faces. To
 * switch, add the TTFs under `res/font` with their licence file and change these two lines to
 * `FontFamily(Font(R.font.x, FontWeight.Normal), ...)`; nothing else needs to move.
 */
val FluxMono: FontFamily = FontFamily.Monospace

/** See [FluxMono]. */
val FluxSans: FontFamily = FontFamily.SansSerif

/** Headings sit slightly tight, as the brand kit specifies: -0.01em. */
private val HeadingTracking = (-0.01).em

/**
 * Material's type scale with the brand's families applied.
 *
 * Sizes are Material's own. What changes is the family and weight: everything a user scans
 * (display, headline, title, label) is mono at 600, and everything a user reads (body) is sans
 * at a 1.6 line height.
 */
fun fluxMaterialTypography(): Typography {
    val base = Typography()

    fun TextStyle.mono() =
        copy(
            fontFamily = FluxMono,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = HeadingTracking,
        )

    fun TextStyle.sans() = copy(fontFamily = FluxSans, lineHeight = (fontSize.value * 1.6f).sp)

    return base.copy(
        displayLarge = base.displayLarge.mono(),
        displayMedium = base.displayMedium.mono(),
        displaySmall = base.displaySmall.mono(),
        headlineLarge = base.headlineLarge.mono(),
        headlineMedium = base.headlineMedium.mono(),
        headlineSmall = base.headlineSmall.mono(),
        titleLarge = base.titleLarge.mono(),
        titleMedium = base.titleMedium.copy(fontFamily = FluxSans, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = FluxSans, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp).sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.mono(),
        labelMedium = base.labelMedium.mono(),
        labelSmall = base.labelSmall.mono(),
    )
}
