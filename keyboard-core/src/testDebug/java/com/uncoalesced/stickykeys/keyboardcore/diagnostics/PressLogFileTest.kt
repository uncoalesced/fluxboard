// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What actually lands on disk: a header once, then the buffered rows in order, and nothing
 * left behind after clear(). Writes happen on a background scope, so each check waits for
 * the file to settle rather than assuming it already has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PressLogFileTest {
    private lateinit var log: PressLog

    @Before
    fun setup() {
        log = PressLog(ApplicationProvider.getApplicationContext())
        log.file.delete()
    }

    private fun linesAfterWrite(expected: Int): List<String> {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (log.file.exists()) {
                val lines = log.file.readLines()
                if (lines.size >= expected) return lines
            }
            Thread.sleep(20)
        }
        return if (log.file.exists()) log.file.readLines() else emptyList()
    }

    @Test
    fun `flush writes the header and the rows in order`() {
        log.onTouchDown(uptimeMillis = 10, pointersDown = 1)
        log.onPressResolved(KeyKind.SPACE, PressOutcome.COMMITTED, 40, uptimeMillis = 50)
        log.flush()

        assertEquals(
            listOf(PressLog.HEADER, "10,down,,,,1", "50,press,SPACE,COMMITTED,40,"),
            linesAfterWrite(3),
        )
    }

    @Test
    fun `a second flush appends without repeating the header`() {
        log.onTouchDown(10, 1)
        log.flush()
        linesAfterWrite(2)
        log.onTouchDown(20, 2)
        log.flush()

        assertEquals(listOf(PressLog.HEADER, "10,down,,,,1", "20,down,,,,2"), linesAfterWrite(3))
    }

    @Test
    fun `a full buffer flushes on its own`() {
        repeat(64) { log.onTouchDown(it.toLong(), 1) }

        assertEquals(65, linesAfterWrite(65).size)
    }

    @Test
    fun `clear removes the file`() {
        log.onTouchDown(10, 1)
        log.flush()
        linesAfterWrite(2)
        log.clear()

        val deadline = System.currentTimeMillis() + 5_000
        while (log.file.exists() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertFalse(log.file.exists())
    }
}
