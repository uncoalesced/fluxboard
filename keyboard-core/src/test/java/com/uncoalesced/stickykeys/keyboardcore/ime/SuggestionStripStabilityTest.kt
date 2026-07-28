// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.darkStickyKeysColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The IME window is WRAP_CONTENT, so anything that changes the content height moves the host
 * app's screen. Showing the suggestion strip only when it had something in it did exactly
 * that, once per completed word.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SuggestionStripStabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val rows = KeyboardRows(LayoutManager.buildDefaultLayout().rows)

    /**
     * Lays the keyboard out the way `TypingKeyboardView` does: a fixed-height panel with an
     * always-present strip above weighted key rows.
     *
     * @param conditionalStrip true reproduces the original behaviour, where the strip was
     *   omitted entirely whenever there was nothing to show.
     */
    private fun measureHeights(
        conditionalStrip: Boolean,
        suggestionSets: List<List<String>>,
    ): List<Int> {
        var suggestions by mutableStateOf(suggestionSets.first())
        val measured = mutableListOf<Int>()

        composeRule.setContent {
            StickyKeysTheme {
                val panelHeight = rememberImePanelHeight()
                val stripHeight = dimensionResource(R.dimen.suggestion_strip_height)
                if (conditionalStrip) {
                    // The original layout: wrap-content column, fixed-height key rows, and a
                    // strip that disappears entirely when there is nothing to suggest.
                    Column(
                        modifier =
                            Modifier
                                .testTag(
                                    "keyboard",
                                ).fillMaxWidth()
                                .height(IntrinsicSize.Min),
                    ) {
                        if (suggestions.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth().height(stripHeight)) {
                                suggestions.forEach { Text(it) }
                            }
                        }
                        rows.rows.forEach { _ ->
                            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {}
                        }
                    }
                } else {
                    // The current layout: one fixed panel height, strip always present, rows
                    // sharing whatever is left.
                    Column(
                        modifier = Modifier.testTag("keyboard").fillMaxWidth().height(panelHeight),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().height(stripHeight)) {
                            suggestions.forEach { Text(it) }
                        }
                        KeyboardRowsView(
                            keyRows = rows,
                            mode = KeyboardMode.LETTERS_LOWER,
                            palette = darkStickyKeysColors(),
                            hasBackgroundImage = false,
                            onKeyPress = {},
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        suggestionSets.forEach { set ->
            suggestions = set
            composeRule.waitForIdle()
            measured +=
                composeRule
                    .onNodeWithTag("keyboard")
                    .fetchSemanticsNode()
                    .size.height
        }
        return measured
    }

    private val typingSequence =
        listOf(
            listOf("the", "there", "these"),
            emptyList(),
            listOf("quick"),
            emptyList(),
            listOf("brown", "brought"),
            emptyList(),
        )

    @Test
    fun `the keyboard height never changes as suggestions come and go`() {
        val heights = measureHeights(conditionalStrip = false, suggestionSets = typingSequence)

        assertEquals(
            "keyboard height varied across a typing sequence: $heights",
            1,
            heights.distinct().size,
        )
    }

    @Test
    fun `a conditional strip reproduces the window jumping`() {
        // The defect: the same sequence moves the window by the strip's height each time a
        // word completes and the suggestions empty out.
        val heights = measureHeights(conditionalStrip = true, suggestionSets = typingSequence)

        assertEquals(
            "expected exactly two distinct heights (with and without the strip), got $heights",
            2,
            heights.distinct().size,
        )
    }
}
