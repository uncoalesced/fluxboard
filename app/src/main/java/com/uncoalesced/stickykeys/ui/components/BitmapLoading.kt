// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Decodes the image at [uriString], closing the stream, or returns null if it cannot
 * be read. Blocking: call it from an IO context.
 *
 * One function rather than a copy per screen because eight of those copies never
 * closed the stream, and a leak fixed in one of nine places is still a leak.
 * [mutable] returns an ARGB_8888 copy, which the eraser needs to paint transparency.
 */
fun Context.decodeBitmap(
    uriString: String,
    mutable: Boolean = false,
): Bitmap? =
    runCatching {
        // ponytail: no downsampling, so a very large import can still OOM. Add inSampleSize
        // here (BackgroundImage.decodeSampled does it on the theme side) when that is reported.
        val decoded =
            contentResolver.openInputStream(Uri.parse(uriString))?.use(BitmapFactory::decodeStream)
        if (mutable && decoded != null) {
            decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
        } else {
            decoded
        }
    }.getOrNull()
