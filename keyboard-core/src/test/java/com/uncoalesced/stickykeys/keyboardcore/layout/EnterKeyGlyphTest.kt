// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import android.view.inputmethod.EditorInfo
import com.uncoalesced.stickykeys.keyboardcore.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Enter key draws the return arrow unless the field will send.
 *
 * Send earns its own glyph because it is the one action the user cannot take back -- the
 * message leaves. Search, Go, Next and Done each had their own artwork until v0.1.7.5 and
 * now do not: five pictures on one key taught nobody anything, and the return arrow is what
 * people already read as "Enter".
 *
 * The distinction is drawn from the action the field declares and never from which app is
 * in front. A search box inside a messaging app declares IME_ACTION_SEARCH and therefore
 * gets the plain arrow, while that same app's compose field declares IME_ACTION_SEND and
 * gets the paper plane -- which is the behaviour asked for, reached without the keyboard
 * knowing any app's name.
 *
 * The pair worth pinning is UNSPECIFIED and NONE. They are 0 and 1 respectively, so the
 * natural `action != IME_ACTION_NONE` test reads a plain text field (which reports
 * UNSPECIFIED) as having a real action. Both must fall to the plain return arrow.
 */
class EnterKeyGlyphTest {
    private fun enterIcon(action: Int): KeyGlyph.Icon =
        keyGlyph("ENTER", enterAction = action) as KeyGlyph.Icon

    @Test
    fun `only a send field gets its own artwork`() {
        assertEquals(R.drawable.ic_key_enter_send, enterIcon(EditorInfo.IME_ACTION_SEND).res)
    }

    @Test
    fun `every other declared action draws the plain return arrow`() {
        // The case that prompted this: a search box inside a messaging app. The app is one
        // that sends, the field is not, and the key must say so.
        assertEquals(R.drawable.ic_key_enter, enterIcon(EditorInfo.IME_ACTION_SEARCH).res)
        assertEquals(R.drawable.ic_key_enter, enterIcon(EditorInfo.IME_ACTION_GO).res)
        assertEquals(R.drawable.ic_key_enter, enterIcon(EditorInfo.IME_ACTION_NEXT).res)
        assertEquals(R.drawable.ic_key_enter, enterIcon(EditorInfo.IME_ACTION_DONE).res)
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
    fun `send is the only action that differs from the arrow`() {
        val nonSending =
            listOf(
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_NEXT,
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_ACTION_NONE,
            ).map { enterIcon(it).res }
        assertTrue(
            "only a send field may draw something other than the return arrow",
            nonSending.all { it == R.drawable.ic_key_enter },
        )
        assertNotEquals(
            R.drawable.ic_key_enter,
            enterIcon(EditorInfo.IME_ACTION_SEND).res,
        )
    }

    /** The description is read aloud, so it has to change with the artwork. */
    @Test
    fun `the accessibility description names the action`() {
        assertEquals("Send", enterIcon(EditorInfo.IME_ACTION_SEND).description)
        // These read as "Enter" now because that is what the key draws and does. A
        // description that still said "Done" would describe artwork that is no longer there.
        assertEquals("Enter", enterIcon(EditorInfo.IME_ACTION_SEARCH).description)
        assertEquals("Enter", enterIcon(EditorInfo.IME_ACTION_DONE).description)
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
