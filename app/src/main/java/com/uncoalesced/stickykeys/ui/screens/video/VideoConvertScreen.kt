// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.video

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.stickercore.animation.ConversionQuality

@Composable
fun VideoConvertScreen(
    videoUriString: String,
    startMs: Long,
    endMs: Long,
    viewModel: VideoConvertViewModel = hiltViewModel(),
    onConversionComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // The conversion itself lives in the ViewModel, so a rotation cannot cancel it. All
    // this effect does is forward the terminal state once.
    LaunchedEffect(uiState) {
        if (uiState is VideoConvertUiState.Done) {
            onConversionComplete()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = uiState) {
            is VideoConvertUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.md),
                ) {
                    Text(
                        stringResource(R.string.text_conversion_error),
                        style = StickyKeysTheme.typography.titleMedium,
                        color = StickyKeysTheme.colors.error,
                    )
                    Text(state.message, style = StickyKeysTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.text_back))
                        }
                        Button(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.text_convert_to_gif))
                        }
                    }
                }
            }

            is VideoConvertUiState.Converting -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.md),
                    modifier = Modifier.padding(StickyKeysTheme.spacing.lg),
                ) {
                    Text(
                        stringResource(R.string.text_creating_gif),
                        style = StickyKeysTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(state.progress * 100).toInt()}%",
                        style = StickyKeysTheme.typography.bodyMedium,
                    )
                }
            }

            // Done is terminal and handled by the effect above; the picker stays on screen
            // for the single frame before navigation happens.
            is VideoConvertUiState.Idle, VideoConvertUiState.Done -> {
                val quality =
                    (state as? VideoConvertUiState.Idle)?.quality ?: ConversionQuality.HIGH
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.md),
                    modifier = Modifier.padding(StickyKeysTheme.spacing.lg),
                ) {
                    Text(
                        stringResource(R.string.text_select_output_quality),
                        style = StickyKeysTheme.typography.titleMedium,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConversionQuality.entries.forEach { option ->
                            FilterChip(
                                selected = quality == option,
                                onClick = { viewModel.selectQuality(option) },
                                label = { Text(option.name) },
                            )
                        }
                    }

                    Text(
                        "Settings: ${quality.maxDimensionPx}px max dimension, ${quality.fps} fps",
                        style = StickyKeysTheme.typography.bodyMedium,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = onCancel) {
                            Text(
                                stringResource(R.string.text_cancel),
                                color = StickyKeysTheme.colors.error,
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.startConversion(
                                    context,
                                    videoUriString,
                                    startMs,
                                    endMs,
                                )
                            },
                        ) {
                            Text(stringResource(R.string.text_convert_to_gif))
                        }
                    }
                }
            }
        }
    }
}
