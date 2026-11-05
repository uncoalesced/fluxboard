// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.ui.components.DecodedImage
import com.uncoalesced.stickykeys.ui.components.ErrorScreen
import com.uncoalesced.stickykeys.ui.components.ImageFit
import com.uncoalesced.stickykeys.ui.components.LoadingScreen
import com.uncoalesced.stickykeys.ui.components.attempt
import com.uncoalesced.stickykeys.ui.components.rememberDecodedImage
import com.uncoalesced.stickykeys.ui.components.writeBitmapToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Brush width on screen. Converted per stroke, so it looks the same on any image size. */
private const val ERASER_BRUSH_WIDTH_PX = 50f

@Composable
fun EraseScreen(
    uriString: String,
    onEraseComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val decoded = context.rememberDecodedImage(uriString)
    val saveFailedMessage = stringResource(R.string.text_sticker_save_failed)

    val strokes = remember { mutableStateListOf<EraseStroke>() }
    var currentStroke by remember { mutableStateOf<EraseStroke?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    if (decoded is DecodedImage.Failed) {
        ErrorScreen(stringResource(R.string.text_image_load_failed), onBack = onCancel)
        return
    }
    if (decoded !is DecodedImage.Ready) {
        LoadingScreen()
        return
    }
    val source = decoded.bitmap

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
                    // One stroke at a time. The list drives both the preview and the saved
                    // image, so removing a stroke here also keeps it out of the sticker.
                    TextButton(
                        enabled = !isSaving && strokes.isNotEmpty(),
                        onClick = { strokes.removeAt(strokes.lastIndex) },
                    ) {
                        Text(stringResource(R.string.text_undo))
                    }
                    TextButton(
                        enabled = !isSaving,
                        onClick = {
                            isSaving = true
                            val finished = strokes.toList()
                            coroutineScope.launch {
                                val cachedUri =
                                    attempt {
                                        withContext(Dispatchers.IO) {
                                            writeBitmapToCache(
                                                context.cacheDir,
                                                "erased",
                                                applyEraseStrokes(source, finished),
                                            )
                                        }
                                    }.getOrNull()
                                isSaving = false
                                if (cachedUri != null) {
                                    onEraseComplete(cachedUri)
                                } else {
                                    Toast
                                        .makeText(context, saveFailedMessage, Toast.LENGTH_LONG)
                                        .show()
                                }
                            }
                        },
                    ) {
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
                    .background(Color.DarkGray),
        ) {
            val imgBitmap = source.asImageBitmap()
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // Clearing needs a layer of its own, or it would punch through to the
                        // window rather than revealing the background behind this image.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .pointerInput(source) {
                            fun fit() =
                                ImageFit.of(
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                    source.width,
                                    source.height,
                                )

                            fun pointAt(position: Offset): EraseStrokePoint {
                                val fit = fit()
                                return EraseStrokePoint(
                                    fit.toBitmapX(position.x),
                                    fit.toBitmapY(position.y),
                                )
                            }
                            detectDragGestures(
                                onDragStart = { position ->
                                    currentStroke =
                                        EraseStroke(
                                            points = listOf(pointAt(position)),
                                            width = fit().toBitmapLength(ERASER_BRUSH_WIDTH_PX),
                                        )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke =
                                        currentStroke?.let {
                                            it.copy(points = it.points + pointAt(change.position))
                                        }
                                },
                                onDragEnd = {
                                    currentStroke?.let { strokes.add(it) }
                                    currentStroke = null
                                },
                                onDragCancel = { currentStroke = null },
                            )
                        },
            ) {
                val fit = ImageFit.of(size.width, size.height, source.width, source.height)
                withTransform({
                    translate(fit.left, fit.top)
                    scale(fit.scale, fit.scale, pivot = Offset.Zero)
                }) {
                    drawImage(imgBitmap, topLeft = Offset.Zero)
                    (strokes + listOfNotNull(currentStroke)).forEach { stroke ->
                        drawPath(
                            erasePathOf(stroke).asComposePath(),
                            Color.Black,
                            style =
                                Stroke(
                                    width = stroke.width,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            blendMode = BlendMode.Clear,
                        )
                    }
                }
            }
        }
    }
}
