// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.capture

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Watches MediaStore for newly-added screenshots and emits their Uris.
 *
 * Detection is scoped to the screenshots bucket (relative path or display name
 * containing "screenshot"), not every new image. Querying MediaStore needs the
 * media-read permission; when it is missing or the Uri cannot be resolved the
 * observer stays silent, and the manual "extract from last screenshot" action
 * remains the fallback path.
 */
class ScreenshotObserver(
    private val context: Context
) {
    private val _screenshots = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val screenshots = _screenshots.asSharedFlow()

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            uri?.let {
                if (isScreenshot(it)) {
                    _screenshots.tryEmit(it)
                }
            }
        }
    }

    private fun isScreenshot(uri: Uri): Boolean {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DISPLAY_NAME)
        } else {
            @Suppress("DEPRECATION")
            arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME)
        }
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                val path = cursor.getString(0) ?: ""
                val name = cursor.getString(1) ?: ""
                path.contains("screenshot", ignoreCase = true) ||
                    name.startsWith("screenshot", ignoreCase = true)
            } ?: false
        } catch (e: Exception) {
            // No media permission, or the uri is not queryable: not a screenshot we can use.
            false
        }
    }

    fun start() {
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(contentObserver)
    }
}
