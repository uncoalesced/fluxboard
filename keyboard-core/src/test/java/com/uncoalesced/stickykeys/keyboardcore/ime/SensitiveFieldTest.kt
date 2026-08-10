// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.text.InputType
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Nothing typed into a password or a PIN may reach the dictionary, the strip or the engine.
 *
 * This is a privacy fix, not a polish item. Before it, `inputType` was read in exactly one
 * place in the whole codebase and the password variations were read nowhere. Incognito was
 * driven only by `IME_FLAG_NO_PERSONALIZED_LEARNING`, a flag the host app sets and which
 * Android's own `TextView` does not set for password fields -- so an alphanumeric password
 * went down the ordinary letter path: every keystroke ran a dictionary lookup whose results
 * were drawn above the keyboard, and the first space afterwards wrote the password into the
 * personal dictionary on disk.
 *
 * For a keyboard whose stated position is zero telemetry, a password persisted to a local
 * database the user cannot see is the same class of failure as sending it somewhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensitiveFieldTest {
    // --- the classifier -------------------------------------------------------------------

    @Test
    fun `a numeric password is a PIN`() {
        assertEquals(
            FieldKind.PIN,
            fieldKindFor(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            ),
        )
    }

    @Test
    fun `all three text password variations are recognised`() {
        listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        ).forEach { variation ->
            assertEquals(
                "variation $variation should be a password",
                FieldKind.PASSWORD,
                fieldKindFor(InputType.TYPE_CLASS_TEXT or variation),
            )
        }
    }

    @Test
    fun `a visible password is still a password`() {
        // The characters being legible on screen says nothing about whether they may be
        // learned. Treating this as ordinary text would leak precisely the passwords of users
        // who pressed "show password".
        val visible =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        assertTrue(fieldKindFor(visible).isSensitive)
    }

    @Test
    fun `ordinary fields are not swept up`() {
        // The guard has to be narrow, or a false positive silently disables learning and
        // suggestions on normal typing and nothing looks broken.
        listOf(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            InputType.TYPE_CLASS_PHONE,
            0,
        ).forEach {
            assertEquals("inputType $it should be normal", FieldKind.NORMAL, fieldKindFor(it))
        }
    }

    @Test
    fun `a plain numeric field is not mistaken for a PIN`() {
        // The distinction the whole split rests on: an amount field and a passcode field are
        // both TYPE_CLASS_NUMBER, and only one of them is a secret.
        assertEquals(FieldKind.NORMAL, fieldKindFor(InputType.TYPE_CLASS_NUMBER))
    }

    // --- what the ViewModel does with it --------------------------------------------------

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
    fun `typing a password produces no dictionary lookup and no suggestions`() =
        runTest(dispatcher) {
            io.mockk.coEvery { engine.getSuggestions(any()) } returns listOf("leaked")
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.PASSWORD)

            "hunter2".forEach { viewModel.onKeyPressed(it.toString()) }
            advanceUntilIdle()

            assertTrue(viewModel.suggestions.value.isEmpty())
            coVerify(exactly = 0) { engine.getSuggestions(any()) }
        }

    @Test
    fun `a password is never sent to the autocorrect engine`() =
        runTest(dispatcher) {
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.PASSWORD)

            assertNull(viewModel.getAutoCorrectionFor("hunter2"))
            advanceUntilIdle()

            coVerify(exactly = 0) { engine.getAutoCorrection(any()) }
        }

    @Test
    fun `the same keystrokes on a normal field still work`() =
        runTest(dispatcher) {
            // The other half of the guard. A privacy fix that also silences ordinary typing
            // would be caught by nothing else here.
            io.mockk.coEvery { engine.getSuggestions("hi") } returns listOf("hit", "his")
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)

            viewModel.onKeyPressed("h")
            viewModel.onKeyPressed("i")
            advanceUntilIdle()

            assertTrue(viewModel.suggestions.value.contains("hit"))
        }

    @Test
    fun `the field kind is republished on every input session`() =
        runTest(dispatcher) {
            // Session-scoped, like incognito. A password field followed by a chat box must not
            // leave the keyboard silent, and a chat box followed by a password field must not
            // leave it learning.
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.PASSWORD)
            assertEquals(FieldKind.PASSWORD, viewModel.fieldKind.value)

            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
            assertEquals(FieldKind.NORMAL, viewModel.fieldKind.value)
        }
}
