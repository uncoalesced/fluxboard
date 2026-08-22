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
 * A backspaced word stays one tap away, because a backspace has no undo of its own.
 *
 * Every other destructive edit here can be taken back: autocorrect has its undo state, a glide
 * has its losing readings. Deleting leaves nothing, and the strip goes empty at exactly the
 * moment there is something worth offering -- so an accidental press means retyping the word,
 * which for a glided word means typing out by hand the thing the gesture existed to avoid.
 *
 * The two cases here are the two where the strip would otherwise be blank. While a word is
 * still shrinking, ordinary prefix prediction already covers it: "hello" is a completion of
 * "hell" and needs nothing added.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackspaceRestoreTest {
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

    @Test
    fun `undoing a glide offers the word back`() =
        runTest(dispatcher) {
            // One press removed the whole word, so the strip is the only way back to it --
            // and the word a glide wrote is by definition one the user did not want to type
            // out by hand.
            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onGlideCommitted("hello", listOf("hello", "hell"))
            viewModel.onDelete()

            assertEquals(listOf("hello"), viewModel.suggestions.value)
        }

    @Test
    fun `backspacing a typed word to nothing offers the whole word back`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            "hey".forEach { viewModel.onKeyPressed(it.toString()) }

            repeat(3) { viewModel.onDelete() }

            // The word as it was when the run started, not the single letter the mirror held
            // by the time it emptied.
            assertEquals(listOf("hey"), viewModel.suggestions.value)
        }

    @Test
    fun `a later delete offers what was just deleted, not what was deleted before it`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            "hey".forEach { viewModel.onKeyPressed(it.toString()) }
            repeat(3) { viewModel.onDelete() }
            assertEquals(listOf("hey"), viewModel.suggestions.value)

            // Typing accepts the delete. A second run then has its own word, and the first
            // one must not resurface -- an offer that outlives the edit it belongs to would
            // put a word the user moved on from at the front of the strip.
            viewModel.onKeyPressed("x")
            viewModel.onDelete()

            assertEquals(listOf("x"), viewModel.suggestions.value)
        }

    @Test
    fun `a new input session does not inherit the previous field's word`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0)
            "hey".forEach { viewModel.onKeyPressed(it.toString()) }
            repeat(3) { viewModel.onDelete() }

            viewModel.onInputStarted(initialCapsMode = 0)
            viewModel.onDelete()

            assertEquals(emptyList<String>(), viewModel.suggestions.value)
        }
}
