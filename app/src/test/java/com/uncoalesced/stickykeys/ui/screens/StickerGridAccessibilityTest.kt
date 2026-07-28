// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every grid cell used to announce the identical string "Sticker", so a screen reader user
 * could neither distinguish two cells nor tell where they were in the grid.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StickerGridAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun sticker(
        id: String,
        favourite: Boolean = false,
    ) = Sticker(
        id = id,
        packId = null,
        categoryId = null,
        isFavourite = favourite,
        createdAt = 0L,
        mimeType = "image/webp",
        // No file on disk, so the item renders its text fallback -- the fallback needs an
        // accessible label just as much as the image path does.
        file = File(""),
        thumbnailFile = File(""),
    )

    private fun setGrid(stickers: List<Sticker>) {
        composeRule.setContent {
            StickyKeysTheme {
                stickers.forEachIndexed { index, s ->
                    StickerGridItem(
                        sticker = s,
                        positionLabel = "Sticker ${index + 1} of ${stickers.size}",
                        onStickerClick = {},
                        onToggleFavourite = {},
                        onLongClick = {},
                    )
                }
            }
        }
    }

    @Test
    fun `each cell announces its own position in the grid`() {
        setGrid(List(3) { sticker("id-$it") })

        composeRule.onNodeWithContentDescription("Sticker 1 of 3").assertExists()
        composeRule.onNodeWithContentDescription("Sticker 2 of 3").assertExists()
        composeRule.onNodeWithContentDescription("Sticker 3 of 3").assertExists()
    }

    @Test
    fun `no cell announces a raw sticker id`() {
        val id = "5f8c1d3e-6b2a-4c9d-8e1f-7a0b3c4d5e6f"
        setGrid(listOf(sticker(id)))

        val leaked =
            composeRule
                .onAllNodes(
                    SemanticsMatcher("has an id in its description") { node ->
                        node.config
                            .getOrNull(SemanticsProperties.ContentDescription)
                            ?.any { it.contains(id) } == true
                    },
                ).fetchSemanticsNodes()

        assertTrue("a 36-character UUID is being read aloud", leaked.isEmpty())
    }

    @Test
    fun `cells are distinguishable from one another`() {
        setGrid(List(4) { sticker("id-$it") })

        val descriptions =
            composeRule
                .onAllNodes(
                    SemanticsMatcher("has a content description") { node ->
                        node.config.getOrNull(SemanticsProperties.ContentDescription) != null
                    },
                ).fetchSemanticsNodes()
                .mapNotNull {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                }.filter { it.startsWith("Sticker ") }

        assertEquals("all four cells must be distinct", 4, descriptions.distinct().size)
    }

    @Test
    fun `favourite state is spoken, not left to a star glyph and a colour`() {
        setGrid(listOf(sticker("id-fav", favourite = true)))
        composeRule.onNodeWithContentDescription("Sticker 1 of 1, favourite").assertExists()

        val toggleState =
            composeRule
                .onNodeWithContentDescription("Favourite")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription)
        assertEquals("On", toggleState)
    }

    @Test
    fun `an unfavourited cell reports the off state`() {
        setGrid(listOf(sticker("id-plain", favourite = false)))
        composeRule.onNodeWithContentDescription("Sticker 1 of 1").assertExists()

        val toggleState =
            composeRule
                .onNodeWithContentDescription("Favourite")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription)
        assertEquals("Off", toggleState)
    }
}
