// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class TouchUpMode {
    Erase, Restore
}

@Composable
fun TouchUpScreen(
    originalUriString: String,
    segmentedUriString: String,
    onTouchUpComplete: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var originalBmp by remember { mutableStateOf<Bitmap?>(null) }
    var segmentedBmp by remember { mutableStateOf<Bitmap?>(null) }

    var activeMode by remember { mutableStateOf(TouchUpMode.Erase) }
    var brushSize by remember { mutableFloatStateOf(50f) }

    val erasePaths = remember { mutableStateListOf<Path>() }
    val restorePaths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    LaunchedEffect(originalUriString, segmentedUriString) {
        withContext(Dispatchers.IO) {
            try {
                val origStream = context.contentResolver.openInputStream(Uri.parse(originalUriString))
                val rawOrig = BitmapFactory.decodeStream(origStream)

                val segStream = context.contentResolver.openInputStream(Uri.parse(segmentedUriString))
                val rawSeg = BitmapFactory.decodeStream(segStream)

                originalBmp = rawOrig
                segmentedBmp = rawSeg.copy(Bitmap.Config.ARGB_8888, true)
                rawSeg.recycle()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (segmentedBmp == null || originalBmp == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.text_touch_up_cutout)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.text_cancel), color = StickyKeysTheme.colors.error) }
                },
                actions = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val cachedUri = withContext(Dispatchers.IO) {
                                val resultBmp = segmentedBmp!!.copy(Bitmap.Config.ARGB_8888, true)
                                val cacheFile = File(context.cacheDir, "touchup_${UUID.randomUUID()}.png")
                                FileOutputStream(cacheFile).use { out ->
                                    resultBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                Uri.fromFile(cacheFile).toString()
                            }
                            onTouchUpComplete(cachedUri)
                        }
                    }) {
                        Text(stringResource(R.string.text_next), color = StickyKeysTheme.colors.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.DarkGray)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val imgBitmap = segmentedBmp!!.asImageBitmap()

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                },
                                onDrag = { change, _ ->
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    val p = currentPath
                                    currentPath = null
                                    currentPath = p
                                },
                                onDragEnd = {
                                    currentPath?.let { path ->
                                        if (activeMode == TouchUpMode.Erase) erasePaths.add(path)
                                        else restorePaths.add(path)
                                    }
                                    currentPath = null
                                }
                            )
                        }
                ) {
                    val scale = minOf(size.width / imgBitmap.width, size.height / imgBitmap.height)
                    val x = (size.width - imgBitmap.width * scale) / 2
                    val y = (size.height - imgBitmap.height * scale) / 2

                    withTransform({
                        translate(x, y)
                        scale(scale, scale)
                    }) {
                        drawImage(imgBitmap, topLeft = Offset.Zero)
                    }

                    // Render Erase Strokes (Red preview overlay)
                    val eraseColor = Color.Red.copy(alpha = 0.5f)
                    erasePaths.forEach { path ->
                        drawPath(path, eraseColor, style = Stroke(width = brushSize, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    // Render Restore Strokes (Green preview overlay)
                    val restoreColor = Color.Green.copy(alpha = 0.5f)
                    restorePaths.forEach { path ->
                        drawPath(path, restoreColor, style = Stroke(width = brushSize, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    // Current Active Stroke
                    currentPath?.let { path ->
                        val strokeColor = if (activeMode == TouchUpMode.Erase) eraseColor else restoreColor
                        drawPath(path, strokeColor, style = Stroke(width = brushSize, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }

            // Controls Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StickyKeysTheme.colors.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(StickyKeysTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            selected = activeMode == TouchUpMode.Erase,
                            onClick = { activeMode = TouchUpMode.Erase },
                            label = { Text(stringResource(R.string.text_erase_background)) }
                        )
                        FilterChip(
                            selected = activeMode == TouchUpMode.Restore,
                            onClick = { activeMode = TouchUpMode.Restore },
                            label = { Text(stringResource(R.string.text_restore_subject)) }
                        )
                    }

                    Text("Brush Size (${brushSize.toInt()}px)", style = StickyKeysTheme.typography.bodyMedium)
                    Slider(
                        value = brushSize,
                        onValueChange = { brushSize = it },
                        valueRange = 10f..120f
                    )
                }
            }
        }
    }
}
