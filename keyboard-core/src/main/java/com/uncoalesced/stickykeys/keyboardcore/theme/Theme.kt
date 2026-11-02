// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalStickyKeysColors =
    staticCompositionLocalOf<StickyKeysColors> {
        error("No StickyKeysColors provided")
    }

val LocalStickyKeysTypography =
    staticCompositionLocalOf<StickyKeysTypography> {
        error("No StickyKeysTypography provided")
    }

val LocalStickyKeysSpacing =
    staticCompositionLocalOf<StickyKeysSpacing> {
        error("No StickyKeysSpacing provided")
    }

val LocalStickyKeysShapes =
    staticCompositionLocalOf<StickyKeysShapes> {
        error("No StickyKeysShapes provided")
    }

/**
 * Per-key presentation overrides.
 *
 * Defaulted rather than erroring like the others, because every existing call site that
 * renders a key predates it and must keep working untouched -- and because the default *is*
 * the shipped look, so a missing provider is a correct keyboard rather than a bug.
 */
val LocalStickyKeysKeyStyle = staticCompositionLocalOf { KeyStyle.Default }

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

    val keyStyle: KeyStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalStickyKeysKeyStyle.current
}

@Composable
fun StickyKeysTheme(
    // Dark mode is the default first-launch experience, not system-driven.
    // Light remains available as an explicit alternate (customColors / presets).
    darkTheme: Boolean = true,
    typeScale: TypeScale = TypeScale.MEDIUM,
    customColors: StickyKeysColors? = null,
    keyStyle: KeyStyle = KeyStyle.Default,
    content: @Composable () -> Unit,
) {
    val colors =
        customColors ?: if (darkTheme) {
            darkStickyKeysColors()
        } else {
            lightStickyKeysColors()
        }

    val typography = stickyKeysTypography(scale = typeScale)
    val spacing = defaultStickyKeysSpacing
    val shapes = defaultStickyKeysShapes

    // Material components (dialogs, buttons, text fields, menus) read these slots directly, so
    // every one of them is mapped rather than left to M3's baseline purple.
    //
    // Material's `primary` is the accent *text* colour, not the Watermelon fill. Material draws
    // text in its primary slot all over the place -- every TextButton label, every
    // OutlinedButton label, a focused field's label -- and Watermelon is 3.12 to 3.73 against
    // this app's grounds, under the 4.5 that normal-size text needs. Mapping the fill there would
    // put sub-contrast text on every dialog. So Material buttons fill with the text tone (Tint in
    // dark, 7.89 with a black label; Shade in light, 6.36 with a Porcelain label), and the places
    // the brand reserves for the pure accent -- switches, sliders, the dock, indicators, the
    // keyboard's own accent key -- ask for `StickyKeysTheme.colors.primary` explicitly.
    //
    // `tertiary` is the pure accent, for the one small piece of Material that wants a loud fill
    // with a dark label (the pre-release badge): black on Watermelon is 5.18.
    val materialColors =
        if (darkTheme) {
            androidx.compose.material3.darkColorScheme(
                primary = colors.accentText,
                onPrimary = colors.onAccentText,
                primaryContainer = colors.primaryVariant,
                onPrimaryContainer = Porcelain,
                secondary = colors.secondary,
                onSecondary = colors.onSecondary,
                secondaryContainer = colors.surfaceVariant,
                onSecondaryContainer = colors.onSurface,
                tertiary = colors.primary,
                onTertiary = colors.onPrimary,
                tertiaryContainer = colors.surface,
                onTertiaryContainer = colors.onSurface,
                background = colors.background,
                onBackground = colors.onBackground,
                surface = colors.background,
                onSurface = colors.onSurface,
                surfaceVariant = colors.surfaceVariant,
                onSurfaceVariant = colors.onSurfaceVariant,
                surfaceContainerLowest = colors.background,
                surfaceContainerLow = colors.surface,
                surfaceContainer = colors.surface,
                surfaceContainerHigh = colors.surface,
                surfaceContainerHighest = colors.surfaceVariant,
                error = colors.error,
                onError = colors.onError,
                outline = colors.outline,
                outlineVariant = colors.outline,
            )
        } else {
            androidx.compose.material3.lightColorScheme(
                primary = colors.accentText,
                onPrimary = colors.onAccentText,
                primaryContainer = colors.primaryVariant,
                onPrimaryContainer = Porcelain,
                secondary = colors.secondary,
                onSecondary = colors.onSecondary,
                secondaryContainer = colors.surfaceVariant,
                onSecondaryContainer = colors.onSurface,
                tertiary = colors.primary,
                onTertiary = colors.onPrimary,
                tertiaryContainer = colors.surface,
                onTertiaryContainer = colors.onSurface,
                background = colors.background,
                onBackground = colors.onBackground,
                surface = colors.background,
                onSurface = colors.onSurface,
                surfaceVariant = colors.surfaceVariant,
                onSurfaceVariant = colors.onSurfaceVariant,
                surfaceContainerLowest = colors.background,
                surfaceContainerLow = colors.surface,
                surfaceContainer = colors.surface,
                surfaceContainerHigh = colors.surface,
                surfaceContainerHighest = colors.surfaceVariant,
                error = colors.error,
                onError = colors.onError,
                outline = colors.outline,
                outlineVariant = colors.outline,
            )
        }
    CompositionLocalProvider(
        LocalStickyKeysColors provides colors,
        LocalStickyKeysTypography provides typography,
        LocalStickyKeysSpacing provides spacing,
        LocalStickyKeysShapes provides shapes,
        LocalStickyKeysKeyStyle provides keyStyle,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = fluxMaterialTypography(),
            // Dialogs, menus, sheets and text fields take the brand's two radii instead of
            // Material's 28dp dialog and 16dp sheet corners.
            shapes =
                androidx.compose.material3.Shapes(
                    extraSmall = RoundedCornerShape(4.dp),
                    small = RoundedCornerShape(4.dp),
                    medium = RoundedCornerShape(10.dp),
                    large = RoundedCornerShape(10.dp),
                    extraLarge = RoundedCornerShape(10.dp),
                ),
            content = content,
        )
    }
}
