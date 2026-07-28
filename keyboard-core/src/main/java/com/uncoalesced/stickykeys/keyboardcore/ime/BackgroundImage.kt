// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Largest power-of-two subsample that still fills the requested box.
 *
 * `BitmapFactory` only honours powers of two for `inSampleSize`, so this halves until one
 * more halving would drop below the target. Separated out as a pure function because it is
 * the part worth testing: the decode itself needs a real file and a real decoder.
 */
internal fun calculateInSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    if (reqWidth <= 0 || reqHeight <= 0) return 1
    var sampleSize = 1
    while (
        (sourceHeight / (sampleSize * 2)) >= reqHeight &&
        (sourceWidth / (sampleSize * 2)) >= reqWidth
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Decodes a keyboard background off the main thread, downsampled to the size it will
 * actually be drawn at.
 *
 * The previous version called `BitmapFactory.decodeFile` inside `remember`, which is a
 * synchronous full-resolution decode on the main thread during composition. A 4000x3000
 * photo is 4000 * 3000 * 4 bytes -- about 48 MB -- allocated to draw into a strip a few
 * hundred pixels tall, on a keyboard that has to appear instantly. Decoding at the target
 * size on [Dispatchers.IO] and returning null until it lands keeps composition free of both
 * the allocation and the stall.
 */
@Composable
fun rememberBackgroundBitmap(
    path: String?,
    reqWidthPx: Int,
    reqHeightPx: Int,
): ImageBitmap? {
    val state =
        produceState<ImageBitmap?>(
            initialValue = null,
            path,
            reqWidthPx,
            reqHeightPx,
        ) {
            value =
                if (path == null) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        decodeDownsampled(path, reqWidthPx, reqHeightPx)
                    }
                }
        }
    return state.value
}

/**
 * Bounds-only pass, then the real decode at the chosen sample size.
 *
 * Kept separate from the cover scale so the subsampling is observable on its own: the two
 * steps produce the same final dimensions, so a test that only looks at the end result
 * cannot tell whether `inSampleSize` was applied at all.
 */
internal fun decodeSampled(
    path: String,
    reqWidthPx: Int,
    reqHeightPx: Int,
): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options =
        BitmapFactory.Options().apply {
            inSampleSize =
                calculateInSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    reqWidthPx,
                    reqHeightPx,
                )
        }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

/** Two-pass decode: subsample during decode, then scale the remainder to the target box. */
internal fun decodeDownsampled(
    path: String,
    reqWidthPx: Int,
    reqHeightPx: Int,
): ImageBitmap? =
    try {
        val sampled = decodeSampled(path, reqWidthPx, reqHeightPx)
        // inSampleSize only moves in powers of two, so it can still leave a bitmap several
        // times larger than the strip it is drawn into -- a 4000x3000 photo into 1080x280
        // subsamples to 2000x1500, which is 12 MB resident on a keyboard that stays in
        // memory for the whole session. One exact scale to the cover size settles it at
        // roughly 3 MB. The intermediate is recycled rather than left for the collector.
        sampled?.let { scaleToCover(it, reqWidthPx, reqHeightPx).asImageBitmap() }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

/**
 * Scales [source] down to the smallest size that still covers a [reqWidthPx] x [reqHeightPx]
 * box, matching how the background is drawn (`ContentScale.Crop`). Never scales up.
 */
internal fun scaleToCover(
    source: Bitmap,
    reqWidthPx: Int,
    reqHeightPx: Int,
): Bitmap {
    if (reqWidthPx <= 0 || reqHeightPx <= 0) return source
    val scale =
        maxOf(
            reqWidthPx.toFloat() / source.width,
            reqHeightPx.toFloat() / source.height,
        )
    if (scale >= 1f) return source

    val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    if (scaled !== source) source.recycle()
    return scaled
}
