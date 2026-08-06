// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker

/** Height of the tab strip along the top of the picker. */
private val TAB_STRIP_HEIGHT = 40.dp

/** Emoji are drawn by the system font, so the cell only needs to be a comfortable target. */
private val EMOJI_CELL = 44.dp
private val STICKER_CELL = 72.dp

/**
 * One picker for stickers and emoji.
 *
 * Emoji are plain Unicode text drawn by the platform's own emoji font -- no artwork is
 * bundled and nothing is downloaded. Only the categorisation is shipped, because Android
 * exposes no API for it; see [com.uncoalesced.stickykeys.keyboardcore.emoji.EmojiRepository].
 *
 * Stickers are the first tab and come from the existing repository, not a second data path.
 */
@Composable
internal fun EmojiPickerView(
    viewModel: EmojiPickerViewModel,
    fileManager: com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager,
    onEmojiClick: (String) -> Unit,
    onStickerClick: (Sticker) -> Unit,
    onBackToKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups by viewModel.emojiGroups.collectAsState()
    val stickers by viewModel.stickers.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val recent by viewModel.recentEmoji.collectAsState()

    // Recent leads, because that is where the emoji key lands and it is what a user reaching
    // for the picker mid-message almost always wants. Stickers follows, then the emoji groups
    // in the order emoji-test.txt lists them, which is the order every other picker uses.
    val tabLabels =
        remember(groups) { listOf(RECENT_TAB_LABEL, STICKERS_TAB) + groups.map { it.name } }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(StickyKeysTheme.colors.background),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TAB_STRIP_HEIGHT)
                    .background(StickyKeysTheme.colors.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        // The strip is 40dp tall, so the drawn size cannot be 48. This
                        // expands the *touch* area to the minimum without changing the
                        // layout, which is what the guideline actually asks for.
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button, onClick = onBackToKeyboard)
                        .semantics { contentDescription = "Back to keyboard" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_quick_arrow),
                    contentDescription = null,
                    tint = StickyKeysTheme.colors.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(tabLabels.size) { index ->
                    val label = tabLabels[index]
                    val selected = index == selectedTab
                    Box(
                        modifier =
                            Modifier
                                .clickable { viewModel.selectTab(index) }
                                .background(
                                    if (selected) {
                                        StickyKeysTheme.colors.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    RoundedCornerShape(6.dp),
                                ).padding(horizontal = 10.dp, vertical = 6.dp)
                                .semantics {
                                    role = Role.Tab
                                    contentDescription = label
                                    stateDescription = if (selected) "Selected" else "Not selected"
                                },
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            color =
                                if (selected) {
                                    StickyKeysTheme.colors.onPrimary
                                } else {
                                    StickyKeysTheme.colors.onSurfaceVariant
                                },
                            style = StickyKeysTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // The way out, at the strip's trailing edge.
            //
            // The leading arrow was already here and was still reported as "no way back" --
            // a bare chevron next to a scrolling row of category names does not read as
            // "leave this panel", and it sits where a scroll gesture starts. "ABC" is the
            // label every keyboard uses for exactly this, and putting it opposite the arrow
            // means the exit is the one control that never scrolls out of reach.
            Box(
                modifier =
                    Modifier
                        .padding(start = 4.dp, end = 4.dp)
                        .minimumInteractiveComponentSize()
                        .background(
                            StickyKeysTheme.colors.surfaceVariant,
                            RoundedCornerShape(6.dp),
                        ).clickable(role = Role.Button, onClick = onBackToKeyboard)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics { contentDescription = "Back to letters" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ABC_TAB,
                    maxLines = 1,
                    color = StickyKeysTheme.colors.onSurfaceVariant,
                    style = StickyKeysTheme.typography.labelMedium,
                )
            }
        }

        if (selectedTab == EmojiPickerViewModel.RECENT_TAB) {
            if (recent.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Emoji you use will show up here",
                        color = StickyKeysTheme.colors.onSurfaceVariant,
                        style = StickyKeysTheme.typography.labelLarge,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(EMOJI_CELL),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = recent, key = { it }) { glyph ->
                        Box(
                            modifier =
                                Modifier
                                    .aspectRatio(1f)
                                    .clickable(role = Role.Button) { onEmojiClick(glyph) }
                                    .semantics { contentDescription = "Recently used $glyph" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = glyph, fontSize = 24.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        } else if (selectedTab == EmojiPickerViewModel.STICKERS_TAB_INDEX) {
            StickerTabGrid(
                stickers = stickers,
                fileManager = fileManager,
                onStickerClick = onStickerClick,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val group = groups.getOrNull(selectedTab - EmojiPickerViewModel.FIRST_EMOJI_TAB)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(EMOJI_CELL),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = group?.emoji.orEmpty(),
                    key = { it.glyph },
                ) { emoji ->
                    Box(
                        modifier =
                            Modifier
                                .aspectRatio(1f)
                                .clickable(role = Role.Button) { onEmojiClick(emoji.glyph) }
                                .semantics { contentDescription = emoji.name },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emoji.glyph,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private const val STICKERS_TAB = "Stickers"

/** The first tab, and where the emoji key lands. */
private const val RECENT_TAB_LABEL = "Recent"

/** The universal label for "back to the letters", on every keyboard that has this panel. */
private const val ABC_TAB = "ABC"

@Composable
private fun StickerTabGrid(
    stickers: List<Sticker>,
    fileManager: com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager,
    onStickerClick: (Sticker) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stickers.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No stickers yet",
                color = StickyKeysTheme.colors.onSurfaceVariant,
                style = StickyKeysTheme.typography.labelLarge,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(STICKER_CELL),
        modifier = modifier,
    ) {
        itemsIndexed(items = stickers, key = { _, s -> s.id }) { index, sticker ->
            // The existing thumbnail composable, not a second decoder. It already does the
            // off-main-thread decode keyed on sticker id.
            StickerThumbnail(
                sticker = sticker,
                fileManager = fileManager,
                // A raw UUID is not a description. Favourites lead this grid, so whether an
                // item is one is the only thing about its position worth announcing.
                contentDescription =
                    if (sticker.isFavourite) {
                        "Favourite sticker ${index + 1} of ${stickers.size}"
                    } else {
                        "Sticker ${index + 1} of ${stickers.size}"
                    },
                onClick = { onStickerClick(sticker) },
            )
        }
    }
}
