// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The spoken-label mapping, checked against every key of every shipped layout rather than
 * against a hand-picked list -- the original defect was a single key whose label happened
 * to be whitespace, which no per-key spot check would have caught.
 */
class KeyAccessibilityTest {
    private val allShippedKeys: List<String> =
        (
            KeyboardLayouts.qwertyLettersLower +
                KeyboardLayouts.qwertyLettersUpper +
                KeyboardLayouts.symbolsPrimary +
                KeyboardLayouts.symbolsShifted
        ).flatten()
            .distinct()

    @Test
    fun `no key in any shipped layout has a blank spoken label`() {
        val blank = allShippedKeys.filter { accessibleKeyLabel(it).isBlank() }
        assertTrue("Keys with unusable spoken labels: $blank", blank.isEmpty())
    }

    @Test
    fun `the default layout has no blank spoken labels either`() {
        val blank =
            LayoutManager
                .buildDefaultLayout()
                .rows
                .flatten()
                .map { it.output }
                .filter { accessibleKeyLabel(it).isBlank() }
        assertTrue("Keys with unusable spoken labels: $blank", blank.isEmpty())
    }

    @Test
    fun `space announces as a word, not as whitespace`() {
        // The regression: getDisplayLabel("SPACE") is " ", so the widest key on the
        // keyboard had nothing for a screen reader to find or announce.
        assertEquals("Space", accessibleKeyLabel("SPACE"))
    }

    @Test
    fun `glyph keys announce as words rather than by symbol`() {
        assertEquals("Delete", accessibleKeyLabel("DEL"))
        assertEquals("Enter", accessibleKeyLabel("ENTER"))
        assertEquals("Shift", accessibleKeyLabel("SHIFT"))
        assertEquals("Stickers", accessibleKeyLabel("STICKERS"))
        assertEquals("Symbols", accessibleKeyLabel("SYMBOLS"))
        assertEquals("More symbols", accessibleKeyLabel("SYMBOLS_SHIFT"))
        assertEquals("Letters", accessibleKeyLabel("ABC"))
        assertEquals("Tab", accessibleKeyLabel("\t"))
    }

    @Test
    fun `no spoken label leaks a raw glyph a reader cannot pronounce`() {
        val glyphs = setOf("⇧", "⌫", "⏎", ":)", "?123", "=\\<")
        allShippedKeys.forEach { key ->
            assertTrue(
                "Key '$key' still announces as a glyph",
                accessibleKeyLabel(key) !in glyphs,
            )
        }
    }

    @Test
    fun `ordinary character keys announce as themselves`() {
        assertEquals("q", accessibleKeyLabel("q"))
        assertEquals("Q", accessibleKeyLabel("Q"))
        assertEquals("7", accessibleKeyLabel("7"))
        assertEquals("@", accessibleKeyLabel("@"))
    }

    @Test
    fun `shift reports all three of its latch positions distinctly`() {
        // Shift, caps lock and off are rendered identically today, so state description is
        // the only channel carrying which one is active.
        val states =
            listOf(
                KeyboardMode.LETTERS_LOWER,
                KeyboardMode.LETTERS_UPPER,
                KeyboardMode.LETTERS_CAPS_LOCK,
            ).map { accessibleKeyState("SHIFT", it) }

        assertEquals(listOf("Off", "Shift on", "Caps lock on"), states)
        assertEquals(3, states.distinct().size)
    }

    @Test
    fun `symbols shift reports its two positions distinctly`() {
        assertEquals("Off", accessibleKeyState("SYMBOLS_SHIFT", KeyboardMode.SYMBOLS))
        assertEquals(
            "Showing more symbols",
            accessibleKeyState("SYMBOLS_SHIFT", KeyboardMode.SYMBOLS_SHIFTED),
        )
    }

    @Test
    fun `keys without a latching state report none`() {
        listOf("q", "SPACE", "DEL", "ENTER", "STICKERS", "CLIPBOARD").forEach { key ->
            assertNull(
                "'$key' should carry no state",
                accessibleKeyState(key, KeyboardMode.LETTERS_LOWER),
            )
        }
        assertNotNull(accessibleKeyState("SHIFT", KeyboardMode.LETTERS_LOWER))
    }
}
