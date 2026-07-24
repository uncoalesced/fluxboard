// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.video

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.stickercore.animation.AndroidAnimatedStickerConverter
import com.uncoalesced.stickykeys.stickercore.animation.ConversionQuality
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable
fun VideoConvertScreen(
    videoUriString: String,
    startMs: Long,
    endMs: Long,
    viewModel: VideoConvertViewModel = hiltViewModel(),
    onConversionComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val repository = viewModel.repository
    var progress by remember { mutableFloatStateOf(0f) }
    var errorText by remember { mutableStateOf<String?>(null) }
    
    var selectedQuality by remember { mutableStateOf(ConversionQuality.HIGH) }
    var isConverting by remember { mutableStateOf(false) }
    
    val targetFormat by viewModel.appPreferences.defaultExportFormat.collectAsState()

    LaunchedEffect(isConverting) {
        if (!isConverting) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                val videoUri = Uri.parse(videoUriString)
                val converter = viewModel.converter

                val animResult = converter.convertVideoToAnimatedSticker(
                    context = context,
                    videoUri = videoUri,
                    startMs = startMs,
                    endMs = endMs,
                    targetFormat = targetFormat,
                    quality = selectedQuality,
                    onProgress = { p -> progress = p }
                )

                val outBytes = animResult.getOrThrow()

                // Generate 256x256 thumbnail from first frame
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri)
                val firstFrame = retriever.getFrameAtTime(startMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()

                val thumbBytes = if (firstFrame != null) {
                    val scaled = Bitmap.createScaledBitmap(firstFrame, 256, 256, true)
                    ByteArrayOutputStream().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, this)
                        } else {
                            @Suppress("DEPRECATION")
                            scaled.compress(Bitmap.CompressFormat.WEBP, 80, this)
                        }
                    }.toByteArray()
                } else outBytes

                val stickerId = UUID.randomUUID().toString()
                val sticker = Sticker(
                    id = stickerId,
                    packId = null,
                    categoryId = null,
                    isFavourite = false,
                    createdAt = System.currentTimeMillis(),
                    mimeType = targetFormat,
                    file = File(""),
                    thumbnailFile = File("")
                )

                repository.saveSticker(sticker, outBytes, thumbBytes)
                withContext(Dispatchers.Main) {
                    onConversionComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorText = e.message ?: "Failed to convert video"
                isConverting = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (errorText != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.md)
            ) {
                Text(stringResource(R.string.text_conversion_error), style = StickyKeysTheme.typography.titleMedium, color = StickyKeysTheme.colors.error)
                Text(errorText!!, style = StickyKeysTheme.typography.bodyMedium)
                Button(onClick = onCancel) { Text(stringResource(R.string.text_back)) }
            }
        } else if (isConverting) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.md),
                modifier = Modifier.padding(StickyKeysTheme.spacing.lg)
            ) {
                Text(stringResource(R.string.text_creating_gif), style = StickyKeysTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(progress * 100).toInt()}%", style = StickyKeysTheme.typography.bodyMedium)
            }
        } else {
            // Quality Selection UI
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.md),
                modifier = Modifier.padding(StickyKeysTheme.spacing.lg)
            ) {
                Text(stringResource(R.string.text_select_output_quality), style = StickyKeysTheme.typography.titleMedium)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConversionQuality.values().forEach { quality ->
                        FilterChip(
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = quality },
                            label = { Text(quality.name) }
                        )
                    }
                }
                
                Text(
                    "Settings: ${selectedQuality.maxDimensionPx}px max dimension, ${selectedQuality.fps} fps",
                    style = StickyKeysTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.text_cancel), color = StickyKeysTheme.colors.error)
                    }
                    Button(onClick = { isConverting = true }) {
                        Text(stringResource(R.string.text_convert_to_gif))
                    }
                }
            }
        }
    }
}
