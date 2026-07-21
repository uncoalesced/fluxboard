// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SaveStickerViewModel @Inject constructor(
    private val repository: StickerRepository
) : ViewModel() {

    fun saveSticker(webpBytes: ByteArray, thumbBytes: ByteArray, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val stickerId = UUID.randomUUID().toString()
            val sticker = Sticker(
                id = stickerId,
                packId = null,
                categoryId = null,
                isFavourite = false,
                createdAt = System.currentTimeMillis(),
                mimeType = "image/webp",
                file = File(""),
                thumbnailFile = File("")
            )
            repository.saveSticker(sticker, webpBytes, thumbBytes)
            onComplete()
        }
    }
}
