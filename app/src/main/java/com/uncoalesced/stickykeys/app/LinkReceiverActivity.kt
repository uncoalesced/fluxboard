// Engineered by uncoalesced
package com.uncoalesced.stickykeys.app

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.uncoalesced.stickykeys.transfer.migration.StickerTransferService
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class LinkReceiverActivity : ComponentActivity() {

    @Inject lateinit var transferService: StickerTransferService
    @Inject lateinit var repository: StickerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uri = intent.data
        if (uri == null || uri.host != "stickykeys.app") {
            finish()
            return
        }

        val linkUrl = uri.toString()

        setContent {
            var progressText by remember { mutableStateOf("Connecting to sender...") }
            var isError by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                try {
                    val stickerId = UUID.randomUUID().toString()
                    val tempFile = File(cacheDir, "$stickerId.tmp")
                    
                    progressText = "Downloading sticker..."
                    transferService.receiveSticker(linkUrl, tempFile)
                    
                    progressText = "Importing sticker..."
                    importSticker(stickerId, tempFile)
                    
                    Toast.makeText(this@LinkReceiverActivity, "Sticker imported successfully!", Toast.LENGTH_LONG).show()
                    finish()
                } catch (e: Exception) {
                    isError = true
                    progressText = "Failed to download sticker: ${e.message}"
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isError) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text(progressText)
                        if (isError) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { finish() }) {
                                Text(stringResource(R.string.text_close))
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun importSticker(id: String, tempFile: File) = withContext(Dispatchers.IO) {
        val bytes = tempFile.readBytes()
        // The received payload is a single image; reuse it as its own thumbnail
        // until a dedicated thumbnail is generated for received stickers.
        val sticker = Sticker(
            id = id,
            packId = null,
            // Uncategorized on arrival; the user can file it from the library.
            categoryId = null,
            isFavourite = false,
            createdAt = System.currentTimeMillis(),
            mimeType = "image/webp",
            file = File(""),
            thumbnailFile = File("")
        )
        // The repository owns both the on-disk write and the DB row -- nothing
        // above it touches StickerFileManager or the DAOs directly.
        repository.saveSticker(sticker, bytes, bytes)
        tempFile.delete()
    }
}
