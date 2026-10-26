// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.KeyKind
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.PressOutcome
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import com.uncoalesced.stickykeys.keyboardcore.layout.keyGlyph
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The per-press log hears about presses exactly when it is enabled, and says nothing at all
 * when it is not. The second half is the release guarantee seen from the keyboard's side: the
 * release recorder reports `enabled = false`, so neither the grid observer nor the per-key
 * report may run against it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PressOutcomeRecordingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingRecorder(
        override val enabled: Boolean,
    ) : UsageRecorder {
        val downs = mutableListOf<Int>()
        val presses = mutableListOf<Pair<KeyKind, PressOutcome>>()

        override fun onTouchDown(
            uptimeMillis: Long,
            pointersDown: Int,
        ) {
            downs += pointersDown
        }

        override fun onPressResolved(
            kind: KeyKind,
            outcome: PressOutcome,
            heldMillis: Long,
        ) {
            presses += kind to outcome
        }

        override fun onSessionStart() = Unit

        override fun onSessionEnd() = Unit

        override fun onKeystroke() = Unit

        override fun onBackspace() = Unit

        override fun onAutocorrectAccepted() = Unit

        override fun onAutocorrectUndone() = Unit

        override fun onModeSwitch() = Unit

        override fun onScrubGesture() = Unit

        override fun shareIntentFile(): Uri? = null

        override fun clear() = Unit
    }

    private val qKey = KeyDefinition(id = "q", output = "q")

    private fun setGrid(
        recorder: UsageRecorder,
        typed: MutableList<String>,
    ) {
        composeRule.setContent {
            StickyKeysTheme {
                CompositionLocalProvider(LocalUsageRecorder provides recorder) {
                    KeyboardRowsView(
                        keyRows = KeyboardRows(listOf(listOf(qKey))),
                        mode = KeyboardMode.LETTERS_LOWER,
                        palette = StickyKeysTheme.colors,
                        hasBackgroundImage = false,
                        onKeyPress = { typed += it },
                    )
                }
            }
        }
    }

    private fun tapQ() {
        composeRule.onNodeWithContentDescription("q").performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a tap is seen at the grid and resolves as a committed letter`() {
        val recorder = RecordingRecorder(enabled = true)
        val typed = mutableListOf<String>()
        setGrid(recorder, typed)

        tapQ()

        assertEquals(listOf("q"), typed)
        assertEquals("one finger down, seen once", listOf(1), recorder.downs)
        assertEquals(listOf(KeyKind.LETTER to PressOutcome.COMMITTED), recorder.presses)
    }

    @Test
    fun `a disabled recorder hears nothing and the key still types`() {
        val recorder = RecordingRecorder(enabled = false)
        val typed = mutableListOf<String>()
        setGrid(recorder, typed)

        tapQ()

        assertEquals(listOf("q"), typed)
        assertTrue(recorder.downs.isEmpty())
        assertTrue(recorder.presses.isEmpty())
    }

    @Test
    fun `a key drawn with no recorder at all still types`() {
        val typed = mutableListOf<String>()
        composeRule.setContent {
            StickyKeysTheme {
                KeyboardKey(
                    keyOutput = "q",
                    glyph = keyGlyph("q", shift = shiftRenderingFor(KeyboardMode.LETTERS_LOWER)),
                    hint = null,
                    mode = KeyboardMode.LETTERS_LOWER,
                    background = Color.DarkGray,
                    foreground = Color.White,
                    alternates = remember { KeyAlternatesState() },
                    preferredCellWidthPx = 100f,
                    availableWidthPx = 1080f,
                    onKeyPress = { typed += it },
                )
            }
        }

        tapQ()

        assertEquals(listOf("q"), typed)
    }
}
