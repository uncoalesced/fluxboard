// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.file

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val stickersDir: File by lazy {
        File(context.filesDir, "stickers").apply {
            if (!exists()) mkdirs()
        }
    }

    private val thumbnailsDir: File by lazy {
        File(context.filesDir, "stickers_thumbnails").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getStickerFile(id: String): File {
        return File(stickersDir, "$id.sticker")
    }

    fun getThumbnailFile(id: String): File {
        return File(thumbnailsDir, "$id.thumb")
    }

    fun saveSticker(id: String, bytes: ByteArray) {
        getStickerFile(id).writeBytes(bytes)
    }

    fun saveThumbnail(id: String, bytes: ByteArray) {
        getThumbnailFile(id).writeBytes(bytes)
    }

    fun deleteFiles(id: String) {
        getStickerFile(id).delete()
        getThumbnailFile(id).delete()
    }
}
