// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.di.IoDispatcher
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import com.uncoalesced.stickykeys.ui.components.attempt
import com.uncoalesced.stickykeys.ui.components.encodeStickerImage
import com.uncoalesced.stickykeys.ui.components.encodeStickerThumbnail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SaveStickerViewModel
    @Inject
    constructor(
        private val repository: StickerRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        /**
         * Encodes and stores [bitmap]. Both callbacks run on the main thread, because
         * [onComplete] navigates: this coroutine used to run wholly on the IO dispatcher, so
         * Navigation threw from a background thread and every saved sticker crashed the app,
         * and the keyboard along with it, just after the sticker had been written.
         */
        fun saveSticker(
            bitmap: Bitmap,
            onComplete: () -> Unit,
            onFailure: () -> Unit,
        ) {
            viewModelScope.launch {
                val saved =
                    attempt {
                        withContext(ioDispatcher) {
                            val sticker =
                                Sticker(
                                    id = UUID.randomUUID().toString(),
                                    packId = null,
                                    categoryId = null,
                                    isFavourite = false,
                                    createdAt = System.currentTimeMillis(),
                                    mimeType = "image/webp",
                                    file = File(""),
                                    thumbnailFile = File(""),
                                )
                            repository.saveSticker(
                                sticker,
                                encodeStickerImage(bitmap),
                                encodeStickerThumbnail(bitmap),
                            )
                        }
                    }
                if (saved.isSuccess) onComplete() else onFailure()
            }
        }
    }
