// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomRevertHistoryTest {
    @Test
    fun `records and recognizes reverted word`() {
        val history = BloomRevertHistory(maxEntries = 3)
        assertFalse(history.isReverted("teh"))

        history.recordRevert("teh")
        assertTrue(history.isReverted("teh"))
        assertTrue(history.isReverted("TEH"))
        assertTrue(history.isReverted("  Teh  "))
    }

    @Test
    fun `evicts oldest entry when capacity is exceeded`() {
        val history = BloomRevertHistory(maxEntries = 2)
        history.recordRevert("word1")
        history.recordRevert("word2")
        assertTrue(history.isReverted("word1"))
        assertTrue(history.isReverted("word2"))

        history.recordRevert("word3")
        assertFalse(history.isReverted("word1")) // Evicted
        assertTrue(history.isReverted("word2"))
        assertTrue(history.isReverted("word3"))
    }

    @Test
    fun `clear empties all recorded words`() {
        val history = BloomRevertHistory(maxEntries = 3)
        history.recordRevert("word1")
        history.recordRevert("word2")
        assertTrue(history.isReverted("word1"))

        history.clear()
        assertFalse(history.isReverted("word1"))
        assertFalse(history.isReverted("word2"))
    }

    @Test
    fun `blank inputs are ignored`() {
        val history = BloomRevertHistory(maxEntries = 3)
        history.recordRevert("")
        history.recordRevert("   ")
        assertFalse(history.isReverted(""))
    }
}
