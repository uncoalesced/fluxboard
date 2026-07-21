// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.edit

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.ui.screens.creation.CropScreen
import com.uncoalesced.stickykeys.ui.screens.creation.EraseScreen

enum class EditTool {
    Overview, Crop, Erase, Filter, Text
}

@Composable
fun EditStickerScreen(
    stickerId: String,
    viewModel: EditStickerViewModel = hiltViewModel(),
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(stickerId) {
        viewModel.loadSticker(stickerId)
    }

    when (val state = uiState) {
        is EditStickerUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is EditStickerUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = StickyKeysTheme.colors.error)
            }
        }
        is EditStickerUiState.Success -> {
            var activeTool by remember { mutableStateOf(EditTool.Overview) }

            when (activeTool) {
                EditTool.Overview -> {
                    EditOverviewScreen(
                        currentUriString = state.currentUriString,
                        onSelectTool = { activeTool = it },
                        onOverwrite = {
                            viewModel.overwriteSticker(context, onComplete)
                        },
                        onSaveAsNew = {
                            viewModel.saveAsNewSticker(context, onComplete)
                        },
                        onCancel = onCancel
                    )
                }
                EditTool.Crop -> {
                    CropScreen(
                        uriString = state.currentUriString,
                        onCropComplete = { newUri ->
                            viewModel.updateCurrentUri(newUri)
                            activeTool = EditTool.Overview
                        },
                        onCancel = { activeTool = EditTool.Overview }
                    )
                }
                EditTool.Erase -> {
                    EraseScreen(
                        uriString = state.currentUriString,
                        onEraseComplete = { newUri ->
                            viewModel.updateCurrentUri(newUri)
                            activeTool = EditTool.Overview
                        },
                        onCancel = { activeTool = EditTool.Overview }
                    )
                }
                EditTool.Filter -> {
                    FilterScreen(
                        uriString = state.currentUriString,
                        onApplyFilter = { newUri ->
                            viewModel.updateCurrentUri(newUri)
                            activeTool = EditTool.Overview
                        },
                        onCancel = { activeTool = EditTool.Overview }
                    )
                }
                EditTool.Text -> {
                    TextOverlayScreen(
                        uriString = state.currentUriString,
                        onApplyText = { newUri ->
                            viewModel.updateCurrentUri(newUri)
                            activeTool = EditTool.Overview
                        },
                        onCancel = { activeTool = EditTool.Overview }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditOverviewScreen(
    currentUriString: String,
    onSelectTool: (EditTool) -> Unit,
    onOverwrite: () -> Unit,
    onSaveAsNew: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(currentUriString) {
        mutableStateOf(
            try {
                val stream = context.contentResolver.openInputStream(Uri.parse(currentUriString))
                BitmapFactory.decodeStream(stream)
            } catch (e: Exception) {
                null
            }
        )
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Edit Sticker") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel", color = StickyKeysTheme.colors.error) }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(StickyKeysTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(StickyKeysTheme.colors.surfaceVariant, StickyKeysTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Edited Preview",
                        modifier = Modifier.fillMaxSize().padding(StickyKeysTheme.spacing.sm)
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            // Tools options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = { onSelectTool(EditTool.Crop) }) { Text("Crop") }
                OutlinedButton(onClick = { onSelectTool(EditTool.Erase) }) { Text("Erase") }
                OutlinedButton(onClick = { onSelectTool(EditTool.Filter) }) { Text("Filter") }
                OutlinedButton(onClick = { onSelectTool(EditTool.Text) }) { Text("Text") }
            }

            // Save choices
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.xs)
            ) {
                Button(
                    onClick = onOverwrite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Overwrite Sticker")
                }
                OutlinedButton(
                    onClick = onSaveAsNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save as New Sticker")
                }
            }
        }
    }
}
