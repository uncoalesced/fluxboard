// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.stickercore.domain.model.Category
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StickerIMEViewModel @Inject constructor(
    private val repository: StickerRepository
) : ViewModel() {

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    // Tab 0 is Favourites. Tabs 1..N are Categories.
    val categories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stickersForCurrentTab: StateFlow<List<Sticker>> = combine(
        _selectedTabIndex,
        categories
    ) { tabIndex, catList ->
        Pair(tabIndex, catList)
    }.flatMapLatest { (tabIndex, catList) ->
        if (tabIndex == 0) {
            repository.getFavouriteStickers()
        } else {
            val category = catList.getOrNull(tabIndex - 1)
            if (category != null) {
                repository.getStickersByCategory(category.id)
            } else {
                repository.getFavouriteStickers() // Fallback
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun selectTab(index: Int) {
        _selectedTabIndex.value = index
    }
}
