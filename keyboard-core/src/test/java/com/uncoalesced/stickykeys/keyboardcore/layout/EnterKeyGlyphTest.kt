// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import android.view.inputmethod.EditorInfo
import com.uncoalesced.stickykeys.keyboardcore.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Enter key draws what it will do.
 *
 * Behaviour was already correct -- `sendEnter` has read the field's declared action since
 * v0.1.4 -- but the key always showed the same return arrow, so a field that would *send*
 * looked identical to one that would insert a newline.
 *
 * The pair worth pinning is UNSPECIFIED and NONE. They are 0 and 1 respectively, so the
 * natural `action != IME_ACTION_NONE` test reads a plain text field (which reports
 * UNSPECIFIED) as having a real action. Both must fall to the plain return arrow.
 */
class EnterKeyGlyphTest {
    private fun enterIcon(action: Int): KeyGlyph.Icon =
        keyGlyph("ENTER", enterAction = action) as KeyGlyph.Icon

    @Test
    fun `each declared action gets its own artwork`() {
        assertEquals(R.drawable.ic_key_enter_send, enterIcon(EditorInfo.IME_ACTION_SEND).res)
        assertEquals(R.drawable.ic_key_search, enterIcon(EditorInfo.IME_ACTION_SEARCH).res)
        assertEquals(R.drawable.ic_key_enter_go, enterIcon(EditorInfo.IME_ACTION_GO).res)
        assertEquals(R.drawable.ic_key_enter_next, enterIcon(EditorInfo.IME_ACTION_NEXT).res)
        assertEquals(R.drawable.ic_key_enter_done, enterIcon(EditorInfo.IME_ACTION_DONE).res)
    }

    @Test
    fun `unspecified and none both fall back to the plain return arrow`() {
        assertEquals(R.drawable.ic_key_enter, enterIcon(EditorInfo.IME_ACTION_UNSPECIFIED).res)
        assertEquals(R.drawable.ic_key_enter, enterIcon(EditorInfo.IME_ACTION_NONE).res)
        // The default argument is the newline case, so a caller that knows nothing about
        // the field is never given a misleading icon.
        assertEquals(R.drawable.ic_key_enter, (keyGlyph("ENTER") as KeyGlyph.Icon).res)
    }

    @Test
    fun `the five actions are all distinguishable from each other`() {
        val icons =
            listOf(
                EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_NEXT,
                EditorInfo.IME_ACTION_DONE,
            ).map { enterIcon(it).res }
        assertEquals("no two actions may share artwork", icons.size, icons.toSet().size)
        assertTrue(icons.none { it == R.drawable.ic_key_enter })
    }

    /** The description is read aloud, so it has to change with the artwork. */
    @Test
    fun `the accessibility description names the action`() {
        assertEquals("Send", enterIcon(EditorInfo.IME_ACTION_SEND).description)
        assertEquals("Search", enterIcon(EditorInfo.IME_ACTION_SEARCH).description)
        assertEquals("Done", enterIcon(EditorInfo.IME_ACTION_DONE).description)
        assertEquals("Enter", enterIcon(EditorInfo.IME_ACTION_UNSPECIFIED).description)
    }

    /** A custom layout's own caption still wins, as it does for every other key. */
    @Test
    fun `a display label overrides the action artwork`() {
        val glyph =
            keyGlyph("ENTER", displayLabel = "Go!", enterAction = EditorInfo.IME_ACTION_SEND)
        assertEquals(KeyGlyph.Label("Go!"), glyph)
    }

    /** Nothing else on the board may change because a field declared an action. */
    @Test
    fun `no other key is affected by the action`() {
        for (output in listOf("SHIFT", "DEL", "SPACE", "SYMBOLS", "a")) {
            assertEquals(
                "$output must not vary with the enter action",
                keyGlyph(output),
                keyGlyph(output, enterAction = EditorInfo.IME_ACTION_SEND),
            )
        }
        assertNotEquals(
            keyGlyph("ENTER"),
            keyGlyph("ENTER", enterAction = EditorInfo.IME_ACTION_SEND),
        )
    }
}
