// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
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
 * What the return key is allowed to teach the dictionary.
 *
 * Found on a device, and the damage is cumulative rather than momentary. Enter deliberately
 * does not autocorrect -- correcting before it needs a blocking lookup and correcting after it
 * is too late in a field that has already sent -- but it still called `onWordFinished()`, which
 * learns. So the one path that skips the check was also writing to disk.
 *
 * Measured, in a single session:
 *
 *   1. "teh" + space  -> "The".  Autocorrect works.
 *   2. "teh" + Enter  -> "Teh", and "teh" is learned. Frequency 1.
 *   3. "teh" + Enter  -> frequency 2.
 *   4. "teh" + space  -> "Teh". No longer corrected, permanently, on disk.
 *   5. The suggestion strip now offers "teh" as a word.
 *
 * In a send-on-enter chat app that is the *ordinary* typing path, so two sends of a typo make
 * it un-correctable and nothing looks broken while it happens. The split below is the fix: a
 * submit-style Enter abandons the word, a newline Enter ends it the way the space bar does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnterLearningTest {
    // --- the classifier, which decides which of the two Enter is ---------------------------

    private val singleLine = InputType.TYPE_CLASS_TEXT

    @Test
    fun `a multi-line text field gets a newline`() {
        assertTrue(
            enterInsertsNewline(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                EditorInfo.IME_ACTION_UNSPECIFIED,
            ),
        )
    }

    @Test
    fun `the IME multi-line flag counts too`() {
        // Set by hosts that want a newline from the IME while keeping the field itself single
        // line in their own layout. Missing it would silently make those fields un-learnable.
        assertTrue(
            enterInsertsNewline(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE,
                EditorInfo.IME_ACTION_UNSPECIFIED,
            ),
        )
    }

    @Test
    fun `a field that suppresses the enter action gets a newline`() {
        // IME_FLAG_NO_ENTER_ACTION says there is nothing to submit to, so the key falls back
        // to a raw newline and there is no send to race.
        assertTrue(
            enterInsertsNewline(singleLine, EditorInfo.IME_FLAG_NO_ENTER_ACTION),
        )
    }

    @Test
    fun `an ordinary single-line field submits`() {
        assertFalse(enterInsertsNewline(singleLine, EditorInfo.IME_ACTION_UNSPECIFIED))
        assertFalse(enterInsertsNewline(singleLine, EditorInfo.IME_ACTION_SEND))
        assertFalse(enterInsertsNewline(singleLine, EditorInfo.IME_ACTION_SEARCH))
    }

    @Test
    fun `a multi-line flag on a non-text class does not count`() {
        // The flag bit is only meaningful for TYPE_CLASS_TEXT. A number field that happens to
        // have the same bit set is still a single-line field.
        assertFalse(
            enterInsertsNewline(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                EditorInfo.IME_ACTION_UNSPECIFIED,
            ),
        )
    }

    // --- what the ViewModel does with it -----------------------------------------------------

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
    fun `the field's enter behaviour reaches the typing model`() {
        viewModel.onInputStarted(initialCapsMode = 0, enterIsNewline = true)
        assertTrue(viewModel.enterEndsAWord())

        viewModel.onInputStarted(initialCapsMode = 0, enterIsNewline = false)
        assertFalse(viewModel.enterEndsAWord())
    }

    @Test
    fun `it is session scoped like every other field property`() =
        runTest(dispatcher) {
            // A note field followed by a search box must not leave Enter still learning.
            viewModel.onInputStarted(initialCapsMode = 0, enterIsNewline = true)
            viewModel.onInputStarted(initialCapsMode = 0)
            assertFalse(viewModel.enterEndsAWord())
        }

    @Test
    fun `abandoning a word learns nothing`() =
        runTest(dispatcher) {
            // What a submit-style Enter now does. The word is dropped, not written.
            viewModel.onKeyPressed("t")
            viewModel.onKeyPressed("e")
            viewModel.onKeyPressed("h")
            viewModel.onWordAbandoned()
            advanceUntilIdle()

            coVerify(exactly = 0) { engine.learnWord(any()) }
        }

    @Test
    fun `finishing a word learns exactly the word it was given`() =
        runTest(dispatcher) {
            // Not the running buffer. The caller reads the editor and passes what is actually
            // there, which is what stops a field that filtered the keystrokes out from
            // teaching them anyway.
            viewModel.onKeyPressed("q")
            viewModel.onKeyPressed("w")
            viewModel.onKeyPressed("x")
            viewModel.onWordFinished("")
            advanceUntilIdle()
            coVerify(exactly = 0) { engine.learnWord(any()) }

            viewModel.onWordFinished("hello")
            advanceUntilIdle()
            coVerify(exactly = 1) { engine.learnWord("hello") }
        }

    // --- auto-capitalize on credential fields ------------------------------------------------

    @Test
    fun `auto-capitalize is suppressed on a password field`() {
        // initialCapsMode is non-zero for any empty text field, whatever its variation, so a
        // password field armed shift and the first character was not the one the user pressed.
        // Observed on device as "correcthorse" entered as "Correcthorse" -- and in a masked
        // field the user cannot see it, which makes it worse rather than more forgivable.
        viewModel.onInputStarted(initialCapsMode = 1, fieldKind = FieldKind.PASSWORD)
        assertFalse(viewModel.shouldAutoCapitalize.value)
    }

    @Test
    fun `auto-capitalize is suppressed on a PIN field`() {
        viewModel.onInputStarted(initialCapsMode = 1, fieldKind = FieldKind.PIN)
        assertFalse(viewModel.shouldAutoCapitalize.value)
    }

    @Test
    fun `the manual privacy switch does not suppress auto-capitalize`() {
        // The gate is isCredential, not isSensitive, and this is why. PRIVATE is a switch the
        // user threw over ordinary prose; silently dropping capitals there would be a visible
        // change to normal typing that no privacy control should be making.
        viewModel.onInputStarted(initialCapsMode = 1, fieldKind = FieldKind.NORMAL)
        assertTrue(viewModel.shouldAutoCapitalize.value)
        assertFalse(FieldKind.PRIVATE.isCredential)
        assertTrue(FieldKind.PRIVATE.isSensitive)
    }

    @Test
    fun `an ordinary field still capitalizes`() {
        viewModel.onInputStarted(initialCapsMode = 1, fieldKind = FieldKind.NORMAL)
        assertTrue(viewModel.shouldAutoCapitalize.value)
    }
}
