// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One sticker thumbnail, decoded off the main thread.
 *
 * Lifted out of the sticker-only IME panel when that panel was retired. It is not dead code
 * that came with it: the unified emoji-and-sticker picker draws every sticker through this,
 * so deleting the panel wholesale would have taken the picker's sticker tab with it.
 *
 * The decode is keyed on the sticker id rather than done inside `remember`, so scrolling a
 * grid does not decode on the composition thread.
 */
@Composable
fun StickerThumbnail(
    sticker: Sticker,
    fileManager: StickerFileManager,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = sticker.id) {
        value =
            withContext(Dispatchers.IO) {
                val file = fileManager.getThumbnailFile(sticker.id)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else {
                    null
                }
            }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .size(80.dp)
                    .padding(4.dp)
                    .clickable(onClick = onClick, role = Role.Button),
        )
    }
}
