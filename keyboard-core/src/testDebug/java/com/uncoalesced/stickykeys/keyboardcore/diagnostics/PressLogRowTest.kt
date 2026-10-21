// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The press log's rows line up with its header, so the file opens as a table.
 *
 * In testDebug rather than test: [PressLog] exists only in the debug source set, and a
 * reference to it from the shared test set would not compile for the release unit tests.
 */
class PressLogRowTest {
    private val columns = PressLog.HEADER.split(",").size

    @Test
    fun `a down row has one cell per header column`() {
        val row = PressLog.downRow(uptimeMillis = 1_000, pointersDown = 2)
        assertEquals("1000,down,,,,2", row)
        assertEquals(columns, row.split(",").size)
    }

    @Test
    fun `a press row has one cell per header column`() {
        val row = PressLog.pressRow(1_000, KeyKind.LETTER, PressOutcome.NOTHING, 84)
        assertEquals("1000,press,LETTER,NOTHING,84,", row)
        assertEquals(columns, row.split(",").size)
    }
}
