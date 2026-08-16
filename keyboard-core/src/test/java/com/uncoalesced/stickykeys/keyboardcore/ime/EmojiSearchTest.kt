// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.emoji.Emoji
import com.uncoalesced.stickykeys.keyboardcore.emoji.EmojiGroup
import com.uncoalesced.stickykeys.keyboardcore.emoji.EmojiRepository
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Emoji search filters on the catalogue's own names.
 *
 * Kept at the ViewModel level because that is where the whole of the logic is: the view
 * only chooses which flow to render. The search *pad* is not tested here -- it is a plain
 * grid of letters that calls [EmojiPickerViewModel.onSearchQueryChanged], and there is
 * nothing in it that a test would catch that looking at it would not.
 *
 * Glyphs are built from code points because `scripts/check-source-rules.sh` bans emoji
 * literals in source and does not exempt tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmojiSearchTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private fun glyph(codePoint: Int) = String(Character.toChars(codePoint))

    private val grinning = Emoji(glyph(0x1F600), "1.0", "grinning face")
    private val cat = Emoji(glyph(0x1F431), "1.0", "cat face")
    private val redHeart = Emoji(glyph(0x2764), "1.0", "red heart")

    private val groups =
        listOf(
            EmojiGroup("Smileys", listOf(grinning, cat)),
            EmojiGroup("Symbols", listOf(redHeart)),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): EmojiPickerViewModel {
        val stickers = mockk<StickerRepository>()
        every { stickers.getAllStickers() } returns flowOf(emptyList())
        val emoji = mockk<EmojiRepository>()
        coEvery { emoji.groups() } returns groups
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        every { prefs.recentEmoji() } returns emptyList()
        return EmojiPickerViewModel(stickers, emoji, prefs)
    }

    @Test
    fun `a query returns one synthetic group of matches`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.filteredEmojiGroups.collect { } }

            vm.onSearchQueryChanged("cat")

            val result = vm.filteredEmojiGroups.value
            assertEquals(1, result.size)
            assertEquals(EmojiPickerViewModel.SEARCH_RESULTS_GROUP, result.first().name)
            assertEquals(listOf(cat), result.first().emoji)
        }

    /** Matching is on the whole name, not just its start: "face" has to find both. */
    @Test
    fun `a query matches anywhere in the name`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.filteredEmojiGroups.collect { } }

            vm.onSearchQueryChanged("face")

            assertEquals(
                listOf(grinning, cat),
                vm.filteredEmojiGroups.value
                    .first()
                    .emoji,
            )
        }

    @Test
    fun `an empty query returns the original groups untouched`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.filteredEmojiGroups.collect { } }

            vm.onSearchQueryChanged("cat")
            vm.onSearchQueryChanged("")

            assertEquals(groups, vm.filteredEmojiGroups.value)
        }

    /** Whitespace and case are the user's, not the catalogue's. */
    @Test
    fun `a query is trimmed and case-insensitive`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.filteredEmojiGroups.collect { } }

            vm.onSearchQueryChanged("  RED Heart ")

            assertEquals(
                listOf(redHeart),
                vm.filteredEmojiGroups.value
                    .first()
                    .emoji,
            )
        }

    @Test
    fun `no match yields an empty group rather than the whole catalogue`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.filteredEmojiGroups.collect { } }

            vm.onSearchQueryChanged("zzzzz")

            assertTrue(
                vm.filteredEmojiGroups.value
                    .first()
                    .emoji
                    .isEmpty(),
            )
        }

    /**
     * Picking a category is how a user leaves search. If the query survived that, the tab
     * would read as selected while the grid still showed results.
     */
    @Test
    fun `selecting a tab clears the query`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.filteredEmojiGroups.collect { } }

            vm.onSearchQueryChanged("cat")
            vm.selectTab(EmojiPickerViewModel.FIRST_EMOJI_TAB)

            assertEquals("", vm.searchQuery.value)
            assertEquals(groups, vm.filteredEmojiGroups.value)
        }
}
