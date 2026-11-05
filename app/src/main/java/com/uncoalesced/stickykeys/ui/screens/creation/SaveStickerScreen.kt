// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.ui.components.DecodedImage
import com.uncoalesced.stickykeys.ui.components.ErrorScreen
import com.uncoalesced.stickykeys.ui.components.rememberDecodedImage

@Composable
fun SaveStickerScreen(
    uriString: String,
    viewModel: SaveStickerViewModel = hiltViewModel(),
    onSaveComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val decoded = context.rememberDecodedImage(uriString)
    var isSaving by remember { mutableStateOf(false) }
    val saveFailedMessage = stringResource(R.string.text_sticker_save_failed)

    if (decoded is DecodedImage.Failed) {
        ErrorScreen(stringResource(R.string.text_image_load_failed), onBack = onCancel)
        return
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.text_save_sticker)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(
                            stringResource(R.string.text_cancel),
                            color = StickyKeysTheme.colors.error,
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
            verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.lg),
        ) {
            if (decoded is DecodedImage.Ready) {
                val bitmap = decoded.bitmap
                Box(
                    modifier =
                        Modifier
                            .size(200.dp)
                            .background(
                                StickyKeysTheme.colors.surfaceVariant,
                                StickyKeysTheme.shapes.medium,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.desc_preview),
                        modifier = Modifier.fillMaxSize().padding(StickyKeysTheme.spacing.sm),
                    )
                }

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        viewModel.saveSticker(
                            bitmap = bitmap,
                            onComplete = onSaveComplete,
                            onFailure = {
                                isSaving = false
                                Toast.makeText(context, saveFailedMessage, Toast.LENGTH_LONG).show()
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = StickyKeysTheme.colors.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.text_save_to_library))
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
