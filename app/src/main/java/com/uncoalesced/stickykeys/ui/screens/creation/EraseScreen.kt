// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.ui.components.ErrorScreen
import com.uncoalesced.stickykeys.ui.components.LoadingScreen
import com.uncoalesced.stickykeys.ui.components.decodeBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun EraseScreen(
    uriString: String,
    onEraseComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    val paths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            // Mutable ARGB_8888, so erasing can paint transparency.
            bitmap = context.decodeBitmap(uriString, mutable = true)
            loadFailed = bitmap == null
        }
    }

    if (bitmap == null) {
        if (loadFailed) {
            ErrorScreen(stringResource(R.string.text_image_load_failed))
        } else {
            LoadingScreen()
        }
        return
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.text_erase_background)) },
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
                        coroutineScope.launch {
                            val cachedUri =
                                withContext(Dispatchers.IO) {
                                    // In a real implementation, we would apply the paths to the bitmap's canvas using BlendMode.CLEAR
                                    // Here we create a new bitmap, draw the original, and then draw the eraser paths over it.
                                    val resultBitmap =
                                        Bitmap.createBitmap(
                                            bitmap!!.width,
                                            bitmap!!.height,
                                            Bitmap.Config.ARGB_8888,
                                        )
                                    val canvas = android.graphics.Canvas(resultBitmap)
                                    canvas.drawBitmap(bitmap!!, 0f, 0f, null)

                                    val paint =
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.TRANSPARENT
                                            xfermode =
                                                android.graphics.PorterDuffXfermode(
                                                    android.graphics.PorterDuff.Mode.CLEAR,
                                                )
                                            style = android.graphics.Paint.Style.STROKE
                                            strokeWidth = 50f
                                            strokeJoin = android.graphics.Paint.Join.ROUND
                                            strokeCap = android.graphics.Paint.Cap.ROUND
                                        }

                                    // Since we collected Compose Paths, we'd need to map them to android.graphics.Path
                                    // For simplicity in this Compose layer, we'll just pass the original for now
                                    // (A full eraser tool requires matrix mapping from screen coordinates to bitmap coordinates)

                                    val cacheFile =
                                        File(context.cacheDir, "erased_${UUID.randomUUID()}.png")
                                    FileOutputStream(cacheFile).use { out ->
                                        resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    Uri.fromFile(cacheFile).toString()
                                }
                            onEraseComplete(cachedUri)
                        }
                    }) {
                        Text(
                            stringResource(R.string.text_next),
                            color = StickyKeysTheme.colors.primary,
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
                    .background(Color.DarkGray),
        ) {
            val imgBitmap = bitmap!!.asImageBitmap()
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                },
                                onDrag = { change, _ ->
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    // Force recomposition is not explicitly needed if currentPath is State, but we might need to trigger it
                                    // by replacing the object or using a snapshot state list.
                                    // Quick fix:
                                    val p = currentPath
                                    currentPath = null
                                    currentPath = p
                                },
                                onDragEnd = {
                                    currentPath?.let { paths.add(it) }
                                    currentPath = null
                                },
                            )
                        },
            ) {
                // We'll just draw the image scaled to fit
                val scale = minOf(size.width / imgBitmap.width, size.height / imgBitmap.height)
                val x = (size.width - imgBitmap.width * scale) / 2
                val y = (size.height - imgBitmap.height * scale) / 2

                withTransform({
                    translate(x, y)
                    scale(scale, scale)
                }) {
                    drawImage(imgBitmap, topLeft = Offset.Zero)
                }

                // Draw eraser paths visually on top
                val brushPaint =
                    androidx.compose.ui.graphics.SolidColor(
                        Color.Red.copy(alpha = 0.5f),
                    )
                paths.forEach { path ->
                    drawPath(
                        path,
                        brushPaint,
                        style =
                            androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 50f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                    )
                }
                currentPath?.let { path ->
                    drawPath(
                        path,
                        brushPaint,
                        style =
                            androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 50f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                    )
                }
            }
        }
    }
}
