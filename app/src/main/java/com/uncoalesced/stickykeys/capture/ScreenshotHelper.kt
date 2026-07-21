// Engineered by uncoalesced
package com.uncoalesced.stickykeys.capture

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object ScreenshotHelper {

    /**
     * Queries MediaStore for the latest screenshot image.
     */
    fun getLastScreenshotUri(context: Context): Uri? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: ""
                if (name.lowercase().contains("screenshot")) {
                    val id = cursor.getLong(idColumn)
                    return ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                }
            }
            
            // Fallback: Return the most recent image if no image explicitly named "screenshot" was found
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(idColumn)
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
            }
        }
        return null
    }
}
