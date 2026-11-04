// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.edit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.di.IoDispatcher
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import com.uncoalesced.stickykeys.ui.components.attempt
import com.uncoalesced.stickykeys.ui.components.decodeBitmap
import com.uncoalesced.stickykeys.ui.components.encodeStickerImage
import com.uncoalesced.stickykeys.ui.components.encodeStickerThumbnail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed interface EditStickerUiState {
    data object Loading : EditStickerUiState

    data class Success(
        val sticker: Sticker,
        val currentUriString: String,
    ) : EditStickerUiState

    data class Error(
        val message: String,
    ) : EditStickerUiState
}

@HiltViewModel
class EditStickerViewModel
    @Inject
    constructor(
        private val repository: StickerRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<EditStickerUiState>(EditStickerUiState.Loading)
        val uiState: StateFlow<EditStickerUiState> = _uiState.asStateFlow()

        fun loadSticker(stickerId: String) {
            viewModelScope.launch {
                val sticker =
                    attempt {
                        withContext(ioDispatcher) {
                            repository.getStickerById(stickerId)?.takeIf { it.file.exists() }
                        }
                    }.getOrNull()
                _uiState.value =
                    if (sticker != null) {
                        EditStickerUiState.Success(
                            sticker = sticker,
                            currentUriString = Uri.fromFile(sticker.file).toString(),
                        )
                    } else {
                        EditStickerUiState.Error("Sticker not found")
                    }
            }
        }

        fun updateCurrentUri(newUriString: String) {
            val state = _uiState.value
            if (state is EditStickerUiState.Success) {
                _uiState.value = state.copy(currentUriString = newUriString)
            }
        }

        /**
         * Replaces the sticker with the edited image, which is always a static WebP. The recorded
         * type changes with it, because an edited GIF would otherwise stay indexed as a GIF.
         */
        fun overwriteSticker(
            context: Context,
            onComplete: () -> Unit,
            onFailure: () -> Unit,
        ) {
            val state = _uiState.value
            if (state !is EditStickerUiState.Success) return
            saveEdit(context, state, onComplete, onFailure) { image, thumbnail ->
                repository.updateStickerData(
                    state.sticker.copy(mimeType = EDITED_MIME_TYPE),
                    image,
                    thumbnail,
                )
            }
        }

        fun saveAsNewSticker(
            context: Context,
            onComplete: () -> Unit,
            onFailure: () -> Unit,
        ) {
            val state = _uiState.value
            if (state !is EditStickerUiState.Success) return
            saveEdit(context, state, onComplete, onFailure) { image, thumbnail ->
                repository.saveSticker(
                    Sticker(
                        id = UUID.randomUUID().toString(),
                        packId = state.sticker.packId,
                        categoryId = state.sticker.categoryId,
                        isFavourite = false,
                        createdAt = System.currentTimeMillis(),
                        mimeType = EDITED_MIME_TYPE,
                        file = File(""),
                        thumbnailFile = File(""),
                    ),
                    image,
                    thumbnail,
                )
            }
        }

        /** Runs [store] off the main thread; both callbacks come back on the main thread. */
        private fun saveEdit(
            context: Context,
            state: EditStickerUiState.Success,
            onComplete: () -> Unit,
            onFailure: () -> Unit,
            store: suspend (image: ByteArray, thumbnail: ByteArray) -> Unit,
        ) {
            viewModelScope.launch {
                val saved =
                    attempt {
                        withContext(ioDispatcher) {
                            val bitmap =
                                checkNotNull(context.decodeBitmap(state.currentUriString)) {
                                    "Cannot decode ${state.currentUriString}"
                                }
                            store(encodeStickerImage(bitmap), encodeStickerThumbnail(bitmap))
                        }
                    }
                if (saved.isSuccess) onComplete() else onFailure()
            }
        }

        private companion object {
            const val EDITED_MIME_TYPE = "image/webp"
        }
    }
