// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton

@Composable
fun StickerIMEView(
    viewModel: StickerIMEViewModel,
    fileManager: StickerFileManager,
    onStickerClick: (Sticker) -> Unit,
    onBackToKeyboard: () -> Unit
) {
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val stickers by viewModel.stickersForCurrentTab.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row {
            TextButton(onClick = onBackToKeyboard) {
                Text("Back")
            }
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 8.dp,
                modifier = Modifier.weight(1f)
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Favourites") }
                )
                categories.forEachIndexed { index, category ->
                    val tabIndex = index + 1
                    Tab(
                        selected = selectedTabIndex == tabIndex,
                        onClick = { viewModel.selectTab(tabIndex) },
                        text = { Text(category.name) }
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(80.dp),
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            items(stickers) { sticker ->
                StickerThumbnail(
                    sticker = sticker,
                    fileManager = fileManager,
                    onClick = { onStickerClick(sticker) }
                )
            }
        }
    }
}

@Composable
fun StickerThumbnail(
    sticker: Sticker,
    fileManager: StickerFileManager,
    onClick: () -> Unit
) {
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = sticker.id) {
        value = withContext(Dispatchers.IO) {
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
            contentDescription = sticker.id,
            modifier = Modifier
                .size(80.dp)
                .padding(4.dp)
                .clickable(onClick = onClick)
        )
    }
}
