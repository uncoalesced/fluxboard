// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val THUMBNAIL_SIDE_PX = 256
private const val THUMBNAIL_QUALITY = 80

/**
 * Runs sticker work that can fail for reasons outside this app, such as a full disk, an unreadable
 * file or too large a bitmap, and returns the failure instead of throwing it. An exception that
 * escapes a coroutine takes down the whole process, and the keyboard runs in that process.
 * Cancellation is not a failure and still propagates.
 */
internal inline fun <T> attempt(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    } catch (e: OutOfMemoryError) {
        e.printStackTrace()
        Result.failure(e)
    }

/** The stored bytes of a static sticker: lossless, because the stored copy is the master. */
internal fun encodeStickerImage(bitmap: Bitmap): ByteArray =
    ByteArrayOutputStream()
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, this)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 100, this)
            }
        }.toByteArray()

/**
 * A square thumbnail with the image fitted and centred on transparency. Scaling straight to a
 * square stretches anything that is not already one, and a crop can legitimately not be.
 */
internal fun encodeStickerThumbnail(bitmap: Bitmap): ByteArray {
    val thumbnail = renderStickerThumbnail(bitmap)
    return ByteArrayOutputStream()
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                thumbnail.compress(Bitmap.CompressFormat.WEBP_LOSSY, THUMBNAIL_QUALITY, this)
            } else {
                @Suppress("DEPRECATION")
                thumbnail.compress(Bitmap.CompressFormat.WEBP, THUMBNAIL_QUALITY, this)
            }
        }.toByteArray()
        .also { thumbnail.recycle() }
}

internal fun renderStickerThumbnail(bitmap: Bitmap): Bitmap {
    val side = THUMBNAIL_SIDE_PX.toFloat()
    val fit = ImageFit.of(side, side, bitmap.width, bitmap.height)
    val thumbnail =
        Bitmap.createBitmap(THUMBNAIL_SIDE_PX, THUMBNAIL_SIDE_PX, Bitmap.Config.ARGB_8888)
    val matrix =
        Matrix().apply {
            postScale(fit.scale, fit.scale)
            postTranslate(fit.left, fit.top)
        }
    Canvas(thumbnail).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    return thumbnail
}

/** Writes [bitmap] to the cache as a PNG, which keeps transparency, and returns its URI. */
internal fun writeBitmapToCache(
    cacheDir: File,
    prefix: String,
    bitmap: Bitmap,
): String {
    val file = File(cacheDir, "${prefix}_${UUID.randomUUID()}.png")
    FileOutputStream(file).use { out ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed" }
    }
    return Uri.fromFile(file).toString()
}
