// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import io.mockk.coVerify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 38: while the editor sets IME_FLAG_NO_PERSONALIZED_LEARNING, nothing may
 * be written to the personal dictionary. Reads (suggestions/autocorrect) are
 * unaffected -- only [PredictionEngine.learnWord] is suspended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncognitoLearningTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var engine: PredictionEngine
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        engine = mockk(relaxed = true)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        // Autocorrect/autocap flags are read as StateFlow values.
        io.mockk.every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        io.mockk.every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        viewModel =
            TypingViewModel(
                engine,
                prefs,
                mockk<ThemeManager>(relaxed = true),
                mockk<LayoutManager>(relaxed = true),
                mockk<HapticsManager>(relaxed = true),
                IncognitoState(),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `learns normally when incognito is off`() =
        runTest(dispatcher) {
            viewModel.onIncognitoChanged(false)

            viewModel.onKeyPressed("h")
            viewModel.onKeyPressed("i")
            viewModel.onWordFinished()
            viewModel.onSuggestionSelected("there")
            viewModel.onAutoCorrected("teh", "the")
            advanceUntilIdle()

            coVerify(exactly = 1) { engine.learnWord("hi") }
            coVerify(exactly = 1) { engine.learnWord("there") }
            coVerify(exactly = 1) { engine.learnWord("the") }
        }

    @Test
    fun `writes nothing to the dictionary while incognito`() =
        runTest(dispatcher) {
            viewModel.onIncognitoChanged(true)
            assertTrue(viewModel.incognito.value)

            // Every path that can write: finished word, chosen suggestion, autocorrect.
            viewModel.onKeyPressed("s")
            viewModel.onKeyPressed("e")
            viewModel.onKeyPressed("c")
            viewModel.onWordFinished()
            viewModel.onSuggestionSelected("secret")
            viewModel.onAutoCorrected("scret", "secret")
            advanceUntilIdle()

            coVerify(exactly = 0) { engine.learnWord(any()) }
        }

    @Test
    fun `incognito is session scoped and resets when the next editor allows learning`() =
        runTest(dispatcher) {
            viewModel.onIncognitoChanged(true)
            viewModel.onKeyPressed("a")
            viewModel.onWordFinished()
            advanceUntilIdle()
            coVerify(exactly = 0) { engine.learnWord(any()) }

            // Next input session on a normal field.
            viewModel.onIncognitoChanged(false)
            assertFalse(viewModel.incognito.value)
            viewModel.onKeyPressed("b")
            viewModel.onWordFinished()
            advanceUntilIdle()

            coVerify(exactly = 1) { engine.learnWord("b") }
        }

    @Test
    fun `suggestions still work while incognito`() =
        runTest(dispatcher) {
            io.mockk.coEvery { engine.getSuggestions("th") } returns listOf("the", "that")
            viewModel.onIncognitoChanged(true)

            viewModel.onKeyPressed("t")
            viewModel.onKeyPressed("h")
            advanceUntilIdle()

            // Reading the dictionary is unaffected; only writing is suspended.
            assertTrue(viewModel.suggestions.value.contains("the"))
            coVerify(exactly = 0) { engine.learnWord(any()) }
        }
}
