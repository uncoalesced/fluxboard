// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.animation

/**
 * Defines the quality profile for converting a video to an animated sticker.
 * @property maxDimensionPx The maximum width or height of the output sticker in pixels.
 * @property fps The target frames per second of the output sticker.
 */
enum class ConversionQuality(val maxDimensionPx: Int, val fps: Int) {
    HIGH(maxDimensionPx = 512, fps = 15),
    MEDIUM(maxDimensionPx = 384, fps = 10),
    LOW(maxDimensionPx = 256, fps = 8)
}
