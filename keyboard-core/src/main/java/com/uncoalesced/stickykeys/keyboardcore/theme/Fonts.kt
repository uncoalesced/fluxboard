// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.uncoalesced.stickykeys.keyboardcore.R

/**
 * One entry of a variable font: the file holds every weight, and the variation setting picks
 * which one this [Font] draws. minSdk is 26, which is where variable font axes start working.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(
    resId: Int,
    weight: FontWeight,
): Font {
    val axes = FontVariation.Settings(FontVariation.weight(weight.weight))
    return Font(resId, weight, variationSettings = axes)
}

/**
 * The two type families, defined once.
 *
 * JetBrains Mono (headings, labels, numerals) and IBM Plex Sans (body), both SIL OFL 1.1, bundled
 * as variable fonts under `res/font` (licences in `docs/licenses/`). Only the weights the app
 * draws are declared; a request for another weight snaps to the nearest declared one.
 */
val FluxMono: FontFamily =
    FontFamily(
        variable(R.font.jetbrains_mono, FontWeight.Normal),
        variable(R.font.jetbrains_mono, FontWeight.SemiBold),
    )

/** See [FluxMono]. */
val FluxSans: FontFamily =
    FontFamily(
        variable(R.font.ibm_plex_sans, FontWeight.Normal),
        variable(R.font.ibm_plex_sans, FontWeight.Medium),
        variable(R.font.ibm_plex_sans, FontWeight.SemiBold),
    )

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
