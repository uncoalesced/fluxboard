// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.theme.darkStickyKeysColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The three shift latch positions used to render pixel-identically, leaving the state
 * description added in Tier 2 as the only channel carrying the difference -- nothing at all
 * for a sighted user.
 */
class KeyAccentTest {
    @Test
    fun `shift reports a distinct accent for each of its three positions`() {
        val accents =
            listOf(
                KeyboardMode.LETTERS_LOWER,
                KeyboardMode.LETTERS_UPPER,
                KeyboardMode.LETTERS_CAPS_LOCK,
            ).map { accentForKey("SHIFT", it) }

        assertEquals(
            listOf(KeyAccent.None, KeyAccent.Active, KeyAccent.Locked),
            accents,
        )
        assertEquals("all three positions must be visually distinct", 3, accents.distinct().size)
    }

    @Test
    fun `symbols shift reports an accent only while engaged`() {
        assertEquals(KeyAccent.None, accentForKey("SYMBOLS_SHIFT", KeyboardMode.SYMBOLS))
        assertEquals(
            KeyAccent.Active,
            accentForKey("SYMBOLS_SHIFT", KeyboardMode.SYMBOLS_SHIFTED),
        )
    }

    @Test
    fun `keys that do not latch never take an accent`() {
        listOf("q", "SPACE", "DEL", "ENTER", "SYMBOLS", "ABC", "STICKERS").forEach { key ->
            KeyboardMode.entries.forEach { mode ->
                assertEquals(
                    "'$key' should not be accented in $mode",
                    KeyAccent.None,
                    accentForKey(key, mode),
                )
            }
        }
    }
}

/**
 * The accent has to reach the colours a key is actually painted with, not just the enum.
 *
 * Asserted through [resolveKeyColors], the single function the view uses to pick them.
 * Sampling rendered pixels would be stronger, but `captureToImage` needs a real window
 * surface and times out under Robolectric -- that check belongs on a device.
 */
class KeyAccentColorTest {
    private val palette = darkStickyKeysColors()

    private fun shiftColors(mode: KeyboardMode) =
        resolveKeyColors(
            keyOutput = "SHIFT",
            weight = 1.5f,
            mode = mode,
            palette = palette,
            hasBackgroundImage = false,
        )

    @Test
    fun `the three latch states resolve to three different key colours`() {
        val off = shiftColors(KeyboardMode.LETTERS_LOWER).background
        val on = shiftColors(KeyboardMode.LETTERS_UPPER).background
        val locked = shiftColors(KeyboardMode.LETTERS_CAPS_LOCK).background

        assertNotEquals("shift-on must not look like shift-off", off, on)
        assertNotEquals("caps lock must not look like shift-on", on, locked)
        assertNotEquals("caps lock must not look like shift-off", off, locked)
        assertEquals(
            "all three latch colours must be distinct, got $off / $on / $locked",
            3,
            setOf(off, on, locked).size,
        )
    }

    @Test
    fun `the accent reuses existing theme tokens rather than a new colour`() {
        assertEquals(palette.primary, shiftColors(KeyboardMode.LETTERS_UPPER).background)
        assertEquals(
            palette.primaryVariant,
            shiftColors(KeyboardMode.LETTERS_CAPS_LOCK).background,
        )
        assertEquals(palette.surfaceVariant, shiftColors(KeyboardMode.LETTERS_LOWER).background)
    }

    @Test
    fun `an accented key switches to the on-primary foreground for contrast`() {
        assertEquals(palette.onPrimary, shiftColors(KeyboardMode.LETTERS_UPPER).foreground)
        assertEquals(palette.onPrimary, shiftColors(KeyboardMode.LETTERS_CAPS_LOCK).foreground)
        assertEquals(
            palette.onSurfaceVariant,
            shiftColors(KeyboardMode.LETTERS_LOWER).foreground,
        )
    }

    @Test
    fun `ordinary keys look the same whatever the latch state is`() {
        val colors =
            KeyboardMode.entries.map {
                resolveKeyColors("q", 1f, it, palette, hasBackgroundImage = false)
            }
        assertEquals("a letter key must not change colour with shift", 1, colors.distinct().size)
        assertEquals(palette.surface, colors.first().background)
    }
}
