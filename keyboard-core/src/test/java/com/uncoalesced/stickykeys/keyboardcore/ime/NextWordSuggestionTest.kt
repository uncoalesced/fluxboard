// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import io.mockk.coEvery
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
import org.junit.Before
import org.junit.Test

/**
 * GitHub issue #17: after a space the strip predicts, rather than sitting blank.
 *
 * "I type hello and then hit space, the next word isn't predicted, its just blank." That was
 * not a broken lookup -- it was a scope boundary. Completion refuses to let sentence context
 * contribute candidates, on the grounds that a word which does not match the prefix is not a
 * completion of it, and with nothing typed the prefix check short-circuited before context was
 * ever consulted. So the strip went blank at exactly the moment the corpus had the most to say.
 *
 * The completion rule is unchanged and is asserted here too: the moment a letter is typed, the
 * strip goes back to completing it and context is only allowed to re-rank.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NextWordSuggestionTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var engine: PredictionEngine
    private lateinit var incognito: IncognitoState
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        every { prefs.privateModeEnabled } returns MutableStateFlow(false)
        engine = mockk<PredictionEngine>(relaxed = true)
        coEvery { engine.getNextWordSuggestions(any()) } returns emptyList()
        coEvery { engine.getNextWordSuggestions("hello") } returns
            listOf("there", "again", "world")
        coEvery { engine.getSuggestions(any(), any()) } returns listOf("completion")
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
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The space-bar path exactly as `handleKeyPress` runs it for a word with no correction. */
    private fun finishWordWithSpace(word: String) {
        viewModel.onSpacePressed()
        viewModel.onWordAccepted(word)
    }

    @Test
    fun `a finished word predicts what usually follows it`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onKeyPressed("h")
            finishWordWithSpace("hello")
            advanceUntilIdle()

            assertEquals(listOf("there", "again", "world"), viewModel.suggestions.value)
        }

    @Test
    fun `a word the corpus has never seen leaves the strip blank as before`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            finishWordWithSpace("qwertyuiop")
            advanceUntilIdle()

            assertEquals(emptyList<String>(), viewModel.suggestions.value)
        }

    /**
     * The completion rule survives. Once a letter exists, context re-ranks and never generates.
     */
    @Test
    fun `typing a letter goes back to completing it`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            finishWordWithSpace("hello")
            advanceUntilIdle()
            viewModel.onKeyPressed("t")
            advanceUntilIdle()

            assertEquals(listOf("completion"), viewModel.suggestions.value)
        }

    /**
     * The race the blank-prefix path introduces, and the reason the lookup carries a token.
     *
     * The next-word lookup is dispatched when the space commits and the completion lookup when
     * the following letter lands, so both are in flight at once and whichever resolves last
     * wins. Without the generation check that is the prediction, arriving on top of a word the
     * user is already several characters into.
     */
    @Test
    fun `a prediction in flight cannot land on top of a word already being typed`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            // No advanceUntilIdle between these: the next-word lookup is still suspended when
            // the keystroke arrives, which is the whole point.
            finishWordWithSpace("hello")
            viewModel.onKeyPressed("t")
            advanceUntilIdle()

            assertEquals(listOf("completion"), viewModel.suggestions.value)
        }

    /**
     * Not across a full stop.
     *
     * The corpus counts "hello"/"there" because they were adjacent, and after a sentence ends
     * the next word is not following the previous one in any sense the table measured. This is
     * the same judgement `onSentenceStarted` already makes by dropping the context outright.
     *
     * The call order is the whole test and is taken from `handleKeyPress`, not from what reads
     * naturally. Typing "." runs `onSymbolCommitted` first -- which is what raises
     * `atSentenceStart` -- and only then dispatches the correction lookup that ends in
     * `onWordAccepted`, so the word is accepted *after* the boundary is already set. Written the
     * intuitive way round, with `onWordAccepted("hello")` before the full stop, this passed
     * without exercising anything: the trailing `onWordAccepted("")` is blank, so
     * `updateSuggestions` was never reached at all and the strip was empty only because
     * `onSpacePressed` had cleared it. Deleting the guard under test left it green.
     */
    @Test
    fun `a sentence boundary suppresses the prediction`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onSymbolCommitted(".")
            viewModel.onWordAccepted("hello")
            advanceUntilIdle()

            assertEquals(emptyList<String>(), viewModel.suggestions.value)

            // And the space that follows the full stop must not bring it back either: it carries
            // the boundary rather than ending it, which is why `onSpacePressed` deliberately
            // leaves `atSentenceStart` alone.
            finishWordWithSpace("hello")
            advanceUntilIdle()

            assertEquals(emptyList<String>(), viewModel.suggestions.value)
        }

    /**
     * A password field gets nothing, for the same reason it gets no completions.
     *
     * The suppression is a *read* gate and cannot be folded into incognito, which suspends
     * writes only. A strip of predicted words above a password field is a shoulder-surfing
     * hazard before any of it reaches storage, and this is a new way of putting words there.
     */
    @Test
    fun `a sensitive field predicts nothing`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(
                initialCapsMode = 0,
                fieldKind = FieldKind.PASSWORD,
            )
            finishWordWithSpace("hello")
            advanceUntilIdle()

            assertEquals(emptyList<String>(), viewModel.suggestions.value)
        }
}
