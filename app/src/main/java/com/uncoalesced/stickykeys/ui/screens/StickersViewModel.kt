// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.stickercore.domain.model.Category
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import com.uncoalesced.stickykeys.ui.components.decodeBitmap
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
import java.io.ByteArrayOutputStream
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

        fun batchImportStickers(
            uriStrings: List<String>,
            context: android.content.Context,
        ) {
            if (uriStrings.isEmpty()) return
            viewModelScope.launch(Dispatchers.IO) {
                _isBatchImporting.value = true

                uriStrings.forEach { uriStr ->
                    try {
                        val finalBmp = context.decodeBitmap(uriStr)
                        if (finalBmp != null) {
                            // Imported as-is. v1 has no automatic background removal --
                            // use the single-image flow and the manual eraser for cutouts.
                            val stickerId = UUID.randomUUID().toString()
                            val webpBytes =
                                ByteArrayOutputStream()
                                    .apply {
                                        if (android.os.Build.VERSION.SDK_INT >=
                                            android.os.Build.VERSION_CODES.R
                                        ) {
                                            finalBmp.compress(
                                                Bitmap.CompressFormat.WEBP_LOSSLESS,
                                                100,
                                                this,
                                            )
                                        } else {
                                            @Suppress("DEPRECATION")
                                            finalBmp.compress(
                                                Bitmap.CompressFormat.WEBP,
                                                100,
                                                this,
                                            )
                                        }
                                    }.toByteArray()

                            val thumbBytes =
                                ByteArrayOutputStream()
                                    .apply {
                                        val thumbBmp =
                                            Bitmap.createScaledBitmap(
                                                finalBmp,
                                                256,
                                                256,
                                                true,
                                            )
                                        if (android.os.Build.VERSION.SDK_INT >=
                                            android.os.Build.VERSION_CODES.R
                                        ) {
                                            thumbBmp.compress(
                                                Bitmap.CompressFormat.WEBP_LOSSY,
                                                80,
                                                this,
                                            )
                                        } else {
                                            @Suppress("DEPRECATION")
                                            thumbBmp.compress(
                                                Bitmap.CompressFormat.WEBP,
                                                80,
                                                this,
                                            )
                                        }
                                    }.toByteArray()

                            val sticker =
                                Sticker(
                                    id = stickerId,
                                    packId = null,
                                    categoryId = null,
                                    isFavourite = false,
                                    createdAt = System.currentTimeMillis(),
                                    mimeType = "image/webp",
                                    file = File(""),
                                    thumbnailFile = File(""),
                                )
                            repository.saveSticker(sticker, webpBytes, thumbBytes)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                _isBatchImporting.value = false
            }
        }

        fun selectTab(tab: TabFilter) {
            _selectedTab.value = tab
        }

        fun addCategory(name: String) {
            if (name.isBlank()) return
            viewModelScope.launch {
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
            viewModelScope.launch {
                repository.deleteCategory(id)
                if (_selectedTab.value is TabFilter.CategoryFilter &&
                    (_selectedTab.value as TabFilter.CategoryFilter).category.id == id
                ) {
                    _selectedTab.value = TabFilter.All
                }
            }
        }

        fun toggleFavourite(stickerId: String) {
            viewModelScope.launch {
                repository.toggleFavourite(stickerId)
            }
        }

        fun assignCategory(
            sticker: Sticker,
            categoryId: String?,
        ) {
            viewModelScope.launch {
                repository.updateStickerMetadata(sticker.copy(categoryId = categoryId))
            }
        }

        fun deleteSticker(stickerId: String) {
            viewModelScope.launch {
                repository.deleteSticker(stickerId)
            }
        }
    }
