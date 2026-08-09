// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.GlideStroke
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Glide typing must obey the same gates as every other dictionary path.
 *
 * A new way of producing words is a new way of leaking them, and it arrived after the privacy
 * work rather than alongside it. Every existing gate was written against the tap path -- the
 * suggestion strip, autocorrect, learning -- so nothing about them automatically covers a
 * feature that decodes a whole word from a swipe and commits it. This pins that it does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlideGateTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var engine: PredictionEngine
    private lateinit var viewModel: TypingViewModel

    /** A stroke shaped like a real glide, so nothing is rejected for being too short. */
    private val stroke = GlideStroke("hgfdertyuiklo".toList(), setOf(0, 4, 11, 12))

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        engine = mockk(relaxed = true)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        io.mockk.every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        io.mockk.every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        io.mockk.every { prefs.privateModeEnabled } returns MutableStateFlow(false)
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
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a password field is never glide-decoded`() =
        runTest(dispatcher) {
            // The decode is a dictionary lookup over what is being typed, and the result would
            // be committed into a field the user cannot read back to check. Both are reasons
            // the suggestion strip is already suppressed there.
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.PASSWORD)
            assertNull(viewModel.decodeGlide(stroke))
            advanceUntilIdle()
            coVerify(exactly = 0) { engine.decodeGlide(any()) }
        }

    @Test
    fun `a PIN field is never glide-decoded`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.PIN)
            assertNull(viewModel.decodeGlide(stroke))
            advanceUntilIdle()
            coVerify(exactly = 0) { engine.decodeGlide(any()) }
        }

    @Test
    fun `an ordinary field is decoded`() =
        runTest(dispatcher) {
            // The other half of the guard. A gate that also blocks normal typing would be
            // caught by nothing else here.
            io.mockk.coEvery { engine.decodeGlide(any()) } returns listOf("hello", "hell")
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
            assertTrue(viewModel.decodeGlide(stroke) == "hello")
        }

    @Test
    fun `a glided word is learned like any other finished word`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
            viewModel.onGlideCommitted("hello")
            advanceUntilIdle()
            coVerify(exactly = 1) { engine.learnWord("hello") }
        }

    @Test
    fun `a glided word is not learned while incognito`() =
        runTest(dispatcher) {
            // Routed through the same write gate as everything else rather than a second one.
            viewModel.onIncognitoChanged(true)
            viewModel.onGlideCommitted("secret")
            advanceUntilIdle()
            coVerify(exactly = 0) { engine.learnWord(any()) }
        }

    @Test
    fun `committing a glide clears the tracked word rather than deleting text`() =
        runTest(dispatcher) {
            // A glide starts a new word; the keys it crossed were never committed. If this
            // left the tracker populated, the next space would autocorrect against letters
            // that are not in the editor.
            viewModel.onKeyPressed("x")
            viewModel.onKeyPressed("y")
            viewModel.onGlideCommitted("hello")
            assertTrue(viewModel.getCurrentWord().isEmpty())
        }

    @Test
    fun `only letters can take part in a glide`() {
        // A path across shift or backspace says nothing about a word, and letting those start
        // one would mean a mistimed drag off the shift key typed something.
        listOf("a", "z", "q").forEach { assertTrue(it, isGlideCandidate(it)) }
        listOf("SHIFT", "DEL", "SPACE", "ENTER", "SYMBOLS", "1", ",", ".").forEach {
            assertFalse(it, isGlideCandidate(it))
        }
    }
}
