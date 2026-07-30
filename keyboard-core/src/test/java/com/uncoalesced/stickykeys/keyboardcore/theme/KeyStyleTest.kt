// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The per-key styling layer.
 *
 * Weighted towards the two things that can quietly break existing users: a theme saved
 * before this feature existed must still load as the shipped look, and an untouched style
 * must resolve to exactly the colours the keyboard used before the layer was added.
 *
 * Robolectric because colour parsing goes through `android.graphics.Color`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyStyleTest {
    @Test
    fun `an untouched style changes nothing it is given`() {
        // The whole "sensible defaults" requirement in one assertion: a user who never opens
        // the customization screen must get the palette values unmodified.
        val style = KeyStyle.Default
        val fill = Color(0xFF334455)
        val text = Color(0xFFEEDDCC)
        assertEquals(fill, style.resolveFill(fill))
        assertEquals(text, style.resolveText(text))
        assertNull("borders are off by default", style.resolveBorder(text))
        assertNull("haze is off by default", style.resolveHaze(fill))
        assertEquals(1f, style.backgroundImageOpacity, 0f)
    }

    @Test
    fun `a null colour inherits the palette but still takes the opacity`() {
        val style = KeyStyle(fillColor = null, fillOpacity = 0.5f)
        val resolved = style.resolveFill(Color(0xFF112233))
        assertEquals(Color(0xFF112233).red, resolved.red, 0.001f)
        // Tolerance is one 8-bit step, not float noise: alpha is stored as a byte, so 0.5
        // round-trips as 128/255 = 0.50196. Asserting tighter than the storage precision
        // fails on a correct value.
        assertEquals(0.5f, resolved.alpha, 1f / 255f)
    }

    @Test
    fun `an explicit colour overrides the palette`() {
        val style = KeyStyle(fillColor = Color.Red)
        assertEquals(Color.Red, style.resolveFill(Color.Blue))
    }

    @Test
    fun `borders resolve only when enabled`() {
        val off = KeyStyle(borderEnabled = false, borderColor = Color.Red)
        assertNull("a colour without the toggle must not draw", off.resolveBorder(Color.White))

        val on = KeyStyle(borderEnabled = true, borderColor = Color.Red)
        assertEquals(Color.Red, on.resolveBorder(Color.White))
    }

    @Test
    fun `haze is off at zero opacity regardless of colour`() {
        val off = KeyStyle(hazeColor = Color.Magenta, hazeOpacity = 0f)
        assertNull(off.resolveHaze(Color.Black))

        val on = KeyStyle(hazeColor = Color.Magenta, hazeOpacity = 0.4f)
        assertNotNull(on.resolveHaze(Color.Black))
        assertEquals(0.4f, on.resolveHaze(Color.Black)!!.alpha, 0.001f)
    }

    @Test
    fun `opacity outside the valid range is clamped, not wrapped`() {
        assertEquals(1f, KeyStyle(fillOpacity = 4f).resolveFill(Color.White).alpha, 0.001f)
        assertEquals(0f, KeyStyle(fillOpacity = -2f).resolveFill(Color.White).alpha, 0.001f)
    }

    @Test
    fun `a theme saved before key styling existed loads as the default`() {
        // The compatibility case. Every preset in assets/themes was written without this
        // object, and must not turn into a blank or transparent keyboard on upgrade.
        assertEquals(KeyStyle.Default, KeyStyle.fromJson(null))
    }

    @Test
    fun `a style survives a round trip through json`() {
        val original =
            KeyStyle(
                fillColor = Color(0xFF204060),
                fillOpacity = 0.75f,
                textColor = Color(0xFFFFEECC),
                textOpacity = 0.9f,
                borderEnabled = true,
                borderColor = Color(0xFF889900),
                borderOpacity = 0.6f,
                borderWidth = 2.5f.dp,
                hazeColor = Color(0xFF00FFAA),
                hazeOpacity = 0.3f,
                hazeRadius = 12f.dp,
                backgroundImageOpacity = 0.45f,
            )
        val restored = KeyStyle.fromJson(original.toJson())
        assertEquals(original, restored)
    }

    @Test
    fun `a whole theme round trips with its key style attached`() {
        val theme =
            KeyboardTheme(
                id = "custom_test",
                name = "Test",
                isLight = false,
                colors = darkStickyKeysColors(),
                typeScale = TypeScale.MEDIUM,
                keyStyle = KeyStyle(borderEnabled = true, hazeOpacity = 0.2f),
            )
        val restored = KeyboardTheme.fromJson(theme.toJson().toString())
        assertTrue(restored.keyStyle.borderEnabled)
        assertEquals(0.2f, restored.keyStyle.hazeOpacity, 0.001f)
    }
}
