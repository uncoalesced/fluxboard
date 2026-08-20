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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A glide's losing readings reach the suggestion strip, and survive the commit that put them
 * there.
 *
 * Being wrong is the *normal* case for a glide -- several real words are usually valid readings
 * of one path -- so the runners-up are not a nicety, they are how a wrong guess becomes one tap
 * instead of a delete and a re-glide. Reported against v0.1.6 as "glide type wasnt able to get
 * suggestions", which is what this state looks like when it goes missing.
 *
 * The ordering is the fragile part and the reason this is a test rather than a comment. The
 * view abandons the partial word the finger crossed *before* committing the glide, and
 * `onWordAbandoned` clears the strip -- so the two calls only produce a populated strip in one
 * order. Swapping them, or adding any further clear after the commit, empties it with nothing
 * about either call looking wrong on its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlideSuggestionsTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TypingViewModel
    private lateinit var incognito: IncognitoState

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        every { prefs.privateModeEnabled } returns MutableStateFlow(false)
        incognito = IncognitoState()
        viewModel =
            TypingViewModel(
                mockk<PredictionEngine>(relaxed = true),
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

    /**
     * The sequence `TypingKeyboardView.onGlide` actually performs, in its actual order.
     *
     * Abandon the partial word the finger crossed, commit the winner, then hand the readings
     * over. Anything that clears the strip after the last of those is the defect this pins.
     */
    private fun commitGlideAsTheViewDoes(
        word: String,
        candidates: List<String>,
    ) {
        viewModel.onWordAbandoned()
        viewModel.onGlideCommitted(word, candidates)
    }

    @Test
    fun `the losing readings are left in the strip`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            commitGlideAsTheViewDoes("hello", listOf("hello", "hell", "hallo"))

            assertEquals(listOf("hell", "hallo"), viewModel.suggestions.value)
        }

    /** The winner is already in the text; offering it again is a tap that does nothing. */
    @Test
    fun `the committed word is not offered back`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            commitGlideAsTheViewDoes("to", listOf("to", "too"))

            assertEquals(listOf("too"), viewModel.suggestions.value)
        }

    /** The decoder keeps five readings; the strip has room for three. */
    @Test
    fun `the strip stays as short as it is for typed words`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            commitGlideAsTheViewDoes(
                "hello",
                listOf("hello", "hell", "hallo", "hollow", "hell's"),
            )

            assertTrue(
                "strip must not grow for a glide: ${viewModel.suggestions.value}",
                viewModel.suggestions.value.size <= 3,
            )
        }

    /**
     * Incognito suppresses *writes* and leaves reads working, so the readings still show.
     *
     * They are readings of a word the user has already committed and can see on screen -- the
     * gesture put it there. Withholding them would remove the only cheap way to fix a wrong
     * glide, in exchange for hiding nothing that is not already visible. A sensitive field is
     * the separate, stricter gate: there `decodeGlide` returns nothing, so no commit reaches
     * here at all (see GlideGateTest).
     */
    @Test
    fun `incognito does not empty the strip after a glide`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0, noPersonalizedLearning = true)
            commitGlideAsTheViewDoes("hello", listOf("hello", "hell"))

            assertEquals(listOf("hell"), viewModel.suggestions.value)
        }

    /** A path with exactly one reading has nothing to offer, and an empty strip is correct. */
    @Test
    fun `a single-reading glide leaves the strip empty rather than echoing itself`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            commitGlideAsTheViewDoes("hello", listOf("hello"))

            assertTrue(viewModel.suggestions.value.isEmpty())
        }
}
