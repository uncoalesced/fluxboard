// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap 4R.1: no part of a key row may belong to no key.
 *
 * Measured on a Galaxy S8 before the fix: a tap in the 18dp left of `a`, the 15dp right of `l`,
 * or the outer few dp of any row typed nothing. Both holes were space the layout drew as empty
 * -- the home row's end spacers and the panel margin -- and both are now owned by the key next
 * to them while it is still drawn where it was.
 */
class RowCellsTest {
    private fun k(
        out: String,
        weight: Float = 1f,
    ) = KeyDefinition(id = out, output = out, weight = weight)

    private fun spacer(name: String) =
        KeyDefinition(id = "spacer_$name", output = KeyboardLayouts.SPACER, weight = 0.5f)

    private val homeRow =
        listOf(spacer("l")) + "asdfghjkl".map { k(it.toString()) } + listOf(spacer("r"))

    @Test
    fun `end spacers are folded into the keys beside them`() {
        val cells = rowCells(homeRow)

        assertTrue(cells.none { it.key.output == KeyboardLayouts.SPACER })
        assertEquals("a", cells.first().key.output)
        assertEquals("l", cells.last().key.output)
        assertEquals(1.5f, cells.first().weight, 1e-6f)
        assertEquals(1.5f, cells.last().weight, 1e-6f)
    }

    @Test
    fun `an absorbing key is drawn at its old width, inset on the spacer side only`() {
        val cells = rowCells(homeRow)

        // Half a key of a key-and-a-half cell is a third, on the outer side.
        assertEquals(1f / 3f, cells.first().inset.startFraction, 1e-6f)
        assertEquals(0f, cells.first().inset.endFraction, 1e-6f)
        assertEquals(0f, cells.last().inset.startFraction, 1e-6f)
        assertEquals(1f / 3f, cells.last().inset.endFraction, 1e-6f)
        assertEquals(KeyDrawInset.None, cells[4].inset)
    }

    @Test
    fun `the row's total share is unchanged, so no other key moves`() {
        assertEquals(
            homeRow.sumOf { it.weight.toDouble() },
            rowCells(homeRow).sumOf { it.weight.toDouble() },
            1e-6,
        )
    }

    @Test
    fun `the panel margin goes to the outermost key of every row`() {
        val cells = rowCells("qwertyuiop".map { k(it.toString()) }, edgeInset = 4.dp)

        assertEquals(4.dp, cells.first().inset.startDp)
        assertEquals(0.dp, cells.first().inset.endDp)
        assertEquals(4.dp, cells.last().inset.endDp)
        assertEquals(0.dp, cells.last().inset.startDp)
        assertTrue(cells.subList(1, cells.lastIndex).all { it.inset == KeyDrawInset.None })
    }

    @Test
    fun `a row with no spacers and no margin is passed through untouched`() {
        val row = listOf(k("SHIFT", 1.5f), k("z"), k("DEL", 1.5f))
        val cells = rowCells(row)

        assertEquals(row, cells.map { it.key })
        assertEquals(row.map { it.weight }, cells.map { it.weight })
        assertTrue(cells.all { it.inset == KeyDrawInset.None })
    }

    @Test
    fun `an interior spacer is not absorbed`() {
        val row = listOf(k("a"), spacer("mid"), k("b"))

        assertEquals(3, rowCells(row).size)
        assertEquals(KeyboardLayouts.SPACER, rowCells(row)[1].key.output)
    }

    // --- roadmap 4R.2: the number row and automatic sentence-start shift ------------------

    @Test
    fun `automatic sentence-start shift leaves the digits as digits`() {
        assertFalse(KeyboardLayouts.showsShiftedDigits(KeyboardMode.LETTERS_UPPER, true))
        assertEquals(
            KeyboardLayouts.numberRow,
            KeyboardLayouts.numberRowFor(KeyboardMode.LETTERS_UPPER, autoCapitalizeArmed = true),
        )
    }

    @Test
    fun `shift the user armed still shows the shifted symbols`() {
        assertTrue(KeyboardLayouts.showsShiftedDigits(KeyboardMode.LETTERS_UPPER, false))
        assertEquals(
            KeyboardLayouts.shiftedNumberRow,
            KeyboardLayouts.numberRowFor(KeyboardMode.LETTERS_UPPER, autoCapitalizeArmed = false),
        )
    }

    @Test
    fun `caps lock is always deliberate and lower case never shifts`() {
        assertTrue(KeyboardLayouts.showsShiftedDigits(KeyboardMode.LETTERS_CAPS_LOCK, true))
        assertTrue(KeyboardLayouts.showsShiftedDigits(KeyboardMode.LETTERS_CAPS_LOCK, false))
        assertFalse(KeyboardLayouts.showsShiftedDigits(KeyboardMode.LETTERS_LOWER, true))
        assertFalse(KeyboardLayouts.showsShiftedDigits(KeyboardMode.LETTERS_LOWER, false))
    }
}
