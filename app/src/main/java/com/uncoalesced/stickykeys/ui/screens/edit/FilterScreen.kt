// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
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
fun FilterScreen(
    uriString: String,
    onApplyFilter: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var brightness by remember { mutableFloatStateOf(0f) } // -100 to 100
    var contrast by remember { mutableFloatStateOf(1f) }   // 0 to 2
    var saturation by remember { mutableFloatStateOf(1f) } // 0 to 2

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

    // Build Compose ColorMatrix for real-time rendering
    val composeColorMatrix = remember(brightness, contrast, saturation) {
        val cm = ColorMatrix()
        // Saturation
        val satCm = ColorMatrix()
        satCm.setSaturation(saturation)
        cm.postConcat(satCm)

        // Contrast
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val conCm = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(conCm)

        // Brightness
        val brightCm = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(brightCm)

        ComposeColorMatrix(cm.array)
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Adjust Filters") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel", color = StickyKeysTheme.colors.error) }
                },
                actions = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val cachedUri = withContext(Dispatchers.IO) {
                                val bmp = originalBitmap!!
                                val resultBmp = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(resultBmp)
                                val paint = Paint()
                                paint.colorFilter = ColorMatrixColorFilter(composeColorMatrix.values)
                                canvas.drawBitmap(bmp, 0f, 0f, paint)

                                val cacheFile = File(context.cacheDir, "filter_${UUID.randomUUID()}.png")
                                FileOutputStream(cacheFile).use { out ->
                                    resultBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                Uri.fromFile(cacheFile).toString()
                            }
                            onApplyFilter(cachedUri)
                        }
                    }) {
                        Text("Apply", color = StickyKeysTheme.colors.primary)
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
                Image(
                    bitmap = originalBitmap!!.asImageBitmap(),
                    contentDescription = "Filter Preview",
                    colorFilter = ColorFilter.colorMatrix(composeColorMatrix),
                    modifier = Modifier.fillMaxSize().padding(StickyKeysTheme.spacing.sm)
                )
            }

            Spacer(modifier = Modifier.height(StickyKeysTheme.spacing.md))

            // Sliders Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StickyKeysTheme.colors.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(StickyKeysTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm)
                ) {
                    Text("Brightness (${brightness.toInt()})", style = StickyKeysTheme.typography.bodyMedium)
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = -100f..100f
                    )

                    Text("Contrast (${String.format("%.2f", contrast)})", style = StickyKeysTheme.typography.bodyMedium)
                    Slider(
                        value = contrast,
                        onValueChange = { contrast = it },
                        valueRange = 0f..2f
                    )

                    Text("Saturation (${String.format("%.2f", saturation)})", style = StickyKeysTheme.typography.bodyMedium)
                    Slider(
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = 0f..2f
                    )
                }
            }
        }
    }
}
