// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Multi-window sizing, exercised at real split-screen window heights via Robolectric
 * qualifiers rather than asserted by eye on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImePanelHeightTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun measuredPanelHeight(): Dp {
        composeRule.setContent {
            Box(modifier = Modifier.testTag("panel").height(rememberImePanelHeight()))
        }
        val node = composeRule.onNodeWithTag("panel").fetchSemanticsNode()
        val density = composeRule.density
        return with(density) { node.size.height.toDp() }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a full-height phone window gets the full preferred panel`() {
        assertEquals(280.dp, measuredPanelHeight())
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `landscape takes its preferred height from values-land, not the portrait value`() {
        // values-land/dimens.xml overrides ime_panel_height to 200dp. Half of a 411dp
        // landscape window is 205dp, so the resource is the binding constraint here -- which
        // is the point: landscape sizing comes from the resource system, not a branch.
        assertEquals(200.dp, measuredPanelHeight())
    }

    @Test
    @Config(qualifiers = "w411dp-h400dp")
    fun `a split-screen pane never gives the panel more than half the window`() {
        // The regression: the clipboard tray asked for a flat 280dp, which in a 400dp-tall
        // pane left roughly 120dp of the host app visible.
        val height = measuredPanelHeight()
        assertTrue("panel took $height of a 400dp window", height <= 200.dp)
        assertEquals(200.dp, height)
    }

    @Test
    @Config(qualifiers = "w411dp-h300dp")
    fun `a very short window still leaves the majority of the pane to the host app`() {
        val height = measuredPanelHeight()
        assertTrue("panel took $height of a 300dp window", height <= 150.dp)
    }

    @Test
    @Config(qualifiers = "w731dp-h411dp")
    fun `landscape is clamped rather than using the portrait height`() {
        val height = measuredPanelHeight()
        assertTrue("panel took $height of a 411dp landscape window", height < 280.dp)
    }

    @Test
    @Config(qualifiers = "w411dp-h200dp")
    fun `an extremely short window keeps the panel usable rather than collapsing it`() {
        // Half of 200dp would be 100dp, below the point where the panel is worth showing.
        val height = measuredPanelHeight()
        assertEquals(120.dp, height)
    }
}
