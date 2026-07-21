// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.animation

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.shakster.gifkt.GifEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [AnimatedStickerConverter] using native Android MediaMetadataRetriever
 * for zero-dependency video frame extraction and gif.kt for GIF encoding.
 */
@Singleton
class AndroidAnimatedStickerConverter @Inject constructor() : AnimatedStickerConverter {

    override suspend fun convertVideoToAnimatedSticker(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        quality: ConversionQuality,
        onProgress: (Float) -> Unit
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = (endMs - startMs).coerceAtLeast(100L)

            val intervalMs = 1000L / quality.fps.coerceIn(1, 30)
            val frameCount = (durationMs / intervalMs).toInt().coerceAtLeast(1)

            val buffer = Buffer()
            val encoder = GifEncoder(buffer)

            for (i in 0 until frameCount) {
                val timeUs = (startMs + i * intervalMs) * 1000L
                val rawBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (rawBmp != null) {
                    val scaledBmp = scaleToMaxDimension(rawBmp, quality.maxDimensionPx)
                    
                    val width = scaledBmp.width
                    val height = scaledBmp.height
                    val pixels = IntArray(width * height)
                    scaledBmp.getPixels(pixels, 0, width, 0, 0, width, height)
                    
                    // The time parameter in writeFrame is frame duration in milliseconds
                    encoder.writeFrame(pixels, width, height, intervalMs.toInt().milliseconds)

                    if (scaledBmp != rawBmp) {
                        scaledBmp.recycle()
                    }
                    rawBmp.recycle()
                }

                val progress = (i + 1).toFloat() / frameCount
                onProgress(progress)
            }
            
            encoder.close()
            Result.success(buffer.readByteArray())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun scaleToMaxDimension(bmp: Bitmap, maxDim: Int): Bitmap {
        val width = bmp.width
        val height = bmp.height
        if (width <= maxDim && height <= maxDim) return bmp

        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, true)
    }
}
