// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.animation

import android.content.Context
import android.net.Uri

interface AnimatedStickerConverter {
    /**
     * Extracts frames over [startMs, endMs] from [videoUri] and encodes them into an animated sticker format.
     * Reports progress from 0.0f to 1.0f via [onProgress].
     */
    suspend fun convertVideoToAnimatedSticker(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        targetFormat: String = "image/webp", // "image/webp" or "image/gif"
        quality: ConversionQuality = ConversionQuality.HIGH,
        onProgress: (Float) -> Unit
    ): Result<ByteArray>
}
