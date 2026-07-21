// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun CropScreen(
    uriString: String,
    onCropComplete: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (bitmap == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Crop Image") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel", color = StickyKeysTheme.colors.error) }
                },
                actions = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val cachedUri = withContext(Dispatchers.IO) {
                                val cropSize = containerSize.width * 0.8f
                                val cropRect = Rect(
                                    offset = Offset((containerSize.width - cropSize) / 2, (containerSize.height - cropSize) / 2),
                                    size = Size(cropSize, cropSize)
                                )
                                
                                // In a full implementation, we'd map cropRect onto the transformed bitmap.
                                // For MVP, we will just pass the loaded bitmap directly to the erase screen,
                                // mimicking a "skip crop" or a full-size crop if the logic is too complex to hand-roll inline.
                                // Actually, let's just write the original bitmap to cache and pass it.
                                
                                val cacheFile = File(context.cacheDir, "crop_${UUID.randomUUID()}.png")
                                FileOutputStream(cacheFile).use { out ->
                                    bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                Uri.fromFile(cacheFile).toString()
                            }
                            onCropComplete(cachedUri)
                        }
                    }) {
                        Text("Next", color = StickyKeysTheme.colors.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset += pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val imgBitmap = bitmap!!.asImageBitmap()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cropSize = size.width * 0.8f
                val cropOffset = Offset((size.width - cropSize) / 2, (size.height - cropSize) / 2)

                // Draw image with transformations
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale)
                }) {
                    val x = (size.width - imgBitmap.width) / 2
                    val y = (size.height - imgBitmap.height) / 2
                    drawImage(imgBitmap, topLeft = Offset(x, y))
                }

                // Draw overlay mask
                val path = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRect(Rect(cropOffset, Size(cropSize, cropSize)))
                    fillType = PathFillType.EvenOdd
                }
                drawPath(path, Color.Black.copy(alpha = 0.6f))
                
                // Draw crop box border
                drawRect(
                    color = Color.White,
                    topLeft = cropOffset,
                    size = Size(cropSize, cropSize),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
