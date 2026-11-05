// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.ui.components.ErrorScreen
import com.uncoalesced.stickykeys.ui.components.ImageFit
import com.uncoalesced.stickykeys.ui.components.LoadingScreen
import com.uncoalesced.stickykeys.ui.components.attempt
import com.uncoalesced.stickykeys.ui.components.decodeBitmap
import com.uncoalesced.stickykeys.ui.components.writeBitmapToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas as ComposeCanvas

@Composable
fun TextOverlayScreen(
    uriString: String,
    onApplyText: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    var text by remember { mutableStateOf("Sticky!") }
    var textPosition by remember { mutableStateOf(Offset(200f, 200f)) }
    var textSize by remember { mutableFloatStateOf(60f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val saveFailedMessage = stringResource(R.string.text_sticker_save_failed)

    // Preset theme colors. `secondary` used to be the fourth: it is the border grey in both
    // palettes, the same value as the card these swatches sit on, so it drew as an empty gap.
    val availableColors =
        listOf(
            Color.White,
            Color.Black,
            StickyKeysTheme.colors.primary,
            StickyKeysTheme.colors.accentText,
            StickyKeysTheme.colors.error,
        )
    var selectedColor by remember { mutableStateOf(availableColors.first()) }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            originalBitmap = context.decodeBitmap(uriString)
            loadFailed = originalBitmap == null
        }
    }

    if (originalBitmap == null) {
        if (loadFailed) {
            ErrorScreen(stringResource(R.string.text_image_load_failed), onBack = onCancel)
        } else {
            LoadingScreen()
        }
        return
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.text_add_text_overlay)) },
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
                                attempt {
                                    withContext(Dispatchers.IO) {
                                        val bmp = originalBitmap!!
                                        val fit =
                                            ImageFit.of(
                                                canvasSize.width.toFloat(),
                                                canvasSize.height.toFloat(),
                                                bmp.width,
                                                bmp.height,
                                            )
                                        writeBitmapToCache(
                                            context.cacheDir,
                                            "text",
                                            renderTextOverlay(
                                                source = bmp,
                                                text = text,
                                                colorArgb = selectedColor.toArgb(),
                                                fit = fit,
                                                viewX = textPosition.x,
                                                viewY = textPosition.y,
                                                viewTextSize = textSize,
                                            ),
                                        )
                                    }
                                }.getOrNull()
                            if (cachedUri != null) {
                                onApplyText(cachedUri)
                            } else {
                                Toast
                                    .makeText(context, saveFailedMessage, Toast.LENGTH_LONG)
                                    .show()
                            }
                        }
                    }) {
                        Text(
                            stringResource(R.string.text_apply),
                            color = StickyKeysTheme.colors.accentText,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(StickyKeysTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black, StickyKeysTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                val imgBitmap = originalBitmap!!.asImageBitmap()
                ComposeCanvas(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    textPosition += dragAmount
                                }
                            },
                ) {
                    val fit =
                        ImageFit.of(size.width, size.height, imgBitmap.width, imgBitmap.height)
                    withTransform({
                        translate(fit.left, fit.top)
                        scale(fit.scale, fit.scale, pivot = Offset.Zero)
                    }) {
                        drawImage(imgBitmap, topLeft = Offset.Zero)
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        text,
                        textPosition.x,
                        textPosition.y,
                        textOverlayPaint(selectedColor.toArgb(), textSize),
                    )
                }
            }

            Spacer(modifier = Modifier.height(StickyKeysTheme.spacing.md))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = StickyKeysTheme.colors.surfaceVariant,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(StickyKeysTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.text_sticker_text)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        "Text Size (${textSize.toInt()})",
                        style = StickyKeysTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = textSize,
                        onValueChange = { textSize = it },
                        valueRange = 20f..150f,
                    )

                    Text(
                        stringResource(R.string.text_color),
                        style = StickyKeysTheme.typography.bodyMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm),
                    ) {
                        availableColors.forEachIndexed { index, color ->
                            val isSelected = color == selectedColor
                            val swatchLabel =
                                stringResource(
                                    R.string.text_color_swatch,
                                    index + 1,
                                    availableColors.size,
                                )
                            // Every swatch gets a thin ring in the muted ink, so no palette colour
                            // can disappear into the card. The chosen one also gets an outer ring
                            // with a gap of card colour before it, so the mark stays visible even
                            // when the ring and the swatch are the same colour.
                            Box(
                                modifier =
                                    Modifier
                                        .size(44.dp)
                                        .border(
                                            width = 2.dp,
                                            color =
                                                if (isSelected) {
                                                    StickyKeysTheme.colors.onSurface
                                                } else {
                                                    Color.Transparent
                                                },
                                            shape = CircleShape,
                                        ).padding(4.dp),
                            ) {
                                Button(
                                    onClick = { selectedColor = color },
                                    colors = ButtonDefaults.buttonColors(containerColor = color),
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            StickyKeysTheme.colors.onSurfaceVariant,
                                        ),
                                    modifier =
                                        Modifier.size(36.dp).semantics {
                                            contentDescription = swatchLabel
                                            selected = isSelected
                                        },
                                    contentPadding = PaddingValues(0.dp),
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Built with a parameter rather than inside `apply` on the screen: there, `textSize = textSize`
 * assigns the Paint's own field to itself, so the size slider never did anything.
 */
internal fun textOverlayPaint(
    colorArgb: Int,
    size: Float,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = colorArgb
        it.textSize = size
        it.style = Paint.Style.FILL
    }

/** A copy of [source] with [text] drawn where it appears on screen, at the size it appears. */
internal fun renderTextOverlay(
    source: Bitmap,
    text: String,
    colorArgb: Int,
    fit: ImageFit,
    viewX: Float,
    viewY: Float,
    viewTextSize: Float,
): Bitmap {
    val result = source.copy(Bitmap.Config.ARGB_8888, true)
    Canvas(result).drawText(
        text,
        fit.toBitmapX(viewX),
        fit.toBitmapY(viewY),
        textOverlayPaint(colorArgb, fit.toBitmapLength(viewTextSize)),
    )
    return result
}
