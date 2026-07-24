// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [SegmentationEngine] for ML Kit Subject Segmentation.
 * 
 * NOTE: This implementation relies on Google Play Services delivering the subject-segmentation model.
 * If Play Services is absent, [isAvailable] returns false and [segmentSubject] fails gracefully,
 * signaling the caller to trigger the manual touch-up / eraser UI.
 */
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions

@Singleton
class MlKitSegmentationEngine @Inject constructor() : SegmentationEngine {

    override val isPlayServicesDependent: Boolean = true

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        // Check if Google Play Services is available
        try {
            val gmsClass = Class.forName("com.google.android.gms.common.GoogleApiAvailability")
            val getInstanceMethod = gmsClass.getMethod("getInstance")
            val availability = getInstanceMethod.invoke(null)
            val isGooglePlayServicesAvailableMethod = gmsClass.getMethod("isGooglePlayServicesAvailable", Context::class.java)
            val resultCode = isGooglePlayServicesAvailableMethod.invoke(availability, context) as Int
            resultCode == 0 // 0 = SUCCESS
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun segmentSubject(context: Context, bitmap: Bitmap): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) {
            return@withContext Result.failure(
                IllegalStateException("Google Play Services is not available on this device for ML Kit Subject Segmentation.")
            )
        }

        try {
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .enableForegroundBitmap()
                .build()
            val segmenter = SubjectSegmentation.getClient(options)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            val result = Tasks.await(segmenter.process(inputImage))
            
            val foregroundBitmap = result.foregroundBitmap
            if (foregroundBitmap != null) {
                Result.success(foregroundBitmap)
            } else {
                // Generate a transparent fallback if no foreground found
                val empty = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                Result.success(empty)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
