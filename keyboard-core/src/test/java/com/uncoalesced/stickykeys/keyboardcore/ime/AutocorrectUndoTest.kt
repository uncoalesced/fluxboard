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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What undoing an autocorrect teaches, and the corrections offered when one is declined
 * (roadmap 4R.3).
 *
 * The correction path learned the *replacement* and nothing ever learned the original, so
 * undoing "zorbly" -> "only" taught the dictionary "only" and the same correction fired on
 * the next "zorbly". An undo, from the chip or from a backspace straight after, now keeps
 * the original. And a correction too uncertain to apply is offered in the strip instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
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

    @Test
    fun `multi-word correction restores original on backspace and chip undo`() =
        runTest(dispatcher) {
            val controller = MutableBufferController("Thanks ")
            "alot".forEach { ch -> pressKey(ch.toString(), controller, this) }
            coEvery { engine.getAutoCorrection("alot", any(), any(), any()) } returns "a lot"

            pressKey("SPACE", controller, this)
            advanceUntilIdle()

            assertEquals("Thanks a lot ", controller.text)
            assertEquals(
                UndoAction("alot", "a lot", suffix = " "),
                viewModel.autocorrectToRevert(controller.text),
            )
            coVerify(exactly = 0) { engine.learnWord("a lot") }
            coVerify(exactly = 1) { engine.learnWord("a") }
            coVerify(exactly = 1) { engine.learnWord("lot") }

            pressKey("DEL", controller, this)
            advanceUntilIdle()

            assertEquals("Thanks alot", controller.text)
            assertEquals("alot", viewModel.getCurrentWord())
            assertNull(viewModel.autocorrectToRevert(controller.text))

            // Now verify chip undo restores original plus trailing space.
            val chipController = MutableBufferController("Thanks a lot ")
            viewModel.onAutoCorrected("alot", "a lot", suffix = " ")
            val undo = viewModel.undoState.value!!
            val span = undo.corrected.length + undo.suffix.length
            assertEquals(6, span)
            chipController.replaceTextBeforeCursor(span, undo.original + undo.suffix)
            viewModel.onUndoApplied(undo.original)
            advanceUntilIdle()

            assertEquals("Thanks alot ", chipController.text)
            coVerify { engine.keepWord("alot") }
        }

    @Test
    fun `punctuation correction reverts cleanly via backspace and chip undo`() =
        runTest(dispatcher) {
            val controller = MutableBufferController()
            "Recieve".forEach { ch -> pressKey(ch.toString(), controller, this) }
            coEvery { engine.getAutoCorrection("Recieve", any(), any(), any()) } returns "Receive"

            pressKey(".", controller, this)
            advanceUntilIdle()

            assertEquals("Receive.", controller.text)
            val expectedUndo = UndoAction("Recieve", "Receive", suffix = ".")
            assertEquals(expectedUndo, viewModel.autocorrectToRevert(controller.text))

            pressKey("DEL", controller, this)
            advanceUntilIdle()

            assertEquals("Recieve", controller.text)
            assertEquals("Recieve", viewModel.getCurrentWord())
            assertNull(viewModel.autocorrectToRevert(controller.text))

            // Verify chip undo on punctuation suffix "." restores "Recieve.".
            val chipController = MutableBufferController("Receive.")
            viewModel.onAutoCorrected("Recieve", "Receive", suffix = ".")
            val undo = viewModel.undoState.value!!
            chipController.replaceTextBeforeCursor(
                undo.corrected.length + undo.suffix.length,
                undo.original + undo.suffix,
            )
            viewModel.onUndoApplied(undo.original)
            advanceUntilIdle()

            assertEquals("Recieve.", chipController.text)
        }

    @Test
    fun `punctuation correction survives rapid trailing space without learning typo`() =
        runTest(dispatcher) {
            val controller = MutableBufferController()
            "Recieve".forEach { ch -> pressKey(ch.toString(), controller, this) }
            coEvery { engine.getAutoCorrection("Recieve", any(), any(), any()) } returns "Receive"

            pressKey(".", controller, this)
            pressKey("SPACE", controller, this)
            assertEquals("Recieve. ", controller.text)

            advanceUntilIdle()

            assertEquals("Receive. ", controller.text)
            coVerify(exactly = 0) { engine.learnWord("Recieve") }
            coVerify(exactly = 0) { engine.learnWord("recieve") }
            coVerify(exactly = 1) { engine.learnWord("Receive") }

            val expectedUndo = UndoAction("Recieve", "Receive", suffix = ". ")
            assertEquals(expectedUndo, viewModel.autocorrectToRevert(controller.text))

            // Verify chip undo restores "Recieve. ".
            val chipController = MutableBufferController(controller.text)
            val undo = viewModel.undoState.value!!
            chipController.replaceTextBeforeCursor(
                undo.corrected.length + undo.suffix.length,
                undo.original + undo.suffix,
            )
            assertEquals("Recieve. ", chipController.text)

            // Verify backspace reverts "Receive. " (9 chars) back to "Recieve".
            pressKey("DEL", controller, this)
            advanceUntilIdle()

            assertEquals("Recieve", controller.text)
            assertEquals("Recieve", viewModel.getCurrentWord())
        }

    @Test
    fun `fast typing after space cancels rewrite without learning the typo`() =
        runTest(dispatcher) {
            val controller = MutableBufferController()
            "teh".forEach { ch -> pressKey(ch.toString(), controller, this) }
            coEvery { engine.getAutoCorrection("teh", any(), any(), any()) } returns "the"

            pressKey("SPACE", controller, this)
            pressKey("x", controller, this)
            advanceUntilIdle()

            assertEquals("teh x", controller.text)
            coVerify(exactly = 0) { engine.learnWord("teh") }
        }

    private fun pressKey(
        label: String,
        controller: KeyboardController,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        handleKeyPress(
            keyLabel = label,
            controller = controller,
            currentMode = KeyboardMode.LETTERS_LOWER,
            viewModel = viewModel,
            coroutineScope = scope,
        ) {}
    }
}

private class MutableBufferController(
    initial: String = "",
) : KeyboardController {
    private val buffer = StringBuilder(initial)

    val text: String
        get() = buffer.toString()

    override fun commitText(text: String) {
        buffer.append(text)
    }

    override fun textBeforeCursor(maxChars: Int): String = buffer.toString().takeLast(maxChars)

    override fun replaceTextBeforeCursor(
        charCount: Int,
        replacement: String,
    ) {
        val deleteCount = charCount.coerceAtMost(buffer.length)
        buffer.delete(buffer.length - deleteCount, buffer.length)
        buffer.append(replacement)
    }

    override fun sendDelete() {
        if (buffer.isNotEmpty()) {
            buffer.deleteCharAt(buffer.length - 1)
        }
    }

    override fun deleteBefore(charCount: Int) {
        val deleteCount = charCount.coerceAtMost(buffer.length)
        buffer.delete(buffer.length - deleteCount, buffer.length)
    }

    override fun sendEnter() {
        buffer.append('\n')
    }

    override fun handleEditorAction() = Unit

    override fun switchMode(mode: AppMode) = Unit

    override fun moveCursor(
        move: CursorMove,
        extend: Boolean,
    ) = Unit

    override fun performEditAction(actionId: Int) = Unit

    override fun showInputMethodPicker() = Unit
}
