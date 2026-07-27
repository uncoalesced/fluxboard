// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.data.local.AppPreferences
import com.uncoalesced.stickykeys.di.IoDispatcher
import com.uncoalesced.stickykeys.stickercore.animation.AnimatedStickerConverter
import com.uncoalesced.stickykeys.stickercore.animation.ConversionQuality
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Where the video-to-sticker conversion currently is. */
sealed interface VideoConvertUiState {
    /** Quality has not been confirmed yet; nothing is running. */
    data class Idle(
        val quality: ConversionQuality = ConversionQuality.HIGH,
    ) : VideoConvertUiState

    data class Converting(
        val progress: Float,
    ) : VideoConvertUiState

    data object Done : VideoConvertUiState

    data class Error(
        val message: String,
    ) : VideoConvertUiState
}

/**
 * Owns the conversion so it outlives the composable.
 *
 * The work deliberately runs in [viewModelScope] rather than a `LaunchedEffect`: a
 * rotation tears the composition down and cancels anything scoped to it, which used to
 * abort a half-finished encode silently and drop the user back on the quality picker.
 * A ViewModel is retained across the configuration change, so the job and its progress
 * both survive it.
 */
@HiltViewModel
class VideoConvertViewModel
    @Inject
    constructor(
        val repository: StickerRepository,
        val converter: AnimatedStickerConverter,
        val appPreferences: AppPreferences,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<VideoConvertUiState>(VideoConvertUiState.Idle())
        val uiState: StateFlow<VideoConvertUiState> = _uiState.asStateFlow()

        fun selectQuality(quality: ConversionQuality) {
            val current = _uiState.value
            if (current is VideoConvertUiState.Idle) {
                _uiState.value = current.copy(quality = quality)
            }
        }

        /** Returns to the quality picker after a failure. */
        fun retry() {
            if (_uiState.value is VideoConvertUiState.Error) {
                _uiState.value = VideoConvertUiState.Idle()
            }
        }

        /**
         * [context] is passed per call rather than held, so the ViewModel keeps no
         * reference to an Activity; only the application context is ever used.
         */
        fun startConversion(
            context: Context,
            videoUriString: String,
            startMs: Long,
            endMs: Long,
        ) {
            // Idle is the only state a conversion can start from, and the switch to
            // Converting below happens before the launch -- so this cast is also what stops
            // a recomposition from launching a second encode over the first.
            val quality = (_uiState.value as? VideoConvertUiState.Idle)?.quality ?: return
            val appContext = context.applicationContext

            _uiState.value = VideoConvertUiState.Converting(0f)
            viewModelScope.launch {
                try {
                    convert(appContext, Uri.parse(videoUriString), startMs, endMs, quality)
                    _uiState.value = VideoConvertUiState.Done
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value =
                        VideoConvertUiState.Error(e.message ?: "Failed to convert video")
                }
            }
        }

        private suspend fun convert(
            appContext: Context,
            videoUri: Uri,
            startMs: Long,
            endMs: Long,
            quality: ConversionQuality,
        ) {
            val targetFormat = appPreferences.defaultExportFormat.value

            val outBytes =
                converter
                    .convertVideoToAnimatedSticker(
                        context = appContext,
                        videoUri = videoUri,
                        startMs = startMs,
                        endMs = endMs,
                        targetFormat = targetFormat,
                        quality = quality,
                        onProgress = { p ->
                            _uiState.value = VideoConvertUiState.Converting(p)
                        },
                    ).getOrThrow()

            // The converter and the repository each dispatch their own IO; the frame grab
            // is the only blocking call this class makes directly.
            val thumbBytes =
                withContext(ioDispatcher) { buildThumbnail(appContext, videoUri, startMs) }
                    ?: outBytes

            val sticker =
                Sticker(
                    id = UUID.randomUUID().toString(),
                    packId = null,
                    categoryId = null,
                    isFavourite = false,
                    createdAt = System.currentTimeMillis(),
                    mimeType = targetFormat,
                    file = File(""),
                    thumbnailFile = File(""),
                )
            repository.saveSticker(sticker, outBytes, thumbBytes)
        }

        /** 256x256 WebP taken from the first frame of the trimmed range. */
        private fun buildThumbnail(
            appContext: Context,
            videoUri: Uri,
            startMs: Long,
        ): ByteArray? {
            val retriever = MediaMetadataRetriever()
            val firstFrame =
                try {
                    retriever.setDataSource(appContext, videoUri)
                    retriever.getFrameAtTime(
                        startMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    )
                } finally {
                    retriever.release()
                } ?: return null

            val scaled = Bitmap.createScaledBitmap(firstFrame, 256, 256, true)
            return ByteArrayOutputStream()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, this)
                    } else {
                        @Suppress("DEPRECATION")
                        scaled.compress(Bitmap.CompressFormat.WEBP, 80, this)
                    }
                }.toByteArray()
        }
    }
