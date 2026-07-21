// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalStickyKeysColors = staticCompositionLocalOf<StickyKeysColors> {
    error("No StickyKeysColors provided")
}

val LocalStickyKeysTypography = staticCompositionLocalOf<StickyKeysTypography> {
    error("No StickyKeysTypography provided")
}

val LocalStickyKeysSpacing = staticCompositionLocalOf<StickyKeysSpacing> {
    error("No StickyKeysSpacing provided")
}

val LocalStickyKeysShapes = staticCompositionLocalOf<StickyKeysShapes> {
    error("No StickyKeysShapes provided")
}

object StickyKeysTheme {
    val colors: StickyKeysColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStickyKeysColors.current

    val typography: StickyKeysTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalStickyKeysTypography.current

    val spacing: StickyKeysSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalStickyKeysSpacing.current

    val shapes: StickyKeysShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalStickyKeysShapes.current
}

@Composable
fun StickyKeysTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    typeScale: TypeScale = TypeScale.MEDIUM,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkStickyKeysColors()
    } else {
        lightStickyKeysColors()
    }

    val typography = stickyKeysTypography(scale = typeScale)
    val spacing = defaultStickyKeysSpacing
    val shapes = defaultStickyKeysShapes

    // We still wrap in MaterialTheme just to provide basic defaults to underlying 
    // Material components (like Ripple, Dialog, Surface defaults), but we map them 
    // conceptually to our tokens to ensure a consistent look.
    val materialColors = if (darkTheme) {
        androidx.compose.material3.darkColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.surface,
            error = colors.error,
            onPrimary = colors.onPrimary,
            onBackground = colors.onBackground,
            onSurface = colors.onSurface,
            onError = colors.onError
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.surface,
            error = colors.error,
            onPrimary = colors.onPrimary,
            onBackground = colors.onBackground,
            onSurface = colors.onSurface,
            onError = colors.onError
        )
    }

    CompositionLocalProvider(
        LocalStickyKeysColors provides colors,
        LocalStickyKeysTypography provides typography,
        LocalStickyKeysSpacing provides spacing,
        LocalStickyKeysShapes provides shapes
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
