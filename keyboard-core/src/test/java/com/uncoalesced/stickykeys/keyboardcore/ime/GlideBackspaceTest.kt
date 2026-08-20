// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import io.mockk.every
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
import org.junit.Before
import org.junit.Test

/**
 * One gesture put the word there, so one press takes it back.
 *
 * Deleting a glided word letter by letter is eight presses to undo one movement. The state
 * this needs already existed -- `consumeGlideCommit` was written so that tapping a losing
 * reading *replaces* rather than appends, and it is already gated on the generation token, so
 * it returns null the instant anything else touches the text.
 *
 * The ordering inside `onDelete` is the part worth pinning. `consumeGlideCommit` is valid only
 * while the generation still matches the one the glide committed under, and `onDelete` bumps
 * that token -- so reading it after the bump returns null every time and the feature degrades
 * to an ordinary backspace with nothing appearing broken.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlideBackspaceTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        every { prefs.privateModeEnabled } returns MutableStateFlow(false)
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

    /** The word plus the space the glide committed with it: "hello " is six characters. */
    @Test
    fun `a backspace straight after a glide removes the whole word and its space`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onGlideCommitted("hello", listOf("hello", "hell"))

            assertEquals(6, viewModel.onDelete())
        }

    /** Exactly once. A second press is an ordinary backspace over whatever is left. */
    @Test
    fun `the whole-word delete does not repeat`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onGlideCommitted("hello", listOf("hello"))

            assertEquals(6, viewModel.onDelete())
            assertEquals(1, viewModel.onDelete())
        }

    /**
     * The behaviour zap asked to keep: a space ends the glide's claim on the backspace.
     *
     * Traced rather than assumed -- `onSpacePressed` bumps the generation, which is what makes
     * `consumeGlideCommit` return null, so this holds for the same reason every other
     * invalidation does rather than by a rule of its own.
     */
    @Test
    fun `a space after a glide breaks out of whole-word delete`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onGlideCommitted("hello", listOf("hello"))
            viewModel.onSpacePressed()

            assertEquals(1, viewModel.onDelete())
        }

    /** So does typing. Any edit at all invalidates the span the glide wrote. */
    @Test
    fun `a keystroke after a glide breaks out of whole-word delete`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onGlideCommitted("hello", listOf("hello"))
            viewModel.onKeyPressed("x")

            assertEquals(1, viewModel.onDelete())
        }

    /** A new field cannot inherit the previous field's glide. */
    @Test
    fun `a new input session breaks out of whole-word delete`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onGlideCommitted("hello", listOf("hello"))
            viewModel.onInputStarted(initialCapsMode = 0)

            assertEquals(1, viewModel.onDelete())
        }

    /** Ordinary typing is untouched: every backspace is one character. */
    @Test
    fun `a backspace with no glide behind it deletes one character`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onKeyPressed("h")
            viewModel.onKeyPressed("i")

            assertEquals(1, viewModel.onDelete())
            assertEquals(1, viewModel.onDelete())
            assertEquals(1, viewModel.onDelete())
        }
}
