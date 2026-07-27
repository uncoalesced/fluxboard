// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.test.performClick
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
        display: String,
        mode: KeyboardMode = KeyboardMode.LETTERS_LOWER,
        onPress: () -> Unit = {},
    ) {
        composeRule.setContent {
            StickyKeysTheme {
                KeyboardKey(
                    keyOutput = output,
                    displayLabel = display,
                    mode = mode,
                    background = Color.DarkGray,
                    foreground = Color.White,
                    onPress = onPress,
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
        setKey(output = "SPACE", display = " ")
        composeRule.onNodeWithContentDescription("Space").assertExists()
    }

    @Test
    fun `the space key's blank glyph is kept out of the semantics tree`() {
        setKey(output = "SPACE", display = " ")
        composeRule.onNodeWithContentDescription("Space").assertContentDescriptionEquals("Space")
        composeRule.onNodeWithText(" ").assertDoesNotExist()
    }

    @Test
    fun `the delete key announces as Delete and not as its glyph`() {
        setKey(output = "DEL", display = "⌫")
        composeRule.onNodeWithContentDescription("Delete").assertExists()
        composeRule.onNodeWithText("⌫").assertDoesNotExist()
    }

    @Test
    fun `keys carry the Button role`() {
        setKey(output = "q", display = "q")
        composeRule.onNodeWithContentDescription("q").assert(hasRole(Role.Button))
    }

    @Test
    fun `keys expose a click action to the accessibility layer`() {
        setKey(output = "q", display = "q")
        val node = composeRule.onNodeWithContentDescription("q").fetchSemanticsNode()
        assertTrue(
            "key must expose OnClick so a screen reader can activate it",
            node.config.contains(SemanticsActions.OnClick),
        )
    }

    @Test
    fun `activating a key through the semantics tree fires the handler`() {
        var pressed = 0
        setKey(output = "SPACE", display = " ", onPress = { pressed++ })
        composeRule.onNodeWithContentDescription("Space").performClick()
        assertTrue("expected the press handler to run, got $pressed", pressed == 1)
    }

    @Test
    fun `shift exposes a distinct state description for each latch position`() {
        // Shift, caps lock and off render pixel-identically, so the state description is
        // the only signal telling a screen reader user which position they are in.
        var mode by mutableStateOf(KeyboardMode.LETTERS_LOWER)
        composeRule.setContent {
            StickyKeysTheme {
                KeyboardKey(
                    keyOutput = "SHIFT",
                    displayLabel = "⇧",
                    mode = mode,
                    background = Color.DarkGray,
                    foreground = Color.White,
                    onPress = {},
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
        setKey(output = "q", display = "q")
        assertTrue(
            "letter keys should not claim a toggle state",
            stateDescriptionOf("q") == null,
        )
    }
}
