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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.R
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
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSegmenting by remember { mutableStateOf(false) }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val bmp =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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

    if (bitmap == null || isSegmenting) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                if (isSegmenting) {
                    Text(
                        stringResource(R.string.text_segmenting_subject),
                        style = StickyKeysTheme.typography.bodyMedium,
                    )
                }
            }
        }
        return
    }

    val source = bitmap!!
    // Relative to the whole-image fit, so 1 is always "all of it" whatever the photo's size.
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.text_crop_image)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(
                            stringResource(R.string.text_cancel),
                            color = StickyKeysTheme.colors.error,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (isSegmenting) return@TextButton
                        val viewWidth = containerSize.width.toFloat()
                        val viewHeight = containerSize.height.toFloat()
                        val rect =
                            cropRectFor(
                                viewWidth,
                                viewHeight,
                                source.width,
                                source.height,
                                cropFitScale(viewWidth, viewHeight, source.width, source.height) *
                                    zoom,
                                offset.x,
                                offset.y,
                            )
                        isSegmenting = true
                        coroutineScope.launch {
                            // v1 has no automatic segmentation: save the crop and hand
                            // straight to the manual eraser.
                            val croppedUri =
                                withContext(Dispatchers.IO) {
                                    writeCrop(context.cacheDir, source, rect)
                                }
                            isSegmenting = false
                            onCropComplete(croppedUri)
                        }
                    }) {
                        Text(
                            stringResource(R.string.text_next),
                            color = StickyKeysTheme.colors.accentText,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Black)
                    .onSizeChanged { containerSize = it }
                    .pointerInput(source) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, CROP_MAX_ZOOM)
                            val viewWidth = containerSize.width.toFloat()
                            val viewHeight = containerSize.height.toFloat()
                            val total =
                                cropFitScale(viewWidth, viewHeight, source.width, source.height) *
                                    zoom
                            val box = cropBoxSide(viewWidth, viewHeight)
                            offset =
                                Offset(
                                    clampCropOffset(offset.x + pan.x, total * source.width, box),
                                    clampCropOffset(offset.y + pan.y, total * source.height, box),
                                )
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            val imgBitmap = source.asImageBitmap()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cropSize = cropBoxSide(size.width, size.height)
                val cropOffset = Offset((size.width - cropSize) / 2, (size.height - cropSize) / 2)
                val total =
                    cropFitScale(size.width, size.height, source.width, source.height) * zoom

                // The transform cropRectFor inverts: keep the two in step.
                withTransform({
                    translate(size.width / 2 + offset.x, size.height / 2 + offset.y)
                    scale(total, total, pivot = Offset.Zero)
                }) {
                    drawImage(
                        imgBitmap,
                        topLeft = Offset(-imgBitmap.width / 2f, -imgBitmap.height / 2f),
                    )
                }

                // Draw overlay mask
                val path =
                    Path().apply {
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
                    style =
                        androidx.compose.ui.graphics.drawscope
                            .Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * Writes the region of [source] under the crop box to the cache and returns its URI. A null
 * [rect] means the view was never measured, and then the only honest result is the whole image.
 */
private fun writeCrop(
    cacheDir: File,
    source: Bitmap,
    rect: CropRect?,
): String {
    val cropped =
        rect?.let { Bitmap.createBitmap(source, it.left, it.top, it.width, it.height) } ?: source
    val cacheFile = File(cacheDir, "crop_${UUID.randomUUID()}.png")
    FileOutputStream(cacheFile).use { out ->
        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    // createBitmap hands back source itself for a whole-image rect, and that one is on screen.
    if (cropped !== source) cropped.recycle()
    return Uri.fromFile(cacheFile).toString()
}
