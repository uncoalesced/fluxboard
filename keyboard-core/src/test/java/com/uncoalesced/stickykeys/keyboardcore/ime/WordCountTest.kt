// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.TypingStatsStore
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * "Words typed", counted at the word boundary rather than guessed from the keystroke count.
 *
 * The count is taken inside `learn`, which is the one function every finished word already
 * reaches -- an ordinary space, an applied autocorrect, a tapped suggestion and a committed
 * glide all arrive there and nowhere else. That is what these cases pin: not that some
 * counter exists, but that each of the four routes counts exactly once, that a word the user
 * did not finish counts nothing, and that a private field still counts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WordCountTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var stats: TypingStatsStore
    private lateinit var incognito: IncognitoState
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        stats = mockk(relaxed = true)
        incognito = IncognitoState()
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        // A real flow rather than the relaxed mock: the view model collects this one, and
        // StateFlow.collect returns Nothing, which a relaxed mock cannot satisfy.
        every { prefs.privateModeEnabled } returns MutableStateFlow(false)
        viewModel =
            TypingViewModel(
                mockk<PredictionEngine>(relaxed = true),
                prefs,
                mockk<ThemeManager>(relaxed = true),
                mockk<LayoutManager>(relaxed = true),
                mockk<HapticsManager>(relaxed = true),
                incognito,
                mockk<UsageRecorder>(relaxed = true),
                stats,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a word ended by a space counts once`() =
        runTest(dispatcher) {
            viewModel.onWordAccepted("hello")
            advanceUntilIdle()

            verify(exactly = 1) { stats.recordWord() }
        }

    @Test
    fun `an applied autocorrect counts the word once, not twice`() =
        runTest(dispatcher) {
            // The correction replaces the typed word rather than adding a second one. The
            // space bar branch picks either this or `onWordAccepted` and never both, so a
            // corrected word must not arrive here already counted.
            viewModel.onAutoCorrected("teh", "the")
            advanceUntilIdle()

            verify(exactly = 1) { stats.recordWord() }
        }

    @Test
    fun `a committed glide counts once, however many keys it crossed`() =
        runTest(dispatcher) {
            viewModel.onGlideCommitted("hello", listOf("help", "hell"))
            advanceUntilIdle()

            verify(exactly = 1) { stats.recordWord() }
        }

    @Test
    fun `a tapped suggestion counts a word even though it is not typed out`() =
        runTest(dispatcher) {
            viewModel.onSuggestionSelected("hello")
            advanceUntilIdle()

            verify(exactly = 1) { stats.recordWord() }
        }

    @Test
    fun `a word the user abandoned counts nothing`() =
        runTest(dispatcher) {
            // Dragging the caret out of a half-typed word is the case `onWordAbandoned`
            // exists for: it is not evidence of a word, which is why it does not learn one
            // either. A count taken from the space bar rather than the word boundary would
            // get this wrong.
            viewModel.onKeyPressed("h")
            viewModel.onKeyPressed("e")
            viewModel.onWordAbandoned()
            advanceUntilIdle()

            verify(exactly = 0) { stats.recordWord() }
        }

    @Test
    fun `a blank word counts nothing`() =
        runTest(dispatcher) {
            // A space in an empty field, or a space straight after a tapped suggestion has
            // already written its own trailing space. Both reach the word-ending branch with
            // nothing in front of the caret.
            viewModel.onWordAccepted("")
            viewModel.onWordFinished("")
            advanceUntilIdle()

            verify(exactly = 0) { stats.recordWord() }
        }

    @Test
    fun `incognito suspends learning but not counting`() =
        runTest(dispatcher) {
            // Deliberate, and the thing most likely to be "corrected" by a later reader. A
            // counter holds no text, and keystrokes are already counted in a private field.
            // Counting below the gate instead would leave "Words typed" frozen while "Keys
            // typed" beside it kept climbing, which reads as a broken stat rather than as a
            // privacy feature.
            viewModel.onIncognitoChanged(true)

            viewModel.onWordAccepted("secret")
            advanceUntilIdle()

            verify(exactly = 1) { stats.recordWord() }
        }
}
