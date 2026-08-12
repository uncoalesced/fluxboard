// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

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

    // We still wrap in MaterialTheme just to provide basic defaults to underlying
    // Material components (like Ripple, Dialog, Surface defaults), but we map them
    // conceptually to our tokens to ensure a consistent look.
    // Nine more M3 slots mapped below, on top of the eight already forwarded. Material
    // components (dialogs, ripples, dropdown menus) read these directly and previously fell
    // back to M3's own baseline purple the instant they touched a slot this block did not set
    // -- visible as an inconsistency between FluxBoard's own drawn UI and anything routed
    // through stock Material. None of these are new colors: StickyKeysColors already carries
    // every value used here, this only widens how much of it Material sees.
    //
    // outline is the one exception -- StickyKeysColors has no border/divider field, so it
    // reads Taupe directly rather than adding one. Taupe is already documented in Color.kt as
    // the "warm neutral," and outline (dividers, unfocused borders) never sits adjacent to
    // secondary on screen, so reusing the same hex for both is safe.
    val materialColors =
        if (darkTheme) {
            androidx.compose.material3.darkColorScheme(
                primary = colors.primary,
                background = colors.background,
                surface = colors.surface,
                error = colors.error,
                onPrimary = colors.onPrimary,
                onBackground = colors.onBackground,
                onSurface = colors.onSurface,
                onError = colors.onError,
                secondary = colors.secondary,
                onSecondary = colors.onSecondary,
                primaryContainer = colors.primaryVariant,
                onPrimaryContainer = colors.onSurface,
                secondaryContainer = colors.surfaceVariant,
                onSecondaryContainer = colors.onSurfaceVariant,
                surfaceVariant = colors.surfaceVariant,
                onSurfaceVariant = colors.onSurfaceVariant,
                surfaceContainer = colors.surface,
                outline = Taupe,
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
                onError = colors.onError,
                secondary = colors.secondary,
                onSecondary = colors.onSecondary,
                primaryContainer = colors.primaryVariant,
                onPrimaryContainer = colors.onSurface,
                secondaryContainer = colors.surfaceVariant,
                onSecondaryContainer = colors.onSurfaceVariant,
                surfaceVariant = colors.surfaceVariant,
                onSurfaceVariant = colors.onSurfaceVariant,
                surfaceContainer = colors.surface,
                outline = Taupe,
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
            content = content,
        )
    }
}
