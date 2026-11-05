// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.material3.Scaffold
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The row used to overflow the Scaffold's button slot and run off the start of the screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StickerImportActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun assertFirstButtonIsOnScreen() {
        composeRule.setContent {
            StickyKeysTheme {
                Scaffold(
                    floatingActionButton = { StickerImportActions({}, {}, {}) },
                ) { it.hashCode() }
            }
        }

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val video =
            composeRule
                .onNodeWithContentDescription("Import a video clip")
                .getUnclippedBoundsInRoot()
        val screenshot =
            composeRule
                .onNodeWithContentDescription("Use your latest screenshot")
                .getUnclippedBoundsInRoot()

        assertTrue("left ${video.left}", video.left >= Dp(0f))
        assertTrue("right ${screenshot.right}", screenshot.right <= root.right)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the buttons stay on screen on an ordinary phone`() {
        assertFirstButtonIsOnScreen()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `the buttons stay on screen on the narrowest phone`() {
        assertFirstButtonIsOnScreen()
    }
}
