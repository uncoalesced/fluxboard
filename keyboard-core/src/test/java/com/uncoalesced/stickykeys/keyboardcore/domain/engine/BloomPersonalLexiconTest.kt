// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BloomPersonalLexiconTest {
    private lateinit var lexicon: BloomPersonalLexicon

    @Before
    fun setUp() {
        lexicon = BloomPersonalLexicon()
    }

    @Test
    fun strike1_stagesWordWithoutShielding() {
        // Novel word typed once
        val count = lexicon.recordOccurrence("zixor")
        assertEquals(1, count)
        assertEquals(1, lexicon.countOf("zixor"))
        assertTrue(lexicon.hasWord("zixor"))
        // Strike 1 does NOT shield the word
        assertFalse(lexicon.isShielded("zixor"))
    }

    @Test
    fun strike2_elevatesWordToInviolableShield() {
        lexicon.recordOccurrence("zixor")
        assertFalse(lexicon.isShielded("zixor"))

        // Typed a second time
        val count = lexicon.recordOccurrence("zixor")
        assertEquals(2, count)
        assertEquals(2, lexicon.countOf("zixor"))
        // Strike 2 joins the Inviolable Lexicon Shield
        assertTrue(lexicon.isShielded("zixor"))
    }

    @Test
    fun caseInsensitiveHandling() {
        lexicon.recordOccurrence("Rahul")
        assertEquals(1, lexicon.countOf("rahul"))
        assertEquals(1, lexicon.countOf("RAHUL"))

        lexicon.recordOccurrence("RAHUL")
        assertEquals(2, lexicon.countOf("rahul"))
        assertTrue(lexicon.isShielded("Rahul"))
    }

    @Test
    fun decrementOrRemove_unshieldsAndPurges() {
        lexicon.recordOccurrence("flux")
        lexicon.recordOccurrence("flux")
        assertTrue(lexicon.isShielded("flux"))

        // Backspace / undo decrements count to 1 -> unshielded
        val remaining1 = lexicon.decrementOrRemove("flux")
        assertEquals(1, remaining1)
        assertEquals(1, lexicon.countOf("flux"))
        assertFalse(lexicon.isShielded("flux"))
        assertTrue(lexicon.hasWord("flux"))

        // Another decrement drops to 0 -> purged
        val remaining2 = lexicon.decrementOrRemove("flux")
        assertEquals(0, remaining2)
        assertEquals(0, lexicon.countOf("flux"))
        assertFalse(lexicon.hasWord("flux"))
    }

    @Test
    fun prefixMatching_ordersByCountDescending() {
        lexicon.recordOccurrence("fluxboard", 5)
        lexicon.recordOccurrence("fluxbloom", 10)
        lexicon.recordOccurrence("flux", 2)
        lexicon.recordOccurrence("other", 8)

        val matches = lexicon.wordsMatchingPrefix("flux", 10)
        assertEquals(3, matches.size)
        assertEquals("fluxbloom", matches[0].first)
        assertEquals(10, matches[0].second)
        assertEquals("fluxboard", matches[1].first)
        assertEquals(5, matches[1].second)
        assertEquals("flux", matches[2].first)
        assertEquals(2, matches[2].second)
    }

    @Test
    fun capacityCap_prunesLowestFrequency() {
        val smallLexicon = BloomPersonalLexicon(maxCapacity = 10)
        // Insert 10 words
        for (i in 1..10) {
            smallLexicon.recordOccurrence("word$i", increment = i)
        }
        assertEquals(10, smallLexicon.size)

        // Adding 11th novel word triggers pruning of lowest frequency
        smallLexicon.recordOccurrence("novelword", 15)
        assertTrue(smallLexicon.size <= 10)
        assertTrue(smallLexicon.hasWord("novelword"))
        // The lowest frequency word (word1 with count 1) was pruned
        assertFalse(smallLexicon.hasWord("word1"))
    }

    @Test
    fun decayAll_decrementsAndPurges() {
        lexicon.recordOccurrence("frequent", 3)
        lexicon.recordOccurrence("rare", 1)

        lexicon.decayAll(1)

        assertEquals(2, lexicon.countOf("frequent"))
        assertTrue(lexicon.isShielded("frequent"))

        assertEquals(0, lexicon.countOf("rare"))
        assertFalse(lexicon.hasWord("rare"))
    }

    @Test
    fun clear_dropsAllEntries() {
        lexicon.recordOccurrence("hello", 3)
        lexicon.recordOccurrence("world", 2)
        assertEquals(2, lexicon.size)

        lexicon.clear()
        assertEquals(0, lexicon.size)
        assertFalse(lexicon.hasWord("hello"))
        assertFalse(lexicon.hasWord("world"))
    }
}
