// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
    val keyboardKey: TextStyle
)

enum class TypeScale {
    SMALL, MEDIUM, LARGE
}

fun stickyKeysTypography(scale: TypeScale = TypeScale.MEDIUM): StickyKeysTypography {
    val scaleFactor = when (scale) {
        TypeScale.SMALL -> 0.85f
        TypeScale.MEDIUM -> 1.0f
        TypeScale.LARGE -> 1.15f
    }

    return StickyKeysTypography(
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = (22 * scaleFactor).sp,
            lineHeight = (28 * scaleFactor).sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = (18 * scaleFactor).sp,
            lineHeight = (24 * scaleFactor).sp,
            letterSpacing = 0.15.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (16 * scaleFactor).sp,
            lineHeight = (24 * scaleFactor).sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * scaleFactor).sp,
            lineHeight = (20 * scaleFactor).sp,
            letterSpacing = 0.25.sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * scaleFactor).sp,
            lineHeight = (20 * scaleFactor).sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * scaleFactor).sp,
            lineHeight = (16 * scaleFactor).sp,
            letterSpacing = 0.5.sp
        ),
        keyboardKey = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (24 * scaleFactor).sp,
            lineHeight = (32 * scaleFactor).sp,
            letterSpacing = 0.sp
        )
    )
}
