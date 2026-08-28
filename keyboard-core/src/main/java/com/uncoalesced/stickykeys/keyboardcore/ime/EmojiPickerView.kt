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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.keyboardcore.emoji.EMOTICONS
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyDefinition
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker

/** Height of the tab strip along the top of the picker. */
private val TAB_STRIP_HEIGHT = 40.dp

/** Emoji are drawn by the system font, so the cell only needs to be a comfortable target. */
private val EMOJI_CELL = 44.dp

/** Wide enough for the longest kaomoji in [EMOTICONS] at 13sp without clipping it. */
private val EMOTICON_CELL = 112.dp

private val EMOTICON_ROW = 40.dp
private val STICKER_CELL = 72.dp

/** Matches one key row, so the picker's bottom edge lines up with the keyboard's. */
private val EMOJI_ACTION_BAR_HEIGHT = 52.dp

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
    /**
     * Committed as plain text and not recorded anywhere, which is what separates it from
     * [onEmojiClick]: an emoticon is characters the keyboard could already type, so there is
     * nothing about it for the Recent grid to be a shortcut to.
     */
    onEmoticonClick: (String) -> Unit,
    onStickerClick: (Sticker) -> Unit,
    onBackToKeyboard: () -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups by viewModel.emojiGroups.collectAsState()
    val stickers by viewModel.stickers.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val recent by viewModel.recentEmoji.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val filtered by viewModel.filteredEmojiGroups.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    // Remembered instances, not fresh ones per recomposition: the gesture machine
    // writes into them and a reallocated holder loses the press mid-gesture.
    val emojiBackspacePressed = remember { mutableStateOf(false) }
    val emojiBackspaceAlternates = remember { KeyAlternatesState() }

    // Recent leads, because that is where the emoji key lands and it is what a user reaching
    // for the picker mid-message almost always wants. Stickers follows, then the emoji groups
    // in the order emoji-test.txt lists them, which is the order every other picker uses.
    val tabLabels =
        remember(groups) {
            listOf(RECENT_TAB_LABEL, STICKERS_TAB, EMOTICONS_TAB_LABEL) + groups.map { it.name }
        }

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
                    painter = painterResource(R.drawable.ic_key_arrow_back),
                    contentDescription = null,
                    tint = StickyKeysTheme.colors.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (searchActive) {
                // The query, drawn rather than edited. A focusable text field cannot work
                // here: this *is* the keyboard, so there is nothing to type into it. The
                // letter pad below feeds this string directly, which also keeps the whole
                // feature off the typing path -- no autocorrect, no glide, no learning, and
                // nothing that could reach the host editor.
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .background(
                                StickyKeysTheme.colors.surfaceVariant,
                                StickyKeysTheme.shapes.small,
                            ).padding(horizontal = 10.dp, vertical = 6.dp)
                            .semantics {
                                contentDescription =
                                    if (query.isEmpty()) SEARCH_HINT else "Searching $query"
                            },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = query.ifEmpty { SEARCH_HINT },
                        maxLines = 1,
                        color =
                            if (query.isEmpty()) {
                                StickyKeysTheme.colors.onSurfaceVariant
                            } else {
                                StickyKeysTheme.colors.onSurface
                            },
                        style = StickyKeysTheme.typography.labelMedium,
                    )
                }
            } else {
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
                                        StickyKeysTheme.shapes.small,
                                    ).padding(horizontal = 10.dp, vertical = 6.dp)
                                    .semantics {
                                        role = Role.Tab
                                        contentDescription = label
                                        stateDescription =
                                            if (selected) "Selected" else "Not selected"
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
            }

            // Search toggle, between the categories and the way out.
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button) {
                            searchActive = !searchActive
                            if (!searchActive) viewModel.onSearchQueryChanged("")
                        }.semantics {
                            contentDescription =
                                if (searchActive) "Close emoji search" else "Search emoji"
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (searchActive) {
                                R.drawable.ic_key_search_close
                            } else {
                                R.drawable.ic_key_search
                            },
                        ),
                    contentDescription = null,
                    tint = StickyKeysTheme.colors.onSurface,
                    modifier = Modifier.size(18.dp),
                )
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
                            StickyKeysTheme.shapes.small,
                        ).clickable(role = Role.Button, onClick = onBackToKeyboard)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics { contentDescription = "Back to letters" },
                contentAlignment = Alignment.Center,
            ) {
                // Arrow *and* word. "ABC" alone sits in a row of category words and reads as
                // one more category; the arrow is what makes it an exit at a glance. Reported
                // twice as a missing back button while both this and the leading arrow were
                // already here, which is a discoverability failure rather than a missing one.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_key_arrow_back),
                        contentDescription = null,
                        tint = StickyKeysTheme.colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = ABC_TAB,
                        maxLines = 1,
                        color = StickyKeysTheme.colors.onSurfaceVariant,
                        style = StickyKeysTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (searchActive) {
            val results = filtered.firstOrNull()?.emoji.orEmpty()
            if (results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (query.isEmpty()) SEARCH_PROMPT else "Nothing matches $query",
                        color = StickyKeysTheme.colors.onSurfaceVariant,
                        style = StickyKeysTheme.typography.labelLarge,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(EMOJI_CELL),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(items = results, key = { it.glyph }) { emoji ->
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
            EmojiSearchPad(
                onLetter = { viewModel.onSearchQueryChanged(query + it) },
                onDelete = { viewModel.onSearchQueryChanged(query.dropLast(1)) },
            )
        } else if (selectedTab == EmojiPickerViewModel.RECENT_TAB) {
            if (recent.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Emoji you use will show up here",
                        color = StickyKeysTheme.colors.onSurfaceVariant,
                        style = StickyKeysTheme.typography.labelLarge,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(EMOJI_CELL),
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
        } else if (selectedTab == EmojiPickerViewModel.EMOTICONS_TAB) {
            // Wider cells than the emoji grid and not square: an emoticon is a short string
            // rather than one glyph, so a square cell sized for a single character would clip
            // every kaomoji in the list. Nothing here is recorded into Recent -- that grid is
            // the emoji the user sent, and mixing text into it would make the emoji key land
            // on a mixture of the two.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(EMOTICON_CELL),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(items = EMOTICONS, key = { it }) { emoticon ->
                    Box(
                        modifier =
                            Modifier
                                .height(EMOTICON_ROW)
                                .clickable(role = Role.Button) { onEmoticonClick(emoticon) }
                                .semantics { contentDescription = "Emoticon $emoticon" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emoticon,
                            fontSize = 13.sp,
                            maxLines = 1,
                            color = StickyKeysTheme.colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else if (selectedTab == EmojiPickerViewModel.STICKERS_TAB_INDEX) {
            StickerTabGrid(
                stickers = stickers,
                fileManager = fileManager,
                onStickerClick = onStickerClick,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            val group = groups.getOrNull(selectedTab - EmojiPickerViewModel.FIRST_EMOJI_TAB)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(EMOJI_CELL),
                modifier = Modifier.fillMaxWidth().weight(1f),
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

        // Backspace, in the place the keyboard puts it: bottom row, hard against the right
        // edge. It was first put in the tab strip, which is where it fits the layout rather
        // than where a thumb already goes -- and a delete key that moves depending on which
        // panel is open is one the user has to hunt for every time.
        //
        // Repeats on hold through the same gesture machine the letter keyboard's backspace
        // uses. A run of emoji is exactly what somebody wants to clear in one gesture, and a
        // key that deletes one per tap here while repeating one screen away reads as a bug.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(EMOJI_ACTION_BAR_HEIGHT)
                    .background(StickyKeysTheme.colors.surface),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(end = 12.dp)
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .background(
                            StickyKeysTheme.colors.surfaceVariant,
                            StickyKeysTheme.shapes.small,
                        ).keyGestures(
                            keyOutput = "DEL",
                            longPress = LongPress.Repeat,
                            alternates = emojiBackspaceAlternates,
                            cellWidthPx = 1f,
                            keyBounds = { Rect.Zero },
                            onCommit = { onBackspace() },
                            pressed = emojiBackspacePressed,
                        ).semantics {
                            role = Role.Button
                            contentDescription = "Backspace"
                            onClick(label = "Delete") {
                                onBackspace()
                                true
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_key_backspace),
                    contentDescription = null,
                    tint = StickyKeysTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * The keyboard that drives emoji search.
 *
 * This used to be a private keypad of its own: plain boxes with a text "del", ignoring the
 * active theme's key shapes, haze, sizing and press feedback. It looked like a different
 * product bolted into the middle of the keyboard, which is what it was.
 *
 * It now draws through [KeyboardRowsView], the same composable the typing keyboard uses, so
 * the keys are the user's keys. That was previously argued against on the grounds that it
 * would mean "teaching the typing path about a second destination for every keystroke". It
 * does not: [KeyboardRowsView] already takes its handler as a parameter, so this is a second
 * *caller* of a shared view rather than a second destination inside one path. Nothing in
 * `TypingKeyboardView` or `StickyKeysIME` is touched, and no keystroke here can reach the
 * editor -- the handler below only ever calls back into the picker's own query.
 *
 * The letters are the shipped a-z rather than the user's remapped layout, and that part of
 * the original reasoning still holds: search matches the English names in
 * `emoji_data.txt`, so a layout that moves or replaces the Latin letters would make the
 * feature unusable rather than personal.
 *
 * Deliberately no shift, no symbols page and no enter. Emoji names are lower-case English,
 * so each of those would be a key that draws like every other key and does nothing when
 * pressed, which is worse than not drawing it. Backspace is a real repeat key, so holding it
 * clears the query the way holding it anywhere else deletes.
 */
@Composable
private fun EmojiSearchPad(
    onLetter: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted and remembered for the same reason the typing keyboard hoists its handler: a
    // lambda rebuilt per recomposition is a changed parameter on every key, which takes the
    // whole grid out of skipping.
    val letter = rememberUpdatedState(onLetter)
    val delete = rememberUpdatedState(onDelete)
    val onKeyPress =
        remember {
            { output: String ->
                when (output) {
                    "DEL" -> delete.value()
                    "SPACE" -> letter.value(" ")
                    else -> letter.value(output)
                }
            }
        }
    Box(modifier = modifier.fillMaxWidth().height(SEARCH_KEYBOARD_HEIGHT)) {
        KeyboardRowsView(
            keyRows = SEARCH_KEYBOARD_ROWS,
            mode = KeyboardMode.LETTERS_LOWER,
            palette = StickyKeysTheme.colors,
            hasBackgroundImage = false,
            onKeyPress = onKeyPress,
            modifier = Modifier.fillMaxSize(),
            // No glide. A swipe across a search pad has no word to decode against, and the
            // decoder is gated on the typing field's privacy state, which does not apply here.
            glide = null,
        )
    }
}

/**
 * The search keyboard's own rows: the shipped letters, a backspace, and a space bar.
 *
 * Built here rather than taken from `KeyboardLayouts.letterRows` because that carries the
 * action row, whose symbols page, emoji key and enter key have nothing to do in a search
 * box. No hints and no alternates, so no letter here offers a corner symbol that the query
 * has no use for.
 */
private val SEARCH_KEYBOARD_ROWS =
    KeyboardRows(
        listOf(
            "qwertyuiop".map { KeyDefinition(id = "search_$it", output = it.toString()) },
            "asdfghjkl".map { KeyDefinition(id = "search_$it", output = it.toString()) },
            "zxcvbnm".map { KeyDefinition(id = "search_$it", output = it.toString()) } +
                KeyDefinition(id = "search_del", output = "DEL", weight = 1.5f),
            listOf(KeyDefinition(id = "search_space", output = "SPACE", weight = 10f)),
        ),
    )

/** Four rows at roughly the height the typing keyboard gives its own. */
private val SEARCH_KEYBOARD_HEIGHT = 176.dp

private const val SEARCH_HINT = "Search emoji"
private const val SEARCH_PROMPT = "Type a name, like heart or cat"

private const val STICKERS_TAB = "Stickers"

/** The first tab, and where the emoji key lands. */
private const val RECENT_TAB_LABEL = "Recent"

// The tab holds text emoticons, and the label deliberately does not say so: "Emojis" is
// what the tester asked for it to read, matching the keyboard he compared it against.
// The constant and the data keep the accurate name so the code still describes itself.
private const val EMOTICONS_TAB_LABEL = "Emojis"

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
