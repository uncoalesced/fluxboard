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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The word the keyboard thinks is under the caret, and what happens when it is wrong.
 *
 * `TypingViewModel` keeps a running copy of the in-progress word, appended to on each key and
 * shortened on each backspace, because reading the field per keystroke would be a blocking IPC
 * into the host app. That copy is correct only while this keyboard is the sole editor. The
 * failure it produced was not a cosmetic one: the suggestion strip replaced `word.length`
 * characters, so after a caret tap or a paste it deleted the wrong span of the user's text.
 *
 * These pin both halves of the fix -- deriving the span from the editor at the moment of a
 * destructive edit, and resyncing the tracker when something else moves the caret.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WordContextTest {
    // --- wordUnderCaret ------------------------------------------------------------------

    @Test
    fun `an empty field has no word`() {
        assertEquals("", wordUnderCaret(""))
    }

    @Test
    fun `the whole field is the word when nothing precedes it`() {
        assertEquals("hello", wordUnderCaret("hello"))
    }

    @Test
    fun `only the last word counts`() {
        assertEquals("wor", wordUnderCaret("hello wor"))
    }

    @Test
    fun `punctuation ends a word, so the caret after it is on no word at all`() {
        // The case that matters for the replacement length: the span must be zero here, or a
        // suggestion tap would delete the full stop the user just typed.
        assertEquals("", wordUnderCaret("hello."))
        assertEquals("", wordUnderCaret("hello "))
    }

    @Test
    fun `an interior apostrophe stays part of the word`() {
        // Treating it as a boundary would make a suggestion tap replace only the "t" of
        // "don't", which is precisely the wrong-span deletion this all exists to stop.
        assertEquals("don't", wordUnderCaret("don't"))
        assertEquals("it's", wordUnderCaret("well it's"))
    }

    @Test
    fun `a leading apostrophe is a quote mark and is not part of the word`() {
        // Counting it would add one to the replacement span and swallow the user's quote.
        assertEquals("quoted", wordUnderCaret("he said 'quoted"))
    }

    @Test
    fun `a newline ends a word`() {
        assertEquals("second", wordUnderCaret("first\nsecond"))
    }

    @Test
    fun `digits are not part of a word`() {
        // Matches what the tracker itself accumulates: handleKeyPress routes digits down the
        // symbol path, never into onKeyPressed, so a mismatch here would desync the two.
        assertEquals("abc", wordUnderCaret("123abc"))
    }

    // --- startsNewSentence ---------------------------------------------------------------

    @Test
    fun `sentence start is derived from the text rather than from the last keystroke`() {
        assertTrue(startsNewSentence(""))
        assertTrue(startsNewSentence("Done. "))
        assertTrue(startsNewSentence("Really?"))
        assertTrue(startsNewSentence("Stop!  "))
        assertTrue(startsNewSentence("line one\n"))

        assertFalse(startsNewSentence("mid sentence "))
        assertFalse(startsNewSentence("halfwor"))
    }

    // --- endsAWord -----------------------------------------------------------------------

    @Test
    fun `only real word terminators trigger a correction`() {
        listOf(".", ",", "!", "?", ";", ":").forEach {
            assertTrue("'$it' should end a word", endsAWord(it))
        }
        // Both of these sit inside words. Correcting on them would fire halfway through
        // "don't" and through any hyphenated compound.
        assertFalse(endsAWord("'"))
        assertFalse(endsAWord("-"))
        assertFalse(endsAWord("a"))
        assertFalse(endsAWord("SPACE"))
    }

    // --- currentWordSpan, the destructive path -------------------------------------------

    @Test
    fun `the replacement span comes from the editor, not from the keyboard's own copy`() {
        // The data-loss regression, stated directly. The keyboard believes it is mid-word in
        // "hello"; the editor has since been left with the caret after "goodbye wor". Sizing
        // the replacement from the keyboard's copy deletes five characters instead of three.
        val controller = FakeController("goodbye wor")
        assertEquals(3, currentWordSpan(controller))
    }

    @Test
    fun `the replacement span is zero when the caret is not on a word`() {
        assertEquals(0, currentWordSpan(FakeController("finished. ")))
    }

    @Test
    fun `the read is bounded rather than pulling the whole field`() {
        // An unbounded read crosses a binder transaction with the entire field in it, which is
        // slow at best and TransactionTooLargeException at worst.
        val controller = FakeController("x".repeat(5000))
        currentWordSpan(controller)
        assertEquals(WORD_CONTEXT_CHARS, controller.lastRequestedChars)
    }

    // --- the ViewModel side --------------------------------------------------------------

    private val dispatcher = StandardTestDispatcher()
    private lateinit var engine: PredictionEngine
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        engine = mockk(relaxed = true)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        io.mockk.every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        io.mockk.every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        // A real flow, not the relaxed mock: TypingViewModel collects this one, and
        // StateFlow.collect returns Nothing, which a relaxed mock cannot satisfy.
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
    fun `the tracker is resynced when the caret is moved into another word`() =
        runTest(dispatcher) {
            viewModel.onKeyPressed("h")
            viewModel.onKeyPressed("i")
            assertEquals("hi", viewModel.getCurrentWord())

            // The user taps into the middle of an earlier word. Nothing this keyboard did.
            viewModel.onEditorContextChanged("some earlier tex")
            assertEquals("tex", viewModel.getCurrentWord())
        }

    @Test
    fun `backspacing past the start of a word leaves no word rather than a stale one`() =
        runTest(dispatcher) {
            viewModel.onKeyPressed("a")
            viewModel.onKeyPressed("b")
            // The host reports the caret after a hard delete that took the whole word out.
            viewModel.onEditorContextChanged("done. ")
            assertEquals("", viewModel.getCurrentWord())
        }

    @Test
    fun `a paste is picked up as the new current word`() =
        runTest(dispatcher) {
            viewModel.onEditorContextChanged("pasted content here")
            assertEquals("here", viewModel.getCurrentWord())
        }

    @Test
    fun `an external change re-arms auto capitalize from the text`() =
        runTest(dispatcher) {
            viewModel.onKeyPressed("x")
            assertFalse(viewModel.shouldAutoCapitalize.value)

            viewModel.onEditorContextChanged("A finished sentence. ")
            assertTrue(viewModel.shouldAutoCapitalize.value)

            viewModel.onEditorContextChanged("mid sentence tex")
            assertFalse(viewModel.shouldAutoCapitalize.value)
        }

    @Test
    fun `an external change invalidates an autocorrect already in flight`() =
        runTest(dispatcher) {
            val token = viewModel.onSpacePressed()
            assertTrue(viewModel.isCurrent(token))

            viewModel.onEditorContextChanged("the user moved the caret")

            // Without this the async correction would rewrite text somewhere else entirely.
            assertFalse(viewModel.isCurrent(token))
        }

    @Test
    fun `abandoning a word does not teach it to the dictionary`() =
        runTest(dispatcher) {
            // The scrub-poisoning regression. Every step of a space-bar scrub called
            // onWordFinished, which learns what it clears, so dragging the caret out of the
            // middle of "keyboard" taught the dictionary "keyb".
            viewModel.onKeyPressed("k")
            viewModel.onKeyPressed("e")
            viewModel.onKeyPressed("y")
            viewModel.onKeyPressed("b")
            viewModel.onWordAbandoned()
            advanceUntilIdle()

            coVerify(exactly = 0) { engine.learnWord(any()) }
            assertEquals("", viewModel.getCurrentWord())
        }

    @Test
    fun `a finished word is still learned, so abandoning has not broken learning`() =
        runTest(dispatcher) {
            // The guard against "fixed" by making onWordFinished stop learning too.
            viewModel.onKeyPressed("h")
            viewModel.onKeyPressed("i")
            viewModel.onWordFinished("hi")
            advanceUntilIdle()

            coVerify(exactly = 1) { engine.learnWord("hi") }
        }

    @Test
    fun `committing punctuation does not learn the word before the correction has run`() =
        runTest(dispatcher) {
            // onSymbolCommitted used to be preceded by onWordFinished, so a misspelling ending
            // in a full stop was written to the personal dictionary before anything decided
            // whether it needed correcting -- and two sightings there permanently veto its own
            // correction.
            viewModel.onKeyPressed("t")
            viewModel.onKeyPressed("e")
            viewModel.onKeyPressed("h")
            viewModel.onSymbolCommitted(".")
            advanceUntilIdle()

            coVerify(exactly = 0) { engine.learnWord(any()) }
            assertEquals("", viewModel.getCurrentWord())
        }

    @Test
    fun `punctuation returns a token that a later correction can validate against`() =
        runTest(dispatcher) {
            val token = viewModel.onSymbolCommitted(".")
            assertTrue(viewModel.isCurrent(token))

            viewModel.onKeyPressed("a")
            assertFalse(viewModel.isCurrent(token))
        }
}

/** Records what was asked for, so the bounded-read requirement is assertable. */
private class FakeController(
    private val text: String,
) : KeyboardController {
    var lastRequestedChars: Int = -1
        private set

    override fun textBeforeCursor(maxChars: Int): String {
        lastRequestedChars = maxChars
        return text.takeLast(maxChars)
    }

    override fun commitText(text: String) = Unit

    override fun replaceTextBeforeCursor(
        charCount: Int,
        replacement: String,
    ) = Unit

    override fun sendDelete() = Unit

    override fun sendEnter() = Unit

    override fun handleEditorAction() = Unit

    override fun switchMode(mode: AppMode) = Unit

    override fun moveCursor(
        move: CursorMove,
        extend: Boolean,
    ) = Unit

    override fun performEditAction(actionId: Int) = Unit

    override fun showInputMethodPicker() = Unit
}
