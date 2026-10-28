// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * What undoing an autocorrect teaches, and the corrections offered when one is declined
 * (roadmap 4R.3).
 *
 * The correction path learned the *replacement* and nothing ever learned the original, so
 * undoing "zorbly" -> "only" taught the dictionary "only" and the same correction fired on
 * the next "zorbly". An undo, from the chip or from a backspace straight after, now keeps
 * the original. And a correction too uncertain to apply is offered in the strip instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutocorrectUndoTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var engine: PredictionEngine
    private lateinit var incognito: IncognitoState
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        engine = mockk(relaxed = true)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        every { prefs.privateModeEnabled } returns MutableStateFlow(false)
        incognito = IncognitoState()
        viewModel =
            TypingViewModel(
                engine,
                prefs,
                mockk<ThemeManager>(relaxed = true),
                mockk<LayoutManager>(relaxed = true),
                mockk<HapticsManager>(relaxed = true),
                incognito,
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
    fun `undoing from the chip keeps the original word`() =
        runTest(dispatcher) {
            viewModel.onAutoCorrected("zorbly", "only")
            viewModel.onUndoApplied("zorbly")
            advanceUntilIdle()

            coVerify(exactly = 1) { engine.keepWord("zorbly") }
        }

    @Test
    fun `undoing in incognito keeps nothing`() =
        runTest(dispatcher) {
            viewModel.onIncognitoChanged(true)
            viewModel.onAutoCorrected("zorbly", "only")
            viewModel.onUndoApplied("zorbly")
            advanceUntilIdle()

            coVerify(exactly = 0) { engine.keepWord(any()) }
        }

    @Test
    fun `a backspace straight after a correction takes it back`() =
        runTest(dispatcher) {
            viewModel.onAutoCorrected("teh", "the")

            val revert = viewModel.autocorrectToRevert("I said the ")
            assertEquals(UndoAction("teh", "the"), revert)

            viewModel.onAutocorrectReverted("teh")
            advanceUntilIdle()

            coVerify(exactly = 1) { engine.keepWord("teh") }
            assertEquals("teh", viewModel.getCurrentWord())
            // One revert per correction: the undo state is spent.
            assertNull(viewModel.autocorrectToRevert("I said teh"))
        }

    @Test
    fun `a backspace after anything else is an ordinary delete`() =
        runTest(dispatcher) {
            viewModel.onAutoCorrected("teh", "the")
            // The text no longer ends in the correction plus its space.
            assertNull(viewModel.autocorrectToRevert("I said the x"))

            viewModel.onKeyPressed("x")
            // Typing clears the undo state outright.
            assertNull(viewModel.autocorrectToRevert("I said the "))
        }

    @Test
    fun `a declined correction is offered and a tap replaces the typed word`() =
        runTest(dispatcher) {
            coEvery { engine.getCorrectionOffers("helo") } returns listOf("help", "hello")

            viewModel.onSpacePressed()
            viewModel.onWordAccepted("helo", offerCorrections = true)
            advanceUntilIdle()

            assertEquals(listOf("help", "hello"), viewModel.suggestions.value)
            assertEquals("helo", viewModel.consumeOfferedWord())
            // Consumed once: a second tap must not replace again.
            assertNull(viewModel.consumeOfferedWord())
        }

    @Test
    fun `an offer goes stale as soon as the text moves`() =
        runTest(dispatcher) {
            coEvery { engine.getCorrectionOffers("helo") } returns listOf("help", "hello")

            viewModel.onSpacePressed()
            viewModel.onWordAccepted("helo", offerCorrections = true)
            advanceUntilIdle()
            viewModel.onKeyPressed("w")

            assertNull(viewModel.consumeOfferedWord())
        }

    @Test
    fun `words ended by anything but a space are not offered corrections`() =
        runTest(dispatcher) {
            viewModel.onWordAccepted("helo")
            advanceUntilIdle()

            coVerify(exactly = 0) { engine.getCorrectionOffers(any()) }
            assertNull(viewModel.consumeOfferedWord())
        }
}
