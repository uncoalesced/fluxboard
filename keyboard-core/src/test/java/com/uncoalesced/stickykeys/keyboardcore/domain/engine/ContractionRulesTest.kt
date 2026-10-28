// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The per-word contraction decisions in [contractionFor], and the edit cost personal words use. */
class ContractionRulesTest {
    @Test
    fun `the unambiguous bare forms always become contractions`() {
        assertEquals("I'm", contractionFor("im", previousWord = "and"))
        assertEquals("can't", contractionFor("cant", previousWord = "i"))
        assertEquals("won't", contractionFor("wont", previousWord = "it"))
    }

    @Test
    fun `ill becomes I'll only where the adjective cannot follow`() {
        assertEquals("I'll", contractionFor("ill", previousWord = null))
        assertEquals("I'll", contractionFor("Ill", previousWord = "late"))
        assertEquals("I'll", contractionFor("ill", previousWord = "and"))
        assertEquals("I'll", contractionFor("ill", previousWord = "OK"))
        assertNull(contractionFor("ill", previousWord = "am"))
        assertNull(contractionFor("ill", previousWord = "felt"))
        assertNull(contractionFor("ill", previousWord = "very"))
    }

    @Test
    fun `ill after a comma or other clause break becomes I'll`() {
        // "Yesterday, ill ..." -- the previous word alone would leave it as the adjective.
        assertEquals("I'll", contractionFor("ill", previousWord = "yesterday", startsClause = true))
        assertNull(contractionFor("ill", previousWord = "yesterday", startsClause = false))
    }

    @Test
    fun `homographs usually meant as typed are never touched`() {
        for (word in listOf("well", "were", "shell", "its", "hell", "wed", "shed", "lets", "id")) {
            assertNull(word, contractionFor(word, previousWord = null))
        }
    }

    @Test
    fun `weighted edit cost matches the trie walk's scale`() {
        assertEquals(0, weightedEditCost("zorbly", "zorbly"))
        // One adjacent key: v sits beside c.
        assertEquals(2, weightedEditCost("vall", "call"))
        // One transposition, not two substitutions.
        assertEquals(3, weightedEditCost("teh", "the"))
        // One letter dropped.
        assertEquals(3, weightedEditCost("helo", "hello"))
        // i and y are not neighbours, so this is a full substitution.
        assertEquals(4, weightedEditCost("zorbli", "zorbly"))
    }
}
