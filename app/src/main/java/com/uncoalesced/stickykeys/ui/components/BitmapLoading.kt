// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * The transform that stands an image up according to its EXIF orientation, or null when it is
 * already upright. Phones commonly store portrait photos as landscape pixels plus this tag.
 */
internal fun exifTransform(orientation: Int): Matrix? =
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE ->
            Matrix().apply {
                postScale(-1f, 1f)
                postRotate(270f)
            }
        ExifInterface.ORIENTATION_TRANSVERSE ->
            Matrix().apply {
                postScale(-1f, 1f)
                postRotate(90f)
            }
        else -> null
    }

/**
 * Decodes the image at [uriString], limited to [MAX_IMPORT_SIDE_PX] and stood upright, or returns
 * null if it cannot be read. Blocking: call it from an IO context.
 *
 * One function rather than a copy per screen because eight of those copies never closed the
 * stream, and because a second decoder is how one screen came to honour EXIF while another
 * imported the same photo on its side. [mutable] returns a mutable ARGB_8888 bitmap.
 */
fun Context.decodeBitmap(
    uriString: String,
    mutable: Boolean = false,
): Bitmap? =
    attempt {
        val uri = Uri.parse(uriString)
        val decoded =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(uri)
            } else {
                decodeWithBitmapFactory(uri)
            }
        if (mutable && decoded != null && !decoded.isMutable) {
            decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
        } else {
            decoded
        }
    }.getOrNull()

/** ImageDecoder applies EXIF orientation itself, and the size it reports is the upright one. */
@RequiresApi(Build.VERSION_CODES.P)
private fun Context.decodeWithImageDecoder(uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
        val (width, height) = scaledSizeFor(info.size.width, info.size.height)
        decoder.setTargetSize(width, height)
        // Hardware bitmaps can be neither compressed nor drawn into, and both happen here.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
    }

private fun Context.decodeWithBitmapFactory(uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options =
        BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
    val decoded =
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
    val transform = exifTransform(readExifOrientation(uri)) ?: return decoded
    return Bitmap
        .createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
        .also { if (it !== decoded) decoded.recycle() }
}

private fun Context.readExifOrientation(uri: Uri): Int =
    runCatching {
        contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

/** Where a screen's image has got to. Failing has to be a state, or it looks like loading. */
sealed interface DecodedImage {
    data object Loading : DecodedImage

    data class Ready(
        val bitmap: Bitmap,
    ) : DecodedImage

    data object Failed : DecodedImage
}

/** Decodes [uriString] off the main thread and reports its progress as state. */
@Composable
fun Context.rememberDecodedImage(
    uriString: String,
    mutable: Boolean = false,
): DecodedImage =
    produceState<DecodedImage>(DecodedImage.Loading, uriString, mutable) {
        value = DecodedImage.Loading
        val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uriString, mutable) }
        value = bitmap?.let { DecodedImage.Ready(it) } ?: DecodedImage.Failed
    }.value
