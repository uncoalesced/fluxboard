// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.emoji.EmojiGroup
import com.uncoalesced.stickykeys.keyboardcore.emoji.EmojiRepository
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the unified sticker + emoji picker.
 *
 * The sticker half goes through the same [StickerRepository] every other sticker surface
 * uses. There is deliberately no second query path, no cache of its own and no parallel
 * model -- the picker is another reader of the existing repository, the same way the
 * dedicated sticker panel is.
 */
@HiltViewModel
class EmojiPickerViewModel
    @Inject
    constructor(
        private val stickerRepository: StickerRepository,
        private val emojiRepository: EmojiRepository,
        private val keyboardPreferences: KeyboardPreferences,
    ) : ViewModel() {
        private val _recentEmoji = MutableStateFlow(keyboardPreferences.recentEmoji())

        /** What the user has actually sent, most recent first. Drives the first tab. */
        val recentEmoji: StateFlow<List<String>> = _recentEmoji.asStateFlow()
        private val _emojiGroups = MutableStateFlow<List<EmojiGroup>>(emptyList())
        val emojiGroups: StateFlow<List<EmojiGroup>> = _emojiGroups.asStateFlow()

        private val _selectedTab = MutableStateFlow(RECENT_TAB)
        val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

        /**
         * Stickers for the Stickers tab: favourites first, then everything else
         * newest-first.
         *
         * Chosen over "all stickers, category-filtered". A picker opened mid-message is
         * judged on taps-to-the-thing-you-want, and the two strongest signals for that are
         * the explicit favourite flag and recency -- a sticker made minutes ago is usually
         * the reason the picker was opened at all. Category filtering still exists on the
         * dedicated sticker panel, where browsing rather than sending is the point; putting
         * a second row of category chips in here would cost a row of grid space to solve a
         * problem this surface does not have.
         */
        val stickers: StateFlow<List<Sticker>> =
            stickerRepository
                .getAllStickers()
                .map { all ->
                    all.sortedWith(
                        compareByDescending<Sticker> { it.isFavourite }
                            .thenByDescending { it.createdAt },
                    )
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        init {
            // Parsed and font-probed off the main thread once; the repository caches.
            viewModelScope.launch {
                _emojiGroups.value = emojiRepository.groups()
            }
        }

        fun selectTab(index: Int) {
            _selectedTab.value = index
        }

        /**
         * Opens the picker where the emoji key should land it.
         *
         * Recent, unless there is nothing recent yet -- landing a first-time user on an empty
         * grid would make the feature look broken on the one occasion it gets the most
         * scrutiny. In that case the first real emoji group is the honest default.
         */
        fun openAtDefaultTab() {
            _selectedTab.value = if (_recentEmoji.value.isEmpty()) FIRST_EMOJI_TAB else RECENT_TAB
        }

        fun onEmojiUsed(glyph: String) {
            keyboardPreferences.recordEmojiUse(glyph)
            _recentEmoji.value = keyboardPreferences.recentEmoji()
        }

        companion object {
            /** Tab 0 is Recent, tab 1 is Stickers, and the Unicode groups follow. */
            const val RECENT_TAB = 0
            const val STICKERS_TAB_INDEX = 1
            const val FIRST_EMOJI_TAB = 2
        }
    }
