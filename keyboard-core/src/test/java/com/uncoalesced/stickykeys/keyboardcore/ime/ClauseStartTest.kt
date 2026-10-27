// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where [startsClause] says a clause begins, which decides "ill" against "I'll". */
class ClauseStartTest {
    @Test
    fun `a word after a clause break opens a clause`() {
        assertTrue(startsClause("ill"))
        assertTrue(startsClause("Okay, ill"))
        assertTrue(startsClause("Okay,ill"))
        assertTrue(startsClause("Sorry. ill"))
        assertTrue(startsClause("wait; ill"))
        assertTrue(startsClause("note: ill"))
        assertTrue(startsClause("(ill"))
        assertTrue(startsClause("first line\nill"))
    }

    @Test
    fun `a word inside a clause does not`() {
        assertFalse(startsClause("I am ill"))
        assertFalse(startsClause("she felt ill"))
        assertFalse(startsClause("Okay, I am ill"))
    }
}
