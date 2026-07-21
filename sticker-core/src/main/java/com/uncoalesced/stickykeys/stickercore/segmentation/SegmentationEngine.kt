// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.segmentation

import android.content.Context
import android.graphics.Bitmap

interface SegmentationEngine {
    /**
     * Flags whether this engine relies on Google Play Services.
     */
    val isPlayServicesDependent: Boolean

    /**
     * Checks whether the underlying segmentation model/service is available on the device.
     */
    suspend fun isAvailable(context: Context): Boolean

    /**
     * Executes subject segmentation on the input bitmap, returning a bitmap with background removed (transparent ARGB_8888).
     */
    suspend fun segmentSubject(context: Context, bitmap: Bitmap): Result<Bitmap>
}
