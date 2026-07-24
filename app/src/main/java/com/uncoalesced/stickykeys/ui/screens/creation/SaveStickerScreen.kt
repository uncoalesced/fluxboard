// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SaveStickerScreen(
    uriString: String,
    viewModel: SaveStickerViewModel = hiltViewModel(),
    onSaveComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val stream = context.contentResolver.openInputStream(uri)
                bitmap = BitmapFactory.decodeStream(stream)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.text_save_sticker)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.text_cancel), color = StickyKeysTheme.colors.error) }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(StickyKeysTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.lg)
        ) {
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(StickyKeysTheme.colors.surfaceVariant, StickyKeysTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.desc_preview),
                        modifier = Modifier.fillMaxSize().padding(StickyKeysTheme.spacing.sm)
                    )
                }

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                val stickerId = UUID.randomUUID().toString()
                                val webpBytes = ByteArrayOutputStream().apply {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        bitmap!!.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, this)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        bitmap!!.compress(Bitmap.CompressFormat.WEBP, 100, this)
                                    }
                                }.toByteArray()

                                // Thumbnail uses standard lossy WEBP at lower quality
                                val thumbBytes = ByteArrayOutputStream().apply {
                                    val thumbBmp = Bitmap.createScaledBitmap(bitmap!!, 256, 256, true)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        thumbBmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, this)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        thumbBmp.compress(Bitmap.CompressFormat.WEBP, 80, this)
                                    }
                                }.toByteArray()

                                viewModel.saveSticker(webpBytes, thumbBytes) {
                                    isSaving = false
                                    onSaveComplete()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = StickyKeysTheme.colors.onPrimary)
                    } else {
                        Text(stringResource(R.string.text_save_to_library))
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
