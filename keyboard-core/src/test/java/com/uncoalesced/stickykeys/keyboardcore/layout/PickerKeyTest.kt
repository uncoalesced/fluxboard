// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.ime.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two doors into the picker.
 *
 * `EMOJI` and `STICKERS` open the same view on different tabs, which is the whole point of
 * there being two of them -- so the thing worth pinning is that they stay distinguishable, both
 * to the eye and to a screen reader. They were one token until key remapping needed to be able
 * to name the emoji tab specifically.
 */
class PickerKeyTest {
    @Test
    fun `the two picker keys do not draw the same glyph`() {
        val emoji = keyGlyph("EMOJI") as KeyGlyph.Icon
        val stickers = keyGlyph("STICKERS") as KeyGlyph.Icon

        assertNotEquals(emoji.res, stickers.res)
        assertEquals(R.drawable.ic_key_emoji, emoji.res)
        assertEquals(R.drawable.ic_quick_grid, stickers.res)
        // Announced separately too. Two keys reading as "Stickers and emoji" would leave a
        // TalkBack user unable to tell which one they were on.
        assertNotEquals(emoji.description, stickers.description)
    }

    @Test
    fun `every remappable action is a control token the keyboard already understands`() {
        // The remap dialog lets these through where it rejects the rest of CONTROL_OUTPUTS. One
        // that had drifted out of that set would be saved as literal text and type its own name.
        assertTrue(CONTROL_OUTPUTS.containsAll(REMAPPABLE_ACTIONS))
    }

    @Test
    fun `remappable actions are destinations, never structure`() {
        // LayoutValidator refuses to save a page missing any of these, so offering one as a
        // remap target would let a user overwrite the only Space key from a chip and then be
        // unable to save -- or, worse, be offered a "fix" that removes something else.
        val structural = listOf("SPACE", "DEL", "ENTER", "SYMBOLS", "ABC", "SHIFT")
        assertTrue(REMAPPABLE_ACTIONS.none { it in structural })
    }

    @Test
    fun `the shipped board reaches the picker from both letter and symbol pages`() {
        val letters = KeyboardLayouts.letterRows(upper = false, showNumberRow = false)
        assertTrue(letters.flatten().any { it.output == "EMOJI" })
        assertTrue(KeyboardLayouts.symbolsPrimaryRows.flatten().any { it.output == "EMOJI" })
        assertTrue(KeyboardLayouts.symbolsShiftedRows.flatten().any { it.output == "EMOJI" })
    }
}
