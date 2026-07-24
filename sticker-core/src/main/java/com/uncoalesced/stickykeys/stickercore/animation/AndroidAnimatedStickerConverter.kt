// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.animation

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoder
import com.shakster.gifkt.GifEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [AnimatedStickerConverter] using native Android MediaMetadataRetriever
 * for zero-dependency video frame extraction, gif.kt for GIF encoding, and
 * webp-android's WebPAnimEncoder for animated WebP encoding.
 */
@Singleton
class AndroidAnimatedStickerConverter @Inject constructor() : AnimatedStickerConverter {

    override suspend fun convertVideoToAnimatedSticker(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        targetFormat: String,
        quality: ConversionQuality,
        onProgress: (Float) -> Unit
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var webpEncoder: WebPAnimEncoder? = null
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = (endMs - startMs).coerceAtLeast(100L)

            val intervalMs = 1000L / quality.fps.coerceIn(1, 30)
            val frameCount = (durationMs / intervalMs).toInt().coerceAtLeast(1)

            val buffer = Buffer()
            val gifEncoder = if (targetFormat == "image/gif") GifEncoder(buffer) else null

            for (i in 0 until frameCount) {
                val timeUs = (startMs + i * intervalMs) * 1000L
                val rawBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (rawBmp != null) {
                    val scaledBmp = scaleToMaxDimension(rawBmp, quality.maxDimensionPx)

                    val width = scaledBmp.width
                    val height = scaledBmp.height

                    if (targetFormat == "image/gif") {
                        val pixels = IntArray(width * height)
                        scaledBmp.getPixels(pixels, 0, width, 0, 0, width, height)
                        gifEncoder?.writeFrame(pixels, width, height, intervalMs.toInt().milliseconds)
                    } else if (targetFormat == "image/webp") {
                        val encoder = webpEncoder ?: WebPAnimEncoder(context, width, height).also {
                            webpEncoder = it
                        }
                        encoder.addFrame(i * intervalMs, scaledBmp)
                    }

                    if (scaledBmp != rawBmp) {
                        scaledBmp.recycle()
                    }
                    rawBmp.recycle()
                }

                val progress = (i + 1).toFloat() / frameCount
                onProgress(progress)
            }

            if (targetFormat == "image/gif") {
                gifEncoder?.close()
                Result.success(buffer.readByteArray())
            } else {
                val encoder = webpEncoder
                    ?: return@withContext Result.failure(
                        IllegalStateException("No frames could be extracted from the video")
                    )
                val tempFile = File.createTempFile("anim_sticker", ".webp", context.cacheDir)
                try {
                    // The final timestamp marks the end of the last frame's display window.
                    encoder.assemble(frameCount * intervalMs, Uri.fromFile(tempFile))
                    val webpBytes = tempFile.readBytes()
                    if (isValidAnimatedWebP(webpBytes)) {
                        Result.success(webpBytes)
                    } else {
                        Result.failure(
                            IllegalStateException("Encoder output is not a valid animated WebP")
                        )
                    }
                } finally {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                webpEncoder?.release()
            } catch (ignored: Exception) {}
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

    companion object {
        /**
         * Structural check that [bytes] is a genuinely animated WebP, not a static one:
         * a RIFF/WEBP container whose VP8X chunk has the animation flag (0x02) set and
         * which contains at least two ANMF (animation frame) chunks.
         */
        internal fun isValidAnimatedWebP(bytes: ByteArray): Boolean {
            if (bytes.size < 30) return false
            if (fourCC(bytes, 0) != "RIFF") return false
            if (fourCC(bytes, 8) != "WEBP") return false

            var offset = 12
            var hasAnimFlag = false
            var anmfCount = 0
            while (offset + 8 <= bytes.size) {
                val chunkId = fourCC(bytes, offset)
                val chunkSize = readUInt32Le(bytes, offset + 4)
                if (chunkSize < 0 || offset + 8 + chunkSize > bytes.size) break

                when (chunkId) {
                    "VP8X" -> {
                        val flags = bytes[offset + 8].toInt() and 0xFF
                        if (flags and 0x02 != 0) hasAnimFlag = true
                    }
                    "ANMF" -> anmfCount++
                }
                // Chunks are padded to even sizes per the RIFF spec.
                offset += 8 + chunkSize + (chunkSize and 1)
            }
            return hasAnimFlag && anmfCount >= 2
        }

        private fun fourCC(bytes: ByteArray, offset: Int): String {
            return String(bytes, offset, 4, Charsets.US_ASCII)
        }

        private fun readUInt32Le(bytes: ByteArray, offset: Int): Int {
            return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        }
    }
}
