// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

/**
 * How a bitmap sits when scaled to fit inside a view and centred. A tool that takes touches on
 * screen and paints into the bitmap needs this in both directions, and has to use one definition
 * for both or the result lands somewhere other than where the finger was.
 */
internal data class ImageFit(
    val scale: Float,
    val left: Float,
    val top: Float,
) {
    fun toBitmapX(viewX: Float): Float = (viewX - left) / scale

    fun toBitmapY(viewY: Float): Float = (viewY - top) / scale

    /** A length on screen, such as a brush width, as a length in bitmap pixels. */
    fun toBitmapLength(viewLength: Float): Float = viewLength / scale

    companion object {
        fun of(
            viewWidth: Float,
            viewHeight: Float,
            bitmapWidth: Int,
            bitmapHeight: Int,
        ): ImageFit {
            val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
            return ImageFit(
                scale = scale,
                left = (viewWidth - bitmapWidth * scale) / 2f,
                top = (viewHeight - bitmapHeight * scale) / 2f,
            )
        }
    }
}
