// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed interface EditStickerUiState {
    data object Loading : EditStickerUiState
    data class Success(
        val sticker: Sticker,
        val currentUriString: String
    ) : EditStickerUiState
    data class Error(val message: String) : EditStickerUiState
}

@HiltViewModel
class EditStickerViewModel @Inject constructor(
    private val repository: StickerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditStickerUiState>(EditStickerUiState.Loading)
    val uiState: StateFlow<EditStickerUiState> = _uiState.asStateFlow()

    fun loadSticker(stickerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sticker = repository.getStickerById(stickerId)
            if (sticker != null && sticker.file.exists()) {
                _uiState.value = EditStickerUiState.Success(
                    sticker = sticker,
                    currentUriString = Uri.fromFile(sticker.file).toString()
                )
            } else {
                _uiState.value = EditStickerUiState.Error("Sticker not found")
            }
        }
    }

    fun updateCurrentUri(newUriString: String) {
        val state = _uiState.value
        if (state is EditStickerUiState.Success) {
            _uiState.value = state.copy(currentUriString = newUriString)
        }
    }

    fun overwriteSticker(context: android.content.Context, onComplete: () -> Unit) {
        val state = _uiState.value
        if (state !is EditStickerUiState.Success) return

        viewModelScope.launch(Dispatchers.IO) {
            val (webpBytes, thumbBytes) = processBytes(context, state.currentUriString)
            repository.updateStickerData(state.sticker, webpBytes, thumbBytes)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun saveAsNewSticker(context: android.content.Context, onComplete: () -> Unit) {
        val state = _uiState.value
        if (state !is EditStickerUiState.Success) return

        viewModelScope.launch(Dispatchers.IO) {
            val (webpBytes, thumbBytes) = processBytes(context, state.currentUriString)
            val newSticker = Sticker(
                id = UUID.randomUUID().toString(),
                packId = state.sticker.packId,
                categoryId = state.sticker.categoryId,
                isFavourite = false,
                createdAt = System.currentTimeMillis(),
                mimeType = "image/webp",
                file = File(""),
                thumbnailFile = File("")
            )
            repository.saveSticker(newSticker, webpBytes, thumbBytes)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    private fun processBytes(context: android.content.Context, uriString: String): Pair<ByteArray, ByteArray> {
        val uri = Uri.parse(uriString)
        val stream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(stream)

        val webpBytes = ByteArrayOutputStream().apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, this)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 100, this)
            }
        }.toByteArray()

        val thumbBytes = ByteArrayOutputStream().apply {
            val thumbBmp = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                thumbBmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, this)
            } else {
                @Suppress("DEPRECATION")
                thumbBmp.compress(Bitmap.CompressFormat.WEBP, 80, this)
            }
        }.toByteArray()

        return Pair(webpBytes, thumbBytes)
    }
}
