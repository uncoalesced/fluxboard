// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-press log records a key's kind, never the key. These pin that every letter
 * collapses to one value, so the log cannot be read back into what was typed.
 */
class KeyKindTest {
    @Test
    fun `every letter is the same kind, whatever its case`() {
        for (c in ('a'..'z') + ('A'..'Z')) {
            assertEquals(KeyKind.LETTER, keyKindOf(c.toString()))
        }
    }

    @Test
    fun `space and delete are told apart from letters`() {
        assertEquals(KeyKind.SPACE, keyKindOf("SPACE"))
        assertEquals(KeyKind.DELETE, keyKindOf("DEL"))
    }

    @Test
    fun `digits, punctuation and function keys are other`() {
        for (key in listOf("1", ".", ",", "SHIFT", "ENTER", "SYMBOLS", "EMOJI", "$")) {
            assertEquals(key, KeyKind.OTHER, keyKindOf(key))
        }
    }
}
