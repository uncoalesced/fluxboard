// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How far past the whole-image fit the crop screen can zoom. */
internal const val CROP_MAX_ZOOM = 8f

private const val CROP_BOX_FRACTION = 0.8f

/** A region of a bitmap, in bitmap pixels. */
internal data class CropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Side of the square crop box. Based on the shorter view side so that it stays on screen in
 * landscape and split-screen.
 */
internal fun cropBoxSide(
    viewWidth: Float,
    viewHeight: Float,
): Float = min(viewWidth, viewHeight) * CROP_BOX_FRACTION

/** The scale at which the whole bitmap fits inside the crop box, which is zoom 1. */
internal fun cropFitScale(
    viewWidth: Float,
    viewHeight: Float,
    bitmapWidth: Int,
    bitmapHeight: Int,
): Float = cropBoxSide(viewWidth, viewHeight) / max(bitmapWidth, bitmapHeight)

/**
 * Limits a pan on one axis so the image cannot leave a gap in the box while it is larger than
 * the box, and stays centred while it is smaller. Either way the box always overlaps the
 * image, so a crop can never come out empty.
 */
internal fun clampCropOffset(
    offset: Float,
    scaledImageSide: Float,
    boxSide: Float,
): Float {
    val limit = max(0f, (scaledImageSide - boxSide) / 2f)
    return offset.coerceIn(-limit, limit)
}

/**
 * The bitmap pixels under the crop box.
 *
 * Inverts the transform the screen draws with, where a bitmap pixel lands at
 * `viewCentre + offset + scale * (pixel - bitmapCentre)`. The screen draws and saves through
 * this one definition because a separate copy of the arithmetic is how the saved sticker comes
 * to differ from what the box showed. A box that overhangs the image is clipped to it, so the
 * result is exactly the visible part of the image and may not be square.
 *
 * Returns null when the view has not been measured or the box does not cover any of the image.
 */
internal fun cropRectFor(
    viewWidth: Float,
    viewHeight: Float,
    bitmapWidth: Int,
    bitmapHeight: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): CropRect? {
    if (viewWidth <= 0f || viewHeight <= 0f || scale <= 0f) return null
    val box = cropBoxSide(viewWidth, viewHeight)
    val horizontal = cropSpan(bitmapWidth, box, scale, offsetX)
    val vertical = cropSpan(bitmapHeight, box, scale, offsetY)
    if (horizontal.isEmpty() || vertical.isEmpty()) return null
    return CropRect(
        left = horizontal.first,
        top = vertical.first,
        width = horizontal.last - horizontal.first + 1,
        height = vertical.last - vertical.first + 1,
    )
}

private fun cropSpan(
    bitmapSide: Int,
    box: Float,
    scale: Float,
    offset: Float,
): IntRange {
    val centre = bitmapSide / 2f
    val start = (centre + (-box / 2f - offset) / scale).roundToInt().coerceIn(0, bitmapSide)
    val end = (centre + (box / 2f - offset) / scale).roundToInt().coerceIn(0, bitmapSide)
    return start until end
}
