// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.graphics.Color

// Primitives
val Indigo500 = Color(0xFF6366F1)
val Indigo400 = Color(0xFF818CF8)
val Indigo600 = Color(0xFF4F46E5)

val Teal400 = Color(0xFF2DD4BF)
val Teal300 = Color(0xFF5EEAD4)

val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF020617)

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
    val isLight: Boolean
)

fun lightStickyKeysColors() = StickyKeysColors(
    primary = Indigo500,
    primaryVariant = Indigo600,
    secondary = Teal400,
    background = Slate50,
    surface = Color.White,
    surfaceVariant = Slate100,
    error = ErrorRed,
    onPrimary = Color.White,
    onSecondary = Slate900,
    onBackground = Slate900,
    onSurface = Slate900,
    onSurfaceVariant = Slate800,
    onError = Color.White,
    isLight = true
)

fun darkStickyKeysColors() = StickyKeysColors(
    primary = Indigo400,
    primaryVariant = Indigo500,
    secondary = Teal300,
    background = Slate950,
    surface = Slate900,
    surfaceVariant = Slate800,
    error = ErrorRedDark,
    onPrimary = Slate900,
    onSecondary = Slate900,
    onBackground = Slate50,
    onSurface = Slate50,
    onSurfaceVariant = Slate200,
    onError = Color.White,
    isLight = false
)
