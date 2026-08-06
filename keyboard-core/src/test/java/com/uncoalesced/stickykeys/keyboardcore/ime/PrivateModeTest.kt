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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The manual privacy switch, and the reason it is not simply "incognito with a button".
 *
 * The surface-plausible version of this feature is a toggle that flips [IncognitoState] and
 * lights the padlock. It demos perfectly: the indicator comes on, the dictionary stops
 * growing, and every screenshot of it is correct. It is also only half a privacy control,
 * because incognito deliberately suspends dictionary *writes* and leaves *reads* running --
 * so with that version on, every character of the private thing being typed still goes through
 * a dictionary lookup whose results are drawn in a strip above the keyboard, and autocorrect
 * still rewrites it. A switch that looks armed and leaves the two visible behaviours running
 * is worse than no switch, because the user stops watching what they type.
 *
 * So the assertions here are deliberately about the *reads* as much as the writes, and about
 * the failure direction: this switch has to survive the things that legitimately clear the
 * automatic one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivateModeTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var engine: PredictionEngine
    private lateinit var prefs: KeyboardPreferences
    private lateinit var privateFlow: MutableStateFlow<Boolean>
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        engine = mockk(relaxed = true)
        privateFlow = MutableStateFlow(false)
        prefs = mockk(relaxed = true)
        io.mockk.every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(true)
        io.mockk.every { prefs.autoCorrectEnabled } returns MutableStateFlow(true)
        io.mockk.every { prefs.privateModeEnabled } returns privateFlow
        // The switch is persisted, so the ViewModel writes through the preference rather than
        // holding its own copy. Feeding the write back into the flow is what the real
        // SharedPreferences listener does.
        io.mockk.every { prefs.setPrivateMode(any()) } answers { privateFlow.value = firstArg() }
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

    // --- the pure resolution ----------------------------------------------------------------

    @Test
    fun `the switch upgrades an ordinary field and nothing else`() {
        assertEquals(FieldKind.PRIVATE, effectiveFieldKind(FieldKind.NORMAL, true))
        assertEquals(FieldKind.NORMAL, effectiveFieldKind(FieldKind.NORMAL, false))
    }

    @Test
    fun `a PIN field keeps its own kind so it keeps its digit grid`() {
        // The one case where the upgrade would do visible damage: PRIVATE has no numeric
        // layout, so promoting a PIN field to it would put a QWERTY keyboard in front of a
        // passcode prompt -- a privacy switch making a password field worse to use.
        assertEquals(FieldKind.PIN, effectiveFieldKind(FieldKind.PIN, true))
    }

    @Test
    fun `a password field is already at least this strict`() {
        assertEquals(FieldKind.PASSWORD, effectiveFieldKind(FieldKind.PASSWORD, true))
    }

    @Test
    fun `private counts as sensitive`() {
        // Everything downstream gates on isSensitive rather than on the member, so this single
        // property is what carries the switch into both the read gate and the write gate.
        assertTrue(FieldKind.PRIVATE.isSensitive)
    }

    // --- what the ViewModel does with it ------------------------------------------------------

    @Test
    fun `turning it on suspends dictionary writes`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
            viewModel.setPrivateMode(true)
            advanceUntilIdle()

            assertTrue(viewModel.incognito.value)

            viewModel.onKeyPressed("s")
            viewModel.onKeyPressed("s")
            viewModel.onKeyPressed("n")
            viewModel.onWordFinished("ssn")
            viewModel.onSuggestionSelected("something")
            viewModel.onAutoCorrected("sonething", "something")
            advanceUntilIdle()

            coVerify(exactly = 0) { engine.learnWord(any()) }
        }

    @Test
    fun `turning it on also stops suggestions and autocorrect`() =
        runTest(dispatcher) {
            // The half that separates this from plain incognito. Without it the switch is on,
            // the padlock is lit, and completions of the private word are still being drawn
            // above the keyboard for anyone looking at the screen.
            io.mockk.coEvery { engine.getSuggestions(any()) } returns listOf("leaked")
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
            viewModel.setPrivateMode(true)
            advanceUntilIdle()

            viewModel.onKeyPressed("s")
            viewModel.onKeyPressed("e")
            advanceUntilIdle()

            assertTrue(viewModel.suggestions.value.isEmpty())
            coVerify(exactly = 0) { engine.getSuggestions(any()) }
            assertNull(viewModel.getAutoCorrectionFor("sekret"))
            advanceUntilIdle()
            coVerify(exactly = 0) { engine.getAutoCorrection(any()) }
        }

    @Test
    fun `it arms mid-session without waiting for the next field`() =
        runTest(dispatcher) {
            // Thrown from the keyboard's own row, in the middle of typing, which is the whole
            // point of putting it there. A version that only took effect on the next focus
            // change would leave the sentence that prompted the user to reach for it exposed.
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
            advanceUntilIdle()
            assertFalse(viewModel.incognito.value)

            viewModel.setPrivateMode(true)
            advanceUntilIdle()

            assertEquals(FieldKind.PRIVATE, viewModel.fieldKind.value)
            assertTrue(viewModel.incognito.value)
        }

    @Test
    fun `it survives the end of an input session`() =
        runTest(dispatcher) {
            // The failure direction that matters. The host's flag and the field's type are
            // session-scoped and must be cleared here; the user's switch must not be, or
            // dismissing the keyboard once silently disarms it.
            viewModel.setPrivateMode(true)
            advanceUntilIdle()

            viewModel.onInputFinished()
            advanceUntilIdle()

            assertTrue(viewModel.incognito.value)
            assertEquals(FieldKind.PRIVATE, viewModel.fieldKind.value)
        }

    @Test
    fun `it survives a new field that is not itself private`() =
        runTest(dispatcher) {
            viewModel.setPrivateMode(true)
            advanceUntilIdle()

            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
            advanceUntilIdle()

            assertEquals(FieldKind.PRIVATE, viewModel.fieldKind.value)
            assertTrue(viewModel.incognito.value)
        }

    @Test
    fun `turning it off restores ordinary typing`() =
        runTest(dispatcher) {
            // The other half of every guard in this file. A privacy switch that cannot be
            // switched back leaves a keyboard that has quietly lost its suggestion strip, and
            // nothing about that looks broken enough to report.
            io.mockk.coEvery { engine.getSuggestions("hi") } returns listOf("hit", "his")
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
            viewModel.setPrivateMode(true)
            advanceUntilIdle()

            viewModel.setPrivateMode(false)
            advanceUntilIdle()

            assertFalse(viewModel.incognito.value)
            assertEquals(FieldKind.NORMAL, viewModel.fieldKind.value)

            viewModel.onKeyPressed("h")
            viewModel.onKeyPressed("i")
            advanceUntilIdle()
            // Checked mid-word: finishing the word clears the strip, so asserting after
            // onWordFinished would pass on an empty list whether reads were restored or not.
            assertTrue(viewModel.suggestions.value.contains("hit"))

            viewModel.onWordFinished("hi")
            advanceUntilIdle()
            coVerify(exactly = 1) { engine.learnWord("hi") }
        }

    @Test
    fun `the host flag and the switch are independent`() =
        runTest(dispatcher) {
            // Both raise incognito, and clearing one must not clear the other. Folding them
            // into a single boolean is exactly how a manual switch gets cancelled by the next
            // ordinary field the user taps.
            viewModel.setPrivateMode(true)
            viewModel.onIncognitoChanged(true)
            advanceUntilIdle()
            assertTrue(viewModel.incognito.value)

            viewModel.onIncognitoChanged(false)
            advanceUntilIdle()
            assertTrue("the switch still holds it", viewModel.incognito.value)

            viewModel.setPrivateMode(false)
            advanceUntilIdle()
            assertFalse(viewModel.incognito.value)
        }

    @Test
    fun `a password field is still handled when the switch is off`() =
        runTest(dispatcher) {
            // Regression guard for the rewiring: incognito used to be set by the IME directly
            // and is now derived in the ViewModel. The automatic path has to still work.
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.PASSWORD)
            advanceUntilIdle()

            assertTrue(viewModel.incognito.value)
            assertEquals(FieldKind.PASSWORD, viewModel.fieldKind.value)
        }
}
