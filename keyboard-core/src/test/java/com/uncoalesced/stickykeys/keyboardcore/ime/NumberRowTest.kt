// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The number row's hold strips, and the strip machinery underneath them.
 *
 * The strip used to lead with the digit's shifted symbol on the reasoning that the corner hint
 * promised it. That cell is gone: the same character is one shift-tap away, where the whole row
 * becomes real shifted-symbol keys, so it was a second route to something already reachable and
 * it pushed the superscripts and fractions along the row. The corner hint stays as information.
 *
 * Two consequences are load-bearing and tested here. A release without any drag now commits the
 * superscript rather than the shifted symbol, and there is no longer a length ceiling -- which
 * means the strip has to size its own cells instead of relying on a test to keep it short.
 */
class NumberRowTest {
    private val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    private fun stripFor(key: KeyDefinition): List<String> =
        (
            longPressFor(key.output, key.hint, key.alternates, key.alternatesDefaultIndex)
                as LongPress.Alternates
        ).options

    private fun keyFor(digit: String): KeyDefinition =
        KeyboardLayouts.numberRow.first { it.output == digit }

    // --- the corner hint stays, as information only ---------------------------------------

    @Test
    fun `every digit still shows the symbol shift produces from it`() {
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
    fun `the shifted row still types exactly what the unshifted hints advertise`() {
        // The hint's promise moved rather than disappeared: it now describes what arming shift
        // will do, and this is what keeps that promise true.
        assertEquals(
            KeyboardLayouts.numberRow.map { it.hint },
            KeyboardLayouts.shiftedNumberRow.map { it.output },
        )
    }

    @Test
    fun `the shifted symbol is no longer in any hold strip`() {
        // The removal, stated directly. Leaving one behind would put the strip back to leading
        // with a character that is already one tap away.
        //
        // Digit 4 is excluded by decision, not by accident, and the exception is narrow enough
        // to name here rather than weaken the assertion into uselessness: its strip is the
        // currency set, and `$` sits in it as one of the currencies rather than as a duplicate
        // of the shift route. The rule exists to stop a redundant cell crowding out the strip's
        // real content; on 4 the currencies *are* the content and nothing was displaced by it.
        KeyboardLayouts.numberRow
            .filterNot { it.output == "4" }
            .forEach { key ->
                assertFalse(
                    "${key.output} still offers its shifted symbol ${key.hint} on hold",
                    key.hint in stripFor(key),
                )
            }
    }

    // --- digit 4 carries the currencies ---------------------------------------------------

    @Test
    fun `digit 4 offers the currencies and opens on the dollar`() {
        // The number row is where a currency is actually reached from mid-sentence: the symbols
        // page has the same set on its own `$` key, but getting there is a page switch. A
        // straight hold commits `$`, which is both the corner hint's promise and what a
        // TalkBack long-press produces -- those two must not disagree.
        val four = keyFor("4")
        val held =
            longPressFor(four.output, four.hint, four.alternates, four.alternatesDefaultIndex)
                as LongPress.Alternates

        assertEquals(listOf("€", "¥", "$", "¢", "₹"), held.options)
        assertEquals("$", held.options[held.defaultIndex])
    }

    @Test
    fun `both currency keys offer the same set`() {
        // Two keys, one list. A user who found a currency under one and not the other would
        // reasonably conclude the keyboard had lost it.
        val symbolsPageDollar =
            KeyboardLayouts.symbolsPrimaryRows
                .flatten()
                .first { it.output == "$" }

        assertEquals(symbolsPageDollar.alternates, keyFor("4").alternates)
        assertEquals(
            symbolsPageDollar.alternates!![symbolsPageDollar.alternatesDefaultIndex],
            keyFor("4").alternates!![keyFor("4").alternatesDefaultIndex],
        )
    }

    @Test
    fun `digit 4 no longer carries a superscript or a fraction`() {
        // The trade that made room for the currencies, pinned so it is not quietly undone by
        // someone restoring the table to look like its neighbours.
        val strip = stripFor(keyFor("4"))
        assertFalse("4 should no longer offer its superscript", "⁴" in strip)
        assertFalse("4 should no longer offer its fraction", "⅘" in strip)
    }

    // --- what a hold now commits ----------------------------------------------------------

    @Test
    fun `holding a digit and releasing without moving commits its superscript`() {
        // 4 is absent: it gave up its superscript for the currency set and commits `$` instead.
        // Asserted in its own test above rather than folded in here with a special case.
        val superscripts =
            mapOf(
                "0" to "⁰",
                "1" to "¹",
                "2" to "²",
                "3" to "³",
                "5" to "⁵",
                "6" to "⁶",
                "7" to "⁷",
                "8" to "⁸",
                "9" to "⁹",
            )
        superscripts.keys.forEach { digit ->
            val key = keyFor(digit)
            val held =
                longPressFor(key.output, key.hint, key.alternates, key.alternatesDefaultIndex)
                    as LongPress.Alternates
            assertEquals(
                "$digit should commit its superscript on a straight hold",
                superscripts[digit],
                held.options[held.defaultIndex],
            )
        }
    }

    @Test
    fun `the confirmed strips are exactly as specified`() {
        // 1 and 5 are Joel's confirmed spec, quoted rather than derived. Note that 1 is a
        // curated subset of the nine numerator-1 fractions, so it cannot be generated -- which
        // is the reason the remaining digits had to be proposed rather than inferred from it.
        assertEquals(listOf("¹", "⅛", "¼", "⅓", "½", "ⁱ"), stripFor(keyFor("1")))
        assertEquals(listOf("⁵", "⅝", "⅚", "ⁿ"), stripFor(keyFor("5")))
    }

    // --- the cap is gone, and the strip has to cope on its own ----------------------------

    @Test
    fun `a strip longer than the old six-entry ceiling sizes itself to fit`() {
        // The ceiling used to be a test assertion, which is not a mechanism. Removing it
        // without this leaves a long strip clipped at the screen edge, where its last cells
        // are drawn nowhere and -- because selection is computed from the same cell width --
        // cannot be selected either.
        val twelve = 12
        val screenPx = 360f * 3f // a 360dp phone at 3x
        val preferred = 40f * 3f

        val width = alternateCellWidthPx(twelve, screenPx, preferred)

        assertTrue("cells must shrink below the preferred width", width < preferred)
        assertTrue("the whole strip must fit on screen", width * twelve <= screenPx + 0.01f)
        assertTrue("a cell must stay wide enough to hit", width > 0f)
    }

    @Test
    fun `a short strip keeps the full preferred cell width`() {
        // The shrink must only apply when it is needed, or every ordinary two-entry strip
        // would be drawn at a third of its intended size on a wide screen.
        val preferred = 40f * 3f
        assertEquals(preferred, alternateCellWidthPx(3, 1080f, preferred), 0.01f)
    }

    @Test
    fun `every cell of a long strip is selectable across its full width`() {
        // The functional half: walk a finger across a twelve-cell strip and confirm each cell
        // is reachable in turn. This is what proves removing the ceiling is safe rather than
        // merely quiet.
        val options = (1..12).map { it.toString() }
        val screenPx = 360f * 3f
        val cell = alternateCellWidthPx(options.size, screenPx, 40f * 3f)
        val state = KeyAlternatesState()
        state.show(
            options,
            androidx.compose.ui.geometry
                .Rect(0f, 0f, cell, 100f),
            cell,
        )

        options.indices.forEach { index ->
            // Sample the middle of each cell, which is where a finger tracking the strip sits.
            state.moveTo(index * cell + cell / 2f)
            assertEquals("cell $index should be selectable", index, state.selectedIndex)
        }
    }

    @Test
    fun `selection is clamped to the strip rather than running past its ends`() {
        val options = listOf("a", "b", "c")
        val state = KeyAlternatesState()
        state.show(
            options,
            androidx.compose.ui.geometry
                .Rect(0f, 0f, 40f, 100f),
            40f,
        )

        state.moveTo(-500f)
        assertEquals(0, state.selectedIndex)
        state.moveTo(5000f)
        assertEquals(options.lastIndex, state.selectedIndex)
    }

    // --- ordering -------------------------------------------------------------------------

    @Test
    fun `fractions are ordered ascending by value within each digit`() {
        assertEquals(listOf("⅛", "¼", "⅓", "½"), stripFor(keyFor("1")).drop(1).dropLast(1))
        assertEquals(listOf("⅝", "⅚"), stripFor(keyFor("5")).drop(1).dropLast(1))
    }

    // --- the PIN pad must never inherit any of this ---------------------------------------

    @Test
    fun `the PIN pad offers no hold behaviour on its digits`() {
        // These keys type the same characters as the number row. Under the old output-keyed
        // table they would have inherited the fraction strips, which is absurd on a passcode
        // pad and is the same collision that gave the symbols page a strip it never wanted.
        KeyboardLayouts.pinRows
            .flatten()
            .filter { it.output.singleOrNull()?.isDigit() == true }
            .forEach { key ->
                assertEquals(
                    "${key.output} on the PIN pad should have no alternates",
                    LongPress.None,
                    longPressFor(key.output, key.hint, key.alternates),
                )
            }
    }
}
