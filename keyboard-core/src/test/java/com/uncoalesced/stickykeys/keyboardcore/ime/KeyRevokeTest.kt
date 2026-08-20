// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Taking back a letter that reached the screen on press and turned out not to be a keystroke.
 *
 * Letters commit when the finger lands, which is what removed the dwell-shaped lag from the
 * press-to-letter path. Two gestures only reveal themselves afterwards -- a press that leaves
 * its key is a glide, and one that outstays the long-press window is a hold -- and both have
 * to put the keyboard back exactly where it was, not merely delete a character. A mirror left
 * one letter long after a glide is the desync that makes the next destructive edit size
 * itself against text that is not there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyRevokeTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        io.mockk.every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        io.mockk.every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        io.mockk.every { prefs.privateModeEnabled } returns MutableStateFlow(false)
        viewModel =
            TypingViewModel(
                mockk<PredictionEngine>(relaxed = true),
                prefs,
                mockk<ThemeManager>(relaxed = true),
                mockk<LayoutManager>(relaxed = true),
                mockk<HapticsManager>(relaxed = true),
                IncognitoState(),
                mockk<com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder>(
                    relaxed = true,
                ),
                mockk<com.uncoalesced.stickykeys.keyboardcore.diagnostics.TypingStatsStore>(
                    relaxed = true,
                ),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the word mirror is left exactly as it was before the press`() =
        runTest(dispatcher) {
            viewModel.onKeyPressed("c")
            viewModel.onKeyPressed("a")
            assertEquals("ca", viewModel.getCurrentWord())

            // The third press left its key: this is a glide starting, not a keystroke.
            viewModel.onKeyPressed("t")
            assertEquals("cat", viewModel.getCurrentWord())
            viewModel.onKeyRevoked("t")
            assertEquals("ca", viewModel.getCurrentWord())
        }

    @Test
    fun `revoking the only letter empties the mirror rather than underflowing it`() =
        runTest(dispatcher) {
            viewModel.onKeyPressed("h")
            viewModel.onKeyRevoked("h")
            assertEquals("", viewModel.getCurrentWord())
        }

    @Test
    fun `auto capitalize comes back on when the first letter of a sentence is taken back`() =
        runTest(dispatcher) {
            // The case a plain deleteBefore would miss. At a sentence start the board is
            // shifted; the press clears that, and a glide beginning on that same key must not
            // leave the keyboard in lower case for a word it never typed.
            viewModel.onInputStarted(initialCapsMode = 1)
            assertTrue(viewModel.shouldAutoCapitalize.value)

            viewModel.onKeyPressed("H")
            assertFalse(viewModel.shouldAutoCapitalize.value)

            viewModel.onKeyRevoked("H")
            assertTrue(viewModel.shouldAutoCapitalize.value)
        }

    @Test
    fun `a revoke mid-word does not re-arm capitalization`() =
        runTest(dispatcher) {
            // The other half of the same restore: it puts back what was there, which
            // mid-word is "not a sentence start". Recomputing from an empty mirror instead
            // would answer true and capitalize the next letter in the middle of a word.
            viewModel.onInputStarted(initialCapsMode = 1)
            viewModel.onKeyPressed("H")
            viewModel.onKeyPressed("e")
            viewModel.onKeyRevoked("e")
            assertFalse(viewModel.shouldAutoCapitalize.value)
        }

    @Test
    fun `a revoked letter invalidates a lookup already in flight for it`() =
        runTest(dispatcher) {
            // The suggestion dispatched for the letter that is now gone must not be allowed
            // to land, so the generation has to move on a revoke exactly as it does on a key.
            val before = viewModel.onSpacePressed()
            viewModel.onKeyPressed("q")
            val afterPress = viewModel.onSpacePressed()
            assertTrue(afterPress > before)

            viewModel.onKeyPressed("x")
            viewModel.onKeyRevoked("x")
            assertFalse(viewModel.isCurrent(afterPress))
        }
}
