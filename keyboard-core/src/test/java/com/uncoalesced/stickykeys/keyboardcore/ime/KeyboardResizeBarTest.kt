// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardResizeBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `resize bar exposes Reset and Done actions`() {
        var resetClicked = false
        var doneClicked = false

        composeRule.setContent {
            StickyKeysTheme {
                KeyboardResizeBar(
                    onHeightDelta = {},
                    onReset = { resetClicked = true },
                    onDone = { doneClicked = true },
                )
            }
        }

        composeRule.onNodeWithText(RESET_LABEL).performClick()
        assertTrue(resetClicked)

        composeRule.onNodeWithText(DONE_LABEL).performClick()
        assertTrue(doneClicked)
    }
}
