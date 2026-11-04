// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Longest side an imported image is decoded at. A 50MP photo needs about 200MB as ARGB_8888,
 * more than an app's heap, and a sticker has no use for that much resolution.
 */
internal const val MAX_IMPORT_SIDE_PX = 2048

/**
 * The smallest power-of-two `inSampleSize` that brings the longest side within [maxSide].
 * Unknown dimensions, which a failed bounds decode reports as -1, sample at 1.
 */
internal fun sampleSizeFor(
    width: Int,
    height: Int,
    maxSide: Int = MAX_IMPORT_SIDE_PX,
): Int {
    val longest = max(width, height)
    var sample = 1
    while (longest > maxSide * sample) sample *= 2
    return sample
}

/** The dimensions that bring the longest side within [maxSide], keeping the aspect ratio. */
internal fun scaledSizeFor(
    width: Int,
    height: Int,
    maxSide: Int = MAX_IMPORT_SIDE_PX,
): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= maxSide) return width to height
    val ratio = maxSide.toFloat() / longest
    return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
}

/**
 * Decodes the image at [uriString], closing the stream, or returns null if it cannot
 * be read. Blocking: call it from an IO context.
 *
 * One function rather than a copy per screen because eight of those copies never
 * closed the stream, and a leak fixed in one of nine places is still a leak.
 * [mutable] returns an ARGB_8888 copy, which the eraser needs to paint transparency.
 * Sampled down to [MAX_IMPORT_SIDE_PX].
 */
fun Context.decodeBitmap(
    uriString: String,
    mutable: Boolean = false,
): Bitmap? =
    runCatching {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
        val decoded =
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        if (mutable && decoded != null) {
            decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
        } else {
            decoded
        }
    }.getOrNull()
