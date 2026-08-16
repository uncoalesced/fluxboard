// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The keyboard's type face: the system's rounded sans, where the system has one.
 *
 * A device font family alias rather than a bundled file, which is what makes this free. There
 * is no font to ship, so nothing lands on the 100 MB budget, and nothing has a licence to
 * check. `sans-serif-rounded` has been a standard alias since API 16; where an OEM skin does
 * not provide it, the platform resolves the request to the ordinary system sans, which is
 * exactly what this project drew before -- so the failure mode is "no change", not "tofu".
 *
 * Resolved once at file scope rather than per TextStyle: the family is identical for all
 * seven, and building it seven times per typography call is seven Typeface lookups for one
 * answer.
 */
private val SystemRounded = FontFamily(Font(DeviceFontFamilyName("sans-serif-rounded")))

/**
 * Plain Kotlin data class for StickyKeys typography.
 */
data class StickyKeysTypography(
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val keyboardKey: TextStyle,
)

enum class TypeScale {
    SMALL,
    MEDIUM,
    LARGE,
}

fun stickyKeysTypography(scale: TypeScale = TypeScale.MEDIUM): StickyKeysTypography {
    val scaleFactor =
        when (scale) {
            TypeScale.SMALL -> 0.85f
            TypeScale.MEDIUM -> 1.0f
            TypeScale.LARGE -> 1.15f
        }

    return StickyKeysTypography(
        titleLarge =
            TextStyle(
                fontFamily = SystemRounded,
                fontWeight = FontWeight.Bold,
                fontSize = (22 * scaleFactor).sp,
                lineHeight = (28 * scaleFactor).sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = SystemRounded,
                fontWeight = FontWeight.SemiBold,
                fontSize = (18 * scaleFactor).sp,
                lineHeight = (24 * scaleFactor).sp,
                letterSpacing = 0.15.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = SystemRounded,
                fontWeight = FontWeight.Normal,
                fontSize = (16 * scaleFactor).sp,
                lineHeight = (24 * scaleFactor).sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = SystemRounded,
                fontWeight = FontWeight.Normal,
                fontSize = (14 * scaleFactor).sp,
                lineHeight = (20 * scaleFactor).sp,
                letterSpacing = 0.25.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = SystemRounded,
                fontWeight = FontWeight.Medium,
                fontSize = (14 * scaleFactor).sp,
                lineHeight = (20 * scaleFactor).sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = SystemRounded,
                fontWeight = FontWeight.Medium,
                fontSize = (12 * scaleFactor).sp,
                lineHeight = (16 * scaleFactor).sp,
                letterSpacing = 0.5.sp,
            ),
        keyboardKey =
            TextStyle(
                fontFamily = SystemRounded,
                fontWeight = FontWeight.Medium,
                fontSize = (24 * scaleFactor).sp,
                lineHeight = (32 * scaleFactor).sp,
                letterSpacing = 0.sp,
            ),
    )
}
