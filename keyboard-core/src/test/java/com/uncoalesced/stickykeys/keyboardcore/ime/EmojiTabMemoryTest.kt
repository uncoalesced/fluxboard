// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.ime.EmojiPickerViewModel.Companion.EMOTICONS_TAB
import com.uncoalesced.stickykeys.keyboardcore.ime.EmojiPickerViewModel.Companion.FIRST_EMOJI_TAB
import com.uncoalesced.stickykeys.keyboardcore.ime.EmojiPickerViewModel.Companion.RECENT_TAB
import com.uncoalesced.stickykeys.keyboardcore.ime.EmojiPickerViewModel.Companion.tabOnOpen
import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiTabMemoryTest {
    @Test
    fun `a fresh install opens on Recent when there is something recent`() {
        assertEquals(RECENT_TAB, tabOnOpen(remembered = null, recentIsEmpty = false))
    }

    @Test
    fun `a fresh install with nothing recent opens on the first emoji group`() {
        assertEquals(FIRST_EMOJI_TAB, tabOnOpen(remembered = null, recentIsEmpty = true))
    }

    @Test
    fun `the tab the user last picked is reopened`() {
        assertEquals(EMOTICONS_TAB, tabOnOpen(remembered = EMOTICONS_TAB, recentIsEmpty = false))
        val group = FIRST_EMOJI_TAB + 3
        assertEquals(group, tabOnOpen(remembered = group, recentIsEmpty = true))
    }

    @Test
    fun `a remembered Recent never opens an empty grid`() {
        assertEquals(FIRST_EMOJI_TAB, tabOnOpen(remembered = RECENT_TAB, recentIsEmpty = true))
    }

    @Test
    fun `a remembered Recent opens on Recent once it has something`() {
        assertEquals(RECENT_TAB, tabOnOpen(remembered = RECENT_TAB, recentIsEmpty = false))
    }
}
