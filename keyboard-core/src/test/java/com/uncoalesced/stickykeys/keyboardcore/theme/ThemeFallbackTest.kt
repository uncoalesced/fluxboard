// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The blank-canvas defence.
 *
 * A theme lives in `filesDir` and is pointed at by a SharedPreferences id, so a bad one
 * survives force-stop, cache clearing and reboot and is cleared only by uninstalling. These
 * assert that no saved state can put the renderer in a position where it draws nothing.
 *
 * Robolectric because colour parsing goes through `android.graphics.Color`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeFallbackTest {
    private fun themeWith(keyStyle: KeyStyle) =
        KeyboardTheme(
            id = "custom_test",
            name = "Test",
            isLight = false,
            colors = darkStickyKeysColors(),
            typeScale = TypeScale.MEDIUM,
            keyStyle = keyStyle,
        )

    @Test
    fun `a good theme is returned untouched`() {
        // The whole guard must be inert for every theme that is merely unusual. A user who
        // wants translucent keys over a photo keeps exactly what they chose.
        val theme = themeWith(KeyStyle(fillOpacity = 0.35f, borderEnabled = true))
        assertSame(theme, theme.sanitized())
    }

    @Test
    fun `every shipped preset survives sanitization unchanged`() {
        listOf(KeyboardTheme.fallback(isLight = false), KeyboardTheme.fallback(isLight = true))
            .forEach { preset ->
                assertSame(
                    "Preset '${preset.id}' must not be rewritten",
                    preset,
                    preset.sanitized(),
                )
            }
    }

    @Test
    fun `fully transparent key text is restored`() {
        // The single control that blanks a keyboard on its own: the glyphs stop being drawn
        // and every key becomes an empty rectangle.
        val cleaned = themeWith(KeyStyle(textOpacity = 0f)).sanitized()
        assertEquals(1f, cleaned.keyStyle.textOpacity, 0.001f)
        assertNull(cleaned.keyStyle.textColor)
    }

    @Test
    fun `transparent keys and transparent text together are both restored`() {
        val cleaned = themeWith(KeyStyle(fillOpacity = 0f, textOpacity = 0f)).sanitized()
        assertEquals(1f, cleaned.keyStyle.fillOpacity, 0.001f)
        assertEquals(1f, cleaned.keyStyle.textOpacity, 0.001f)
    }

    @Test
    fun `key text the same colour as the key is restored`() {
        // Two colour pickers defaulting to the same swatch reaches this in two taps, and the
        // result is a panel of blank keys rather than an obviously broken screen.
        val cleaned =
            themeWith(
                KeyStyle(fillColor = Color(0xFF101010), textColor = Color(0xFF101010)),
            ).sanitized()
        assertNull(cleaned.keyStyle.fillColor)
        assertNull(cleaned.keyStyle.textColor)
    }

    @Test
    fun `a palette with no contrast between surface and its text falls back`() {
        // The same mistake expressed one layer down, where no key override is involved at all.
        val flat = darkStickyKeysColors().copy(surface = Color.Black, onSurface = Color.Black)
        val cleaned =
            KeyboardTheme(
                id = "custom_flat",
                name = "Flat",
                isLight = false,
                colors = flat,
                typeScale = TypeScale.MEDIUM,
            ).sanitized()
        assertTrue(
            "surface and onSurface must not stay identical",
            cleaned.colors.surface != cleaned.colors.onSurface,
        )
    }

    @Test
    fun `a background image path pointing at a deleted file is dropped`() {
        val cleaned =
            KeyboardTheme(
                id = "custom_img",
                name = "Img",
                isLight = false,
                colors = darkStickyKeysColors(),
                typeScale = TypeScale.MEDIUM,
                backgroundImagePath = "/data/does/not/exist.png",
            ).sanitized()
        assertNull(cleaned.backgroundImagePath)
    }

    @Test
    fun `the fallback theme is renderable without reading any asset or file`() {
        // This is what resolves when asset loading itself has failed, so it must not depend
        // on anything that could have been what failed.
        val fallback = KeyboardTheme.fallback()
        assertNotNull(fallback.colors)
        assertTrue(fallback.colors.surface != fallback.colors.onSurface)
        assertEquals(KeyboardTheme.FALLBACK_DARK_ID, fallback.id)
        assertSame(fallback, fallback.sanitized())
    }

    @Test
    fun `a sanitized theme still round trips through json`() {
        val cleaned = themeWith(KeyStyle(textOpacity = 0f, borderEnabled = true)).sanitized()
        val restored = KeyboardTheme.fromJson(cleaned.toJson().toString())
        assertEquals(cleaned.keyStyle.textOpacity, restored.keyStyle.textOpacity, 0.001f)
        assertTrue(restored.keyStyle.borderEnabled)
    }
}
