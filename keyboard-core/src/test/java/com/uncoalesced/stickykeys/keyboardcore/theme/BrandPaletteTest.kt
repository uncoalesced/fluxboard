// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.pow

/**
 * Pins the contrast rules of the Watermelon palette.
 *
 * The rule most likely to be broken by a later edit is that the accent fill is never the text
 * colour: it measures 3.73 on the dark ground and 4.03 on Porcelain, both under 4.5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrandPaletteTest {
    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun ratio(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `the accent fill is below text contrast, which is why it is not the text colour`() {
        assertEquals(3.73, ratio(Watermelon, DarkBackground), 0.01)
        assertEquals(4.03, ratio(Watermelon, Porcelain), 0.01)
        assertNotEquals(Watermelon, darkStickyKeysColors().accentText)
        assertNotEquals(Watermelon, lightStickyKeysColors().accentText)
    }

    @Test
    fun `text pairs in both palettes reach 4_5`() {
        listOf(darkStickyKeysColors(), lightStickyKeysColors()).forEach { p ->
            assertTrue(ratio(p.accentText, p.background) >= 4.5)
            assertTrue(ratio(p.accentText, p.surface) >= 4.5)
            assertTrue(ratio(p.onBackground, p.background) >= 4.5)
            assertTrue(ratio(p.onSurface, p.surface) >= 4.5)
            assertTrue(ratio(p.onSurfaceVariant, p.surface) >= 4.5)
            assertTrue(ratio(p.onPrimary, p.primary) >= 4.5)
            assertTrue(ratio(p.onAccentText, p.accentText) >= 4.5)
        }
    }

    @Test
    fun `a theme saved before the accent split loads with a single accent`() {
        val legacy =
            """
            {"id":"t","name":"t","isLight":false,"colors":{
             "primary":"#FF7CAE7A","primaryVariant":"#FF839073","secondary":"#FF6E6362",
             "background":"#FF000000","surface":"#FF4E4A59","surfaceVariant":"#FF394053",
             "error":"#FFDC2626","onPrimary":"#FF000000","onSecondary":"#FFE9EDDE",
             "onBackground":"#FFE9EDDE","onSurface":"#FFE9EDDE","onSurfaceVariant":"#FFE9EDDE",
             "onError":"#FFFFFFFF"}}
            """.trimIndent()
        val colors = KeyboardTheme.fromJson(legacy).colors
        assertEquals(colors.primary, colors.accentText)
        assertEquals(colors.onPrimary, colors.onAccentText)
        assertEquals(colors.secondary, colors.outline)
    }
}
