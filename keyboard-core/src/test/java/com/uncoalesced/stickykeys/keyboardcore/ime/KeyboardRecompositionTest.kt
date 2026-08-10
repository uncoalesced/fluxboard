// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyGlyph
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.layout.keyGlyph
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `@Stable` and always the same instance, so holding it never itself triggers a
 * recomposition -- a count that goes up means the composable genuinely ran again.
 */
@Stable
private class RecompositionCounter {
    var count: Int = 0
}

/**
 * Deliberately unstable: a plain class with a mutable property and no `@Stable`. Capturing
 * one in a lambda stops the Compose compiler memoizing that lambda, which is exactly what
 * the real call site did by capturing the controller, the ViewModel and the scope.
 */
private class UnstableCapture {
    @Suppress("unused")
    var touched: Int = 0
}

/**
 * Measures how much of the key grid rebuilds when only the suggestion strip changes -- which
 * is what every keystroke does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KeyboardRecompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val defaultRows = KeyboardRows(LayoutManager.buildDefaultLayout().rows)
    private val keyCount = defaultRows.rows.sumOf { it.size }

    /** Mirrors [KeyboardKey]'s parameter list, so it skips under the same conditions. */
    @Composable
    private fun CountingKey(
        keyOutput: String,
        glyph: KeyGlyph,
        hint: String?,
        mode: KeyboardMode,
        background: Color,
        foreground: Color,
        alternates: KeyAlternatesState,
        onKeyPress: (String) -> Unit,
        counter: RecompositionCounter,
    ) {
        counter.count++
        KeyboardKey(
            keyOutput = keyOutput,
            glyph = glyph,
            hint = hint,
            mode = mode,
            background = background,
            foreground = foreground,
            alternates = alternates,
            preferredCellWidthPx = 100f,
            availableWidthPx = 1080f,
            onKeyPress = onKeyPress,
        )
    }

    @Composable
    private fun CountingRows(
        keyRows: KeyboardRows,
        onKeyPress: (String) -> Unit,
        counter: RecompositionCounter,
    ) {
        // Hoisted, exactly as KeyboardRowsView hoists it. Allocating a fresh one per key
        // would hand every key an unstable parameter and defeat the very skipping this test
        // exists to measure -- which is itself the failure mode being guarded against.
        val alternates = remember { KeyAlternatesState() }
        keyRows.rows.forEach { row ->
            row.forEach { keyDef ->
                CountingKey(
                    keyOutput = keyDef.output,
                    glyph = keyGlyph(keyDef.output, keyDef.displayLabel),
                    hint = keyDef.hint,
                    mode = KeyboardMode.LETTERS_LOWER,
                    background = Color.DarkGray,
                    foreground = Color.White,
                    alternates = alternates,
                    onKeyPress = onKeyPress,
                    counter = counter,
                )
            }
        }
    }

    /**
     * @param stableController false reproduces the original `MainIMEView`, which built a new
     *   `object : KeyboardController` on every recomposition. Every key's press lambda
     *   captured that controller, so a fresh instance invalidated the compiler's lambda
     *   memoization and handed each key a parameter that could not compare equal.
     * @return initial key builds, then key builds caused by the keystrokes.
     */
    private fun measureKeystrokes(
        keystrokes: Int,
        stableController: Boolean,
    ): Pair<Int, Int> {
        val counter = RecompositionCounter()
        var suggestions by mutableStateOf(listOf("a"))

        composeRule.setContent {
            StickyKeysTheme {
                val captured =
                    if (stableController) {
                        remember { UnstableCapture() }
                    } else {
                        UnstableCapture()
                    }
                val handler: (String) -> Unit = { _: String -> captured.touched++ }
                Column {
                    // Stands in for the suggestion strip: the only thing that changes.
                    Text(text = suggestions.joinToString())
                    CountingRows(
                        keyRows = defaultRows,
                        onKeyPress = handler,
                        counter = counter,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val initial = counter.count

        repeat(keystrokes) { i ->
            suggestions = listOf("suggestion$i")
            composeRule.waitForIdle()
        }

        return initial to (counter.count - initial)
    }

    @Test
    fun `changing only the suggestion strip recomposes no keys at all`() {
        val (initial, afterKeystrokes) =
            measureKeystrokes(keystrokes = 10, stableController = true)

        assertEquals("first composition should build every key once", keyCount, initial)
        assertEquals(
            "10 keystrokes rebuilt $afterKeystrokes keys; expected 0",
            0,
            afterKeystrokes,
        )
    }

    @Test
    fun `reallocating the controller rebuilds every key on every keystroke`() {
        // The defect, reproduced. This is the measurement that matters: the same 10
        // keystrokes cost 0 key rebuilds with a remembered controller and keyCount * 10
        // without one.
        val (initial, afterKeystrokes) =
            measureKeystrokes(keystrokes = 10, stableController = false)

        assertEquals(keyCount, initial)
        assertEquals(
            "expected the whole keyboard to rebuild per keystroke with a reallocated controller",
            keyCount * 10,
            afterKeystrokes,
        )
    }

    @Test
    fun `the intercepting controller is allocated once, not per recomposition`() {
        val seen = mutableListOf<KeyboardController>()
        var tick by mutableStateOf(0)
        val delegate = NoOpController()

        composeRule.setContent {
            val appModeState = remember { mutableStateOf(AppMode.TYPING) }
            seen += rememberInterceptingController(delegate, appModeState)
            Text(text = "tick $tick")
        }
        composeRule.waitForIdle()

        repeat(20) {
            tick++
            composeRule.waitForIdle()
        }

        assertTrue("controller was never produced", seen.isNotEmpty())
        assertEquals(
            "controller was reallocated across ${seen.size} compositions",
            1,
            seen.distinct().size,
        )
    }

    @Test
    fun `the key press handler is allocated once, not per recomposition`() {
        val seen = mutableListOf<(String) -> Unit>()
        var tick by mutableStateOf(0)
        val viewModel = mockk<TypingViewModel>(relaxed = true)
        val controller = NoOpController()

        composeRule.setContent {
            val modeState = remember { mutableStateOf(KeyboardMode.LETTERS_LOWER) }
            val scope = rememberCoroutineScope()
            seen +=
                rememberKeyPressHandler(
                    keyboardController = controller,
                    typingViewModel = viewModel,
                    coroutineScope = scope,
                    modeState = modeState,
                )
            Text(text = "tick $tick")
        }
        composeRule.waitForIdle()

        repeat(20) {
            tick++
            composeRule.waitForIdle()
        }

        assertEquals(1, seen.distinct().size)
        seen.forEach { assertSame(seen.first(), it) }
    }

    @Test
    fun `the immutable row wrapper compares equal when rebuilt from equal contents`() {
        // Why the wrapper exists: a bare List<List<KeyDefinition>> is inferred unstable, so
        // any composable taking one is marked non-skippable regardless of equality.
        val rebuilt =
            KeyboardRows(
                // copy() rather than a positional rebuild: listing the fields by hand meant
                // adding `hint` to KeyDefinition silently dropped it here, and the test then
                // failed for a reason that had nothing to do with what it measures.
                defaultRows.rows.map { row -> row.map { it.copy() } },
            )
        assertEquals(defaultRows, rebuilt)
    }
}

private class NoOpController : KeyboardController {
    override fun commitText(text: String) = Unit

    override fun textBeforeCursor(maxChars: Int): String = ""

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
