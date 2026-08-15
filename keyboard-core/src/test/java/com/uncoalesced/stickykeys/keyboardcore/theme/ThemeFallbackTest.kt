// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
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
    fun `transparent keys and transparent text restore only the text`() {
        // Restoring the glyph is enough to make the keyboard readable, and the fill is then
        // left exactly as the user set it. Resetting both was the reported bug: it wiped a
        // deliberate fill value that had nothing to do with the edit being made.
        val cleaned = themeWith(KeyStyle(fillOpacity = 0f, textOpacity = 0f)).sanitized()
        assertEquals(1f, cleaned.keyStyle.textOpacity, 0.001f)
        assertEquals(0f, cleaned.keyStyle.fillOpacity, 0.001f)
    }

    @Test
    fun `a low fill survives a later edit that drags the text below the threshold`() {
        // The reported sequence, exactly: fill to 5% (accepted, saved), then the text slider
        // dragged down in a separate edit. Every drag tick round-trips through sanitized(),
        // and this used to snap *both* sliders to 100% because the fill check was reading the
        // pre-repair text value. Only the text may move.
        val accepted = themeWith(KeyStyle(fillOpacity = 0.05f)).sanitized()
        assertEquals(0.05f, accepted.keyStyle.fillOpacity, 0.001f)

        val afterTextDrag =
            accepted
                .copy(keyStyle = accepted.keyStyle.copy(textOpacity = 0.05f))
                .sanitized()
        assertEquals(0.05f, afterTextDrag.keyStyle.fillOpacity, 0.001f)
        assertEquals(1f, afterTextDrag.keyStyle.textOpacity, 0.001f)
    }

    @Test
    fun `a repaired glyph is still readable over the fill the user kept`() {
        // The reason the fill may be left alone: whatever it is, the restored glyph has to
        // remain visible against it, which is the contrast check's job rather than a second
        // opacity reset. A fully transparent key is the hardest case -- there is no key colour
        // at all, only the panel behind it.
        val cleaned = themeWith(KeyStyle(fillOpacity = 0f, textOpacity = 0f)).sanitized()
        val text = cleaned.keyStyle.resolveText(cleaned.colors.onSurface)
        val fill = cleaned.keyStyle.resolveFill(cleaned.colors.surface)
        assertTrue("glyph must be opaque after repair", text.alpha >= 0.99f)
        assertTrue("fill must be left transparent", fill.alpha <= 0.01f)
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

    /** Loads a preset the way ThemeManager does, from the asset that actually ships. */
    private fun shippedPreset(name: String): KeyboardTheme {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val json =
            context.assets
                .open("themes/$name.json")
                .bufferedReader()
                .use { it.readText() }
        return KeyboardTheme.fromJson(json)
    }

    @Test
    fun `the shipped preset files survive sanitization unchanged`() {
        // The test above covers KeyboardTheme.fallback(), which is the *Kotlin* default. These
        // are the JSON files, which are what a user actually gets -- and, per this project's
        // own history, a Kotlin token and the preset that overrides it have disagreed before.
        listOf("default_dark", "default_light", "amoled_dark").forEach { name ->
            val preset = shippedPreset(name)
            assertSame(
                "Preset '$name' must not be rewritten by sanitization",
                preset,
                preset.sanitized(),
            )
        }
    }

    @Test
    fun `the two depth presets ship haze and the AMOLED one does not`() {
        // Without this the test above passes vacuously: a keyStyle block that failed to parse
        // would load as KeyStyle.Default, sanitize to itself, and look perfectly healthy.
        listOf("default_dark", "default_light").forEach { name ->
            val style = shippedPreset(name).keyStyle
            assertTrue("Preset '$name' should carry haze", style.hazeOpacity > 0f)
            assertNotNull(
                "Preset '$name' haze must resolve to a drawable colour",
                style.resolveHaze(Color.Gray),
            )
            assertNull(
                "Preset '$name' must derive its haze from the palette, not a hardcoded hex",
                style.hazeColor,
            )
        }
        // Flat black with no glow is the entire point of an OLED preset; a shadow behind every
        // key would light pixels it exists to keep dark.
        assertEquals(0f, shippedPreset("amoled_dark").keyStyle.hazeOpacity, 0.0001f)
    }
}
