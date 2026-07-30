// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
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
 * Asserts against the real Compose semantics tree -- the same tree TalkBack reads -- rather
 * than against the label function in isolation. A correct label that never reaches the
 * semantics node is still an unusable key.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KeyboardKeySemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setKey(
        output: String,
        mode: KeyboardMode = KeyboardMode.LETTERS_LOWER,
        hint: String? = null,
        onKeyPress: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            StickyKeysTheme {
                KeyboardKey(
                    keyOutput = output,
                    // The real glyph table, not a hand-passed label: these assertions are
                    // about what the keyboard actually announces, and a test that supplied
                    // its own display string could pass while the shipped table regressed.
                    glyph = keyGlyph(output, shift = shiftRenderingFor(mode)),
                    hint = hint,
                    mode = mode,
                    background = Color.DarkGray,
                    foreground = Color.White,
                    alternates = remember { KeyAlternatesState() },
                    alternateCellWidthPx = 100f,
                    onKeyPress = onKeyPress,
                )
            }
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun stateDescriptionOf(description: String): String? =
        composeRule
            .onNodeWithContentDescription(description)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun `the space key is findable and announces as Space`() {
        // The regression: the space bar drew as " " and that blank was the only thing in
        // the semantics tree, so the key could not be found or announced at all.
        setKey(output = "SPACE")
        composeRule.onNodeWithContentDescription("Space").assertExists()
    }

    @Test
    fun `the space key's blank glyph is kept out of the semantics tree`() {
        setKey(output = "SPACE")
        composeRule.onNodeWithContentDescription("Space").assertContentDescriptionEquals("Space")
        composeRule.onNodeWithText(" ").assertDoesNotExist()
    }

    @Test
    fun `the delete key announces as Delete and not as its icon's own description`() {
        // Delete now draws a vector rather than U+232B. An Icon carries its own
        // contentDescription, so the risk moved from "announces a glyph" to "announces
        // Backspace twice" -- the drawable's description must stay cleared.
        setKey(output = "DEL")
        composeRule.onNodeWithContentDescription("Delete").assertExists()
        composeRule.onNodeWithContentDescription("Backspace").assertDoesNotExist()
    }

    @Test
    fun `keys carry the Button role`() {
        setKey(output = "q")
        composeRule.onNodeWithContentDescription("q").assert(hasRole(Role.Button))
    }

    @Test
    fun `keys expose a click action to the accessibility layer`() {
        setKey(output = "q")
        val node = composeRule.onNodeWithContentDescription("q").fetchSemanticsNode()
        assertTrue(
            "key must expose OnClick so a screen reader can activate it",
            node.config.contains(SemanticsActions.OnClick),
        )
    }

    @Test
    fun `activating a key through the semantics tree fires the handler with its own output`() {
        val pressed = mutableListOf<String>()
        setKey(output = "SPACE", onKeyPress = { pressed += it })
        // Deliberately the semantics action rather than performClick: keys are driven by a
        // raw pointer state machine now, and this test is about the path TalkBack uses --
        // which is the OnClick action and never the pointer stream.
        composeRule
            .onNodeWithContentDescription("Space")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("SPACE"), pressed)
    }

    @Test
    fun `shift exposes a distinct state description for each latch position`() {
        // The three latch positions now differ visually too (outline / filled / filled with
        // bar), but that is no help to a screen reader: the state description remains the
        // only signal a non-sighted user gets, so it still has to be distinct three ways.
        var mode by mutableStateOf(KeyboardMode.LETTERS_LOWER)
        composeRule.setContent {
            StickyKeysTheme {
                KeyboardKey(
                    keyOutput = "SHIFT",
                    glyph = keyGlyph("SHIFT", shift = shiftRenderingFor(mode)),
                    hint = null,
                    mode = mode,
                    background = Color.DarkGray,
                    foreground = Color.White,
                    alternates = remember { KeyAlternatesState() },
                    alternateCellWidthPx = 100f,
                    onKeyPress = {},
                )
            }
        }

        val off = stateDescriptionOf("Shift")
        mode = KeyboardMode.LETTERS_UPPER
        composeRule.waitForIdle()
        val on = stateDescriptionOf("Shift")
        mode = KeyboardMode.LETTERS_CAPS_LOCK
        composeRule.waitForIdle()
        val caps = stateDescriptionOf("Shift")

        assertEquals("Off", off)
        assertEquals("Shift on", on)
        assertEquals("Caps lock on", caps)
        assertEquals(3, listOf(off, on, caps).distinct().size)
    }

    @Test
    fun `a plain letter key carries no state description`() {
        setKey(output = "q")
        assertTrue(
            "letter keys should not claim a toggle state",
            stateDescriptionOf("q") == null,
        )
    }
}
