// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.edit

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

@Composable
fun TextOverlayScreen(
    uriString: String,
    onApplyText: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var text by remember { mutableStateOf("Sticky!") }
    var textPosition by remember { mutableStateOf(Offset(200f, 200f)) }
    var textSize by remember { mutableFloatStateOf(60f) }
    
    // Preset theme colors
    val availableColors = listOf(
        Color.White,
        Color.Black,
        StickyKeysTheme.colors.primary,
        StickyKeysTheme.colors.secondary,
        StickyKeysTheme.colors.error
    )
    var selectedColor by remember { mutableStateOf(availableColors.first()) }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val stream = context.contentResolver.openInputStream(uri)
                originalBitmap = BitmapFactory.decodeStream(stream)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (originalBitmap == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.text_add_text_overlay)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.text_cancel), color = StickyKeysTheme.colors.error) }
                },
                actions = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val cachedUri = withContext(Dispatchers.IO) {
                                val bmp = originalBitmap!!
                                val resultBmp = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(resultBmp)
                                canvas.drawBitmap(bmp, 0f, 0f, null)

                                val paint = Paint().apply {
                                    color = selectedColor.toArgb()
                                    textSize = textSize
                                    isAntiAlias = true
                                    style = Paint.Style.FILL
                                }
                                canvas.drawText(text, textPosition.x, textPosition.y, paint)

                                val cacheFile = File(context.cacheDir, "text_${UUID.randomUUID()}.png")
                                FileOutputStream(cacheFile).use { out ->
                                    resultBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                Uri.fromFile(cacheFile).toString()
                            }
                            onApplyText(cachedUri)
                        }
                    }) {
                        Text(stringResource(R.string.text_apply), color = StickyKeysTheme.colors.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(StickyKeysTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, StickyKeysTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                val imgBitmap = originalBitmap!!.asImageBitmap()
                ComposeCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                textPosition += dragAmount
                            }
                        }
                ) {
                    val scale = minOf(size.width / imgBitmap.width, size.height / imgBitmap.height)
                    val x = (size.width - imgBitmap.width * scale) / 2
                    val y = (size.height - imgBitmap.height * scale) / 2

                    drawImage(imgBitmap, topLeft = Offset(x, y))

                    drawContext.canvas.nativeCanvas.drawText(
                        text,
                        textPosition.x,
                        textPosition.y,
                        Paint().apply {
                            color = selectedColor.toArgb()
                            textSize = textSize
                            isAntiAlias = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(StickyKeysTheme.spacing.md))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StickyKeysTheme.colors.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(StickyKeysTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.text_sticker_text)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Text Size (${textSize.toInt()})", style = StickyKeysTheme.typography.bodyMedium)
                    Slider(
                        value = textSize,
                        onValueChange = { textSize = it },
                        valueRange = 20f..150f
                    )

                    Text(stringResource(R.string.text_color), style = StickyKeysTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm)
                    ) {
                        availableColors.forEach { color ->
                            Button(
                                onClick = { selectedColor = color },
                                colors = ButtonDefaults.buttonColors(containerColor = color),
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {}
                        }
                    }
                }
            }
        }
    }
}
