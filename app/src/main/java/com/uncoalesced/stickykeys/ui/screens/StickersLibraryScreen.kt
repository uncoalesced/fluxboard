package com.uncoalesced.stickykeys.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.uncoalesced.stickykeys.ui.components.LoadingScreen
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import android.graphics.BitmapFactory
import androidx.compose.runtime.remember

sealed interface StickersUiState {
    data object Loading : StickersUiState
    data class Success(val stickers: List<Sticker>) : StickersUiState
}

@HiltViewModel
class StickersViewModel @Inject constructor(
    repository: StickerRepository
) : ViewModel() {
    val uiState: StateFlow<StickersUiState> = repository.getAllStickers()
        .map { stickers -> StickersUiState.Success(stickers) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StickersUiState.Loading)
}

@Composable
fun StickersLibraryScreen(
    viewModel: StickersViewModel = hiltViewModel(),
    onImagePicked: (String) -> Unit = {},
    onStickerClick: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onImagePicked(it.toString()) }
    }

    when (val current = state) {
        is StickersUiState.Loading -> LoadingScreen()
        is StickersUiState.Success -> {
            Scaffold(
                containerColor = StickyKeysTheme.colors.background,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        containerColor = StickyKeysTheme.colors.primary,
                        contentColor = StickyKeysTheme.colors.onPrimary
                    ) {
                        Text("+")
                    }
                }
            ) { paddingValues ->
                if (current.stickers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No stickers yet. Tap + to add one!",
                            style = StickyKeysTheme.typography.bodyMedium,
                            color = StickyKeysTheme.colors.onBackground
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        contentPadding = PaddingValues(StickyKeysTheme.spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        items(current.stickers, key = { it.id }) { sticker ->
                            val bitmap = remember(sticker.thumbnailFile) {
                                if (sticker.thumbnailFile.exists()) {
                                    BitmapFactory.decodeFile(sticker.thumbnailFile.absolutePath)
                                } else null
                            }
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clickable { onStickerClick(sticker.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Sticker",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("Sticker", style = StickyKeysTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
