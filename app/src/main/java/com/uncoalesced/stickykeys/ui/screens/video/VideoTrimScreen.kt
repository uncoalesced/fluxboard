// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_TRIM_DURATION_MS = 10_000L // 10 seconds ceiling

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTrimScreen(
    videoUriString: String,
    onTrimComplete: (videoUriString: String, startMs: Long, endMs: Long) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val videoUri = remember(videoUriString) { Uri.parse(videoUriString) }

    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var keyframes by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    var rangeValues by remember { mutableStateOf(0f..10000f) }
    var currentPreviewFrame by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoUriString) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 5000L
                totalDurationMs = duration

                val initialMax = minOf(duration, MAX_TRIM_DURATION_MS)
                rangeValues = 0f..initialMax.toFloat()

                // Extract 5 thumbnail keyframes across video timeline
                val frameList = mutableListOf<Bitmap>()
                val stepUs = (duration * 1000L) / 5
                for (i in 0 until 5) {
                    val frameTimeUs = i * stepUs
                    val bmp = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bmp != null) {
                        frameList.add(Bitmap.createScaledBitmap(bmp, 120, 120, true))
                    }
                }
                keyframes = frameList

                // Initial preview frame
                val firstFrame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                currentPreviewFrame = firstFrame
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }
        }
    }

    // Update preview frame when scrub position changes
    LaunchedEffect(rangeValues.start) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val frameUs = (rangeValues.start * 1000f).toLong()
                val frame = retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    currentPreviewFrame = frame
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }
        }
    }

    if (totalDurationMs == 0L) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startMs = rangeValues.start.toLong()
    val endMs = rangeValues.endInclusive.toLong()
    val selectedDurationMs = endMs - startMs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trim Video (Max 10s)") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel", color = StickyKeysTheme.colors.error) }
                },
                actions = {
                    TextButton(onClick = {
                        onTrimComplete(videoUriString, startMs, endMs)
                    }) {
                        Text("Next", color = StickyKeysTheme.colors.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
                .padding(StickyKeysTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.md)
        ) {
            // Live Frame Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (currentPreviewFrame != null) {
                    Image(
                        bitmap = currentPreviewFrame!!.asImageBitmap(),
                        contentDescription = "Trim Preview Frame",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            // Keyframe strip preview
            if (keyframes.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items(keyframes) { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Keyframe",
                            modifier = Modifier
                                .width(60.dp)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // Duration Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Start: ${startMs / 1000f}s",
                    style = StickyKeysTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    "Length: ${selectedDurationMs / 1000f}s / 10s max",
                    style = StickyKeysTheme.typography.bodyMedium,
                    color = if (selectedDurationMs > MAX_TRIM_DURATION_MS) StickyKeysTheme.colors.error else Color.LightGray
                )
                Text(
                    "End: ${endMs / 1000f}s",
                    style = StickyKeysTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            // Scrubber RangeSlider
            RangeSlider(
                value = rangeValues,
                onValueChange = { newRange ->
                    var start = newRange.start
                    var end = newRange.endInclusive
                    if (end - start > MAX_TRIM_DURATION_MS) {
                        // Enforce 10-second max duration ceiling
                        if (start != rangeValues.start) {
                            end = start + MAX_TRIM_DURATION_MS
                        } else {
                            start = end - MAX_TRIM_DURATION_MS
                        }
                    }
                    rangeValues = start..end
                },
                valueRange = 0f..totalDurationMs.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
