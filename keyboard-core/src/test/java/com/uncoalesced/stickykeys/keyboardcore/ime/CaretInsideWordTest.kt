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
import org.junit.Before
import org.junit.Test

/**
 * A caret inside a word works on the whole word.
 *
 * Found on device: tapping into "hel|o" left the strip completing "hel", and tapping "help"
 * replaced only "hel", giving "help o". The keyboard now reads the rest of the word after the
 * caret, offers replacements for the whole of it, and a tapped suggestion replaces both halves.
 * Backspacing into a word resumes it the same way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaretInsideWordTest {
    // --- the pure reads ----------------------------------------------------------------------

    @Test
    fun `the rest of the word after the caret`() {
        assertEquals("o", wordAfterCaret("o there"))
        assertEquals("", wordAfterCaret(" there"))
        assertEquals("", wordAfterCaret(""))
        assertEquals("t", wordAfterCaret("t' said"))
        assertEquals("n't", wordAfterCaret("n't go"))
    }

    @Test
    fun `a replacement adds a space only where none follows`() {
        assertEquals("hello ", suggestionReplacement("hello", ""))
        assertEquals("hello ", suggestionReplacement("hello", ",then"))
        assertEquals("hello", suggestionReplacement("hello", " there"))
    }

    // --- the ViewModel -----------------------------------------------------------------------

    private val dispatcher = StandardTestDispatcher()
    private lateinit var engine: PredictionEngine
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        engine = mockk(relaxed = true)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        every { prefs.privateModeEnabled } returns MutableStateFlow(false)
        viewModel =
            TypingViewModel(
                engine,
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
    fun `a caret placed inside a word offers replacements for the whole word`() =
        runTest(dispatcher) {
            coEvery { engine.getCorrectionOffers("helo") } returns listOf("help", "hello")

            viewModel.onEditorContextChanged("I said hel", "o there")
            advanceUntilIdle()

            assertEquals(listOf("help", "hello"), viewModel.suggestions.value)
        }

    @Test
    fun `typing at the caret keeps the rest of the word`() =
        runTest(dispatcher) {
            viewModel.onEditorContextChanged("I said hel", "o there")
            advanceUntilIdle()
            viewModel.onKeyPressed("l")
            advanceUntilIdle()

            // "hell" before the caret plus "o" after it is "hello", as in the text.
            coVerify { engine.getCorrectionOffers("hello") }
        }

    @Test
    fun `finishing the word forgets the rest of it`() =
        runTest(dispatcher) {
            viewModel.onEditorContextChanged("I said hel", "o there")
            viewModel.onSpacePressed()
            viewModel.onKeyPressed("w")
            advanceUntilIdle()

            coVerify { engine.getSuggestions("w", any()) }
        }

    @Test
    fun `backspacing over the space after a word resumes it`() =
        runTest(dispatcher) {
            viewModel.onSpacePressed()
            viewModel.onDelete("I said helo ")
            advanceUntilIdle()

            assertEquals("helo", viewModel.getCurrentWord())
            viewModel.onKeyPressed("w")
            assertEquals("helow", viewModel.getCurrentWord())
        }
}
