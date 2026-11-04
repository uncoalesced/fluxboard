// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.stickercore.domain.model.Category
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import com.uncoalesced.stickykeys.ui.components.attempt
import com.uncoalesced.stickykeys.ui.components.decodeBitmap
import com.uncoalesced.stickykeys.ui.components.encodeStickerImage
import com.uncoalesced.stickykeys.ui.components.encodeStickerThumbnail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed interface TabFilter {
    data object All : TabFilter

    data object Favourites : TabFilter

    data class CategoryFilter(
        val category: Category,
    ) : TabFilter
}

sealed interface StickersUiState {
    data object Loading : StickersUiState

    data class Success(
        val stickers: List<Sticker>,
        val categories: List<Category>,
        val currentTab: TabFilter,
    ) : StickersUiState
}

@HiltViewModel
class StickersViewModel
    @Inject
    constructor(
        private val repository: StickerRepository,
    ) : ViewModel() {
        private val _selectedTab = MutableStateFlow<TabFilter>(TabFilter.All)
        val selectedTab: StateFlow<TabFilter> = _selectedTab.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        private val stickersFlow: Flow<List<Sticker>> =
            _selectedTab.flatMapLatest { tab ->
                when (tab) {
                    is TabFilter.All -> repository.getAllStickers()
                    is TabFilter.Favourites -> repository.getFavouriteStickers()
                    is TabFilter.CategoryFilter -> repository.getStickersByCategory(tab.category.id)
                }
            }

        val uiState: StateFlow<StickersUiState> =
            combine(
                stickersFlow,
                repository.getAllCategories(),
                _selectedTab,
            ) { stickers, categories, currentTab ->
                StickersUiState.Success(
                    stickers = stickers,
                    categories = categories,
                    currentTab = currentTab,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                StickersUiState.Loading,
            )

        private val _isBatchImporting = MutableStateFlow(false)
        val isBatchImporting: StateFlow<Boolean> = _isBatchImporting.asStateFlow()

        private val _batchImportFailures = MutableStateFlow(0)

        /** How many images the last batch could not import; 0 once acknowledged. */
        val batchImportFailures: StateFlow<Int> = _batchImportFailures.asStateFlow()

        fun clearBatchImportFailures() {
            _batchImportFailures.value = 0
        }

        fun batchImportStickers(
            uriStrings: List<String>,
            context: android.content.Context,
        ) {
            if (uriStrings.isEmpty()) return
            viewModelScope.launch(Dispatchers.IO) {
                _isBatchImporting.value = true
                val failures =
                    uriStrings.count { uriStr ->
                        attempt {
                            // Imported as-is. v1 has no automatic background removal --
                            // use the single-image flow and the manual eraser for cutouts.
                            val bitmap =
                                checkNotNull(context.decodeBitmap(uriStr)) {
                                    "Cannot decode $uriStr"
                                }
                            repository.saveSticker(
                                Sticker(
                                    id = UUID.randomUUID().toString(),
                                    packId = null,
                                    categoryId = null,
                                    isFavourite = false,
                                    createdAt = System.currentTimeMillis(),
                                    mimeType = "image/webp",
                                    file = File(""),
                                    thumbnailFile = File(""),
                                ),
                                encodeStickerImage(bitmap),
                                encodeStickerThumbnail(bitmap),
                            )
                        }.isFailure
                    }
                _isBatchImporting.value = false
                _batchImportFailures.value = failures
            }
        }

        /** A database write that fails leaves the library as it was rather than crashing. */
        private fun launchWrite(write: suspend () -> Unit) {
            viewModelScope.launch { attempt { write() } }
        }

        fun selectTab(tab: TabFilter) {
            _selectedTab.value = tab
        }

        fun addCategory(name: String) {
            if (name.isBlank()) return
            launchWrite {
                val newCategory =
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        sortOrder = System.currentTimeMillis().toInt(),
                    )
                repository.saveCategory(newCategory)
            }
        }

        fun deleteCategory(id: String) {
            launchWrite {
                repository.deleteCategory(id)
                if (_selectedTab.value is TabFilter.CategoryFilter &&
                    (_selectedTab.value as TabFilter.CategoryFilter).category.id == id
                ) {
                    _selectedTab.value = TabFilter.All
                }
            }
        }

        fun toggleFavourite(stickerId: String) {
            launchWrite {
                repository.toggleFavourite(stickerId)
            }
        }

        fun assignCategory(
            sticker: Sticker,
            categoryId: String?,
        ) {
            launchWrite {
                repository.updateStickerMetadata(sticker.copy(categoryId = categoryId))
            }
        }

        fun deleteSticker(stickerId: String) {
            launchWrite {
                repository.deleteSticker(stickerId)
            }
        }
    }
