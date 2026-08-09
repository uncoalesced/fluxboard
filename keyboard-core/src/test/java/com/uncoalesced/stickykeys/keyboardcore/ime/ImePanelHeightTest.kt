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

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the number row brings its own height instead of taking it from the letters`() {
        // The reported problem: turning the digit row on divided the same fixed height across
        // five rows instead of four, so every key lost a fifth of its height. The panel has
        // to grow by the row's own height for the letters to keep theirs -- 280 + 48.
        assertEquals(328.dp, measuredPanelHeightWith(ImePanelMetrics(showNumberRow = true)))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the panel keeps its shipped height when the number row is off`() {
        assertEquals(280.dp, measuredPanelHeightWith(ImePanelMetrics(showNumberRow = false)))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the height preference can shrink the panel`() {
        assertEquals(196.dp, measuredPanelHeightWith(ImePanelMetrics(heightScale = 0.7f)))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the height preference can grow the panel`() {
        assertEquals(420.dp, measuredPanelHeightWith(ImePanelMetrics(heightScale = 1.5f)))
    }

    @Test
    fun `raising the slider is not silently clamped away`() {
        // The reason the window cap is not simply a constant. On a normal phone window, the
        // largest thing the slider can ask for -- full scale with the number row on -- has to
        // actually be delivered, or the control stops responding partway along its travel and
        // looks broken.
        val request = (280.dp + 48.dp) * 1.5f
        assertEquals(request, panelHeightFor(request, 891.dp, heightScale = 1.5f))
    }

    @Test
    fun `a user who never touched the slider is unaffected by the raised ceiling`() {
        // The split-screen protection is load-bearing and was measured on device. Scaling the
        // cap must not weaken it for the default configuration.
        assertEquals(150.dp, panelHeightFor(280.dp, 300.dp, heightScale = 1f))
        assertEquals(200.dp, panelHeightFor(280.dp, 400.dp, heightScale = 1f))
    }

    @Test
    fun `choosing a smaller keyboard does not tighten the clamp`() {
        // Scaling the cap by anything below 1 would clamp a request that was already small,
        // which would make short windows behave differently for no reason.
        assertEquals(196.dp, panelHeightFor(196.dp, 891.dp, heightScale = 0.7f))
    }

    private fun measuredPanelHeightWith(metrics: ImePanelMetrics): Dp {
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalImePanelMetrics provides metrics,
            ) {
                Box(modifier = Modifier.testTag("panel").height(rememberImePanelHeight()))
            }
        }
        val node = composeRule.onNodeWithTag("panel").fetchSemanticsNode()
        return with(composeRule.density) { node.size.height.toDp() }
    }
}
