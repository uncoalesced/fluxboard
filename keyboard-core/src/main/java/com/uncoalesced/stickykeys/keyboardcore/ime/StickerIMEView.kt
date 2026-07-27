// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StickerIMEView(
    viewModel: StickerIMEViewModel,
    fileManager: StickerFileManager,
    onStickerClick: (Sticker) -> Unit,
    onBackToKeyboard: () -> Unit,
) {
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val stickers by viewModel.stickersForCurrentTab.collectAsState()

    // Not fillMaxSize(): the IME window is WRAP_CONTENT, so "fill" resolves against the
    // whole available height and the grid claimed the entire screen.
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(rememberImePanelHeight()),
    ) {
        Row {
            TextButton(onClick = onBackToKeyboard) {
                Text(stringResource(R.string.text_back))
            }
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 8.dp,
                modifier = Modifier.weight(1f),
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text(stringResource(R.string.text_favourites)) },
                )
                categories.forEachIndexed { index, category ->
                    val tabIndex = index + 1
                    Tab(
                        selected = selectedTabIndex == tabIndex,
                        onClick = { viewModel.selectTab(tabIndex) },
                        text = { Text(category.name) },
                    )
                }
            }
        }

        val favouritesLabel = stringResource(R.string.text_favourites)
        val currentTabName =
            if (selectedTabIndex == 0) {
                favouritesLabel
            } else {
                categories.getOrNull(selectedTabIndex - 1)?.name ?: favouritesLabel
            }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(80.dp),
            modifier = Modifier.fillMaxSize().padding(8.dp),
        ) {
            itemsIndexed(stickers) { index, sticker ->
                StickerThumbnail(
                    sticker = sticker,
                    fileManager = fileManager,
                    // A sticker has no name to read out, so position within the visible tab
                    // is the only thing that actually distinguishes one from another. The
                    // id was worse than nothing: 36 characters of spoken hexadecimal.
                    contentDescription =
                        "Sticker ${index + 1} of ${stickers.size} in $currentTabName",
                    onClick = { onStickerClick(sticker) },
                )
            }
        }
    }
}

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
