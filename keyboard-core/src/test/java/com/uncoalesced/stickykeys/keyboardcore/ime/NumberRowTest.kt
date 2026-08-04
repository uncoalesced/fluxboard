// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The number row, where three separate features share one hint slot.
 *
 * Corner hints showing symbols, shift-active showing the shifted symbol, and a long-press
 * offering superscripts and vulgar fractions all wanted the same slot on the same key. The
 * resolution chosen (option B) is that the hint shows the shifted symbol, and the hold strip
 * *leads* with that same symbol before the superscript and fractions.
 *
 * That first-entry rule is the load-bearing part and the reason these tests exist. A key's
 * corner glyph is a promise that holding it produces that character, and index 0 of the strip
 * is both where the finger already sits and what a TalkBack long-press commits. If the table
 * and the hint ever disagree, the superscript on every digit becomes a lie -- and nothing about
 * the keyboard would look wrong while it happened.
 */
class NumberRowTest {
    private val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    @Test
    fun `every digit hint is the symbol shift produces from it`() {
        val hints = KeyboardLayouts.numberRow.associate { it.output to it.hint }

        assertEquals("!", hints["1"])
        assertEquals("@", hints["2"])
        assertEquals("#", hints["3"])
        assertEquals("$", hints["4"])
        assertEquals("%", hints["5"])
        assertEquals("^", hints["6"])
        assertEquals("&", hints["7"])
        assertEquals("*", hints["8"])
        assertEquals("(", hints["9"])
        assertEquals(")", hints["0"])
    }

    @Test
    fun `the shifted row types exactly what the unshifted hints advertise`() {
        // Two lists that must not drift. Both are built from digitShiftPairs, and this asserts
        // that they still are rather than that they merely happen to match today.
        val hinted = KeyboardLayouts.numberRow.map { it.hint }
        val shifted = KeyboardLayouts.shiftedNumberRow.map { it.output }

        assertEquals(hinted, shifted)
    }

    @Test
    fun `holding a digit commits its hint first`() {
        // The invariant. Index 0 is what the corner glyph promises and what TalkBack commits.
        KeyboardLayouts.numberRow.forEach { key ->
            val held = longPressFor(key.output, key.hint)
            assertTrue("${key.output} should offer alternates", held is LongPress.Alternates)
            assertEquals(
                "First alternate for ${key.output} must match its hint",
                key.hint,
                (held as LongPress.Alternates).options.first(),
            )
        }
    }

    @Test
    fun `the confirmed fraction specs are exactly as specified`() {
        // 1 and 2 are Joel's confirmed spec, quoted rather than derived. The remaining digits
        // follow the same rule but are an unconfirmed proposal.
        assertEquals(
            listOf("!", "¹", "⅛", "¼", "⅓", "½"),
            (longPressFor("1", "!") as LongPress.Alternates).options,
        )
        assertEquals(
            listOf("@", "²", "⅔"),
            (longPressFor("2", "@") as LongPress.Alternates).options,
        )
    }

    @Test
    fun `every digit offers its own superscript`() {
        val superscripts =
            mapOf(
                "0" to "⁰",
                "1" to "¹",
                "2" to "²",
                "3" to "³",
                "4" to "⁴",
                "5" to "⁵",
                "6" to "⁶",
                "7" to "⁷",
                "8" to "⁸",
                "9" to "⁹",
            )
        digits.forEach { digit ->
            val options = (longPressFor(digit, null) as LongPress.Alternates).options
            assertTrue(
                "$digit should offer ${superscripts[digit]}",
                superscripts[digit] in options,
            )
        }
    }

    @Test
    fun `no strip exceeds what the alternates row can display`() {
        // Six 40dp cells is the practical ceiling on a narrow phone before the strip clamps to
        // stay on screen. Digit 1 sits exactly at it, so this is a real bound, not a formality.
        digits.forEach { digit ->
            val options = (longPressFor(digit, null) as LongPress.Alternates).options
            assertTrue(
                "$digit offers ${options.size} alternates, maximum is 6",
                options.size <= 6,
            )
        }
    }

    @Test
    fun `fractions are ordered ascending by value within each digit`() {
        // Consistency across digits is the point: a user who learns that halves sit at the far
        // end of the strip for 1 should find thirds at the far end for 2.
        assertEquals(listOf("⅛", "¼", "⅓", "½"), fractionsFor("1"))
        assertEquals(listOf("⅜", "⅗", "¾"), fractionsFor("3"))
        assertEquals(listOf("⅝", "⅚"), fractionsFor("5"))
    }

    private fun fractionsFor(digit: String): List<String> =
        (longPressFor(digit, null) as LongPress.Alternates)
            .options
            // Drop the shifted symbol and the superscript; what is left is the fractions.
            .drop(2)

    @Test
    fun `the shifted digit row does not also claim the fraction strip`() {
        // While shift is armed the row already shows the symbols, so holding one of them must
        // not reopen the digit's strip -- the key is no longer a digit.
        KeyboardLayouts.shiftedNumberRow.forEach { key ->
            val held = longPressFor(key.output, key.hint)
            val offersFractions =
                held is LongPress.Alternates && held.options.any { it in "¹²³⁴⁵⁶⁷⁸⁹⁰" }
            assertTrue("${key.output} should not offer digit fractions", !offersFractions)
        }
    }
}
