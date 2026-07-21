// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.stickercore.domain.model.Category
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import com.uncoalesced.stickykeys.ui.components.LoadingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed interface TabFilter {
    data object All : TabFilter
    data object Favourites : TabFilter
    data class CategoryFilter(val category: Category) : TabFilter
}

sealed interface StickersUiState {
    data object Loading : StickersUiState
    data class Success(
        val stickers: List<Sticker>,
        val categories: List<Category>,
        val currentTab: TabFilter
    ) : StickersUiState
}

@HiltViewModel
class StickersViewModel @Inject constructor(
    private val repository: StickerRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow<TabFilter>(TabFilter.All)
    val selectedTab: StateFlow<TabFilter> = _selectedTab.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stickersFlow: Flow<List<Sticker>> = _selectedTab.flatMapLatest { tab ->
        when (tab) {
            is TabFilter.All -> repository.getAllStickers()
            is TabFilter.Favourites -> repository.getFavouriteStickers()
            is TabFilter.CategoryFilter -> repository.getStickersByCategory(tab.category.id)
        }
    }

    val uiState: StateFlow<StickersUiState> = combine(
        stickersFlow,
        repository.getAllCategories(),
        _selectedTab
    ) { stickers, categories, currentTab ->
        StickersUiState.Success(
            stickers = stickers,
            categories = categories,
            currentTab = currentTab
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        StickersUiState.Loading
    )

    private val _isBatchImporting = MutableStateFlow(false)
    val isBatchImporting: StateFlow<Boolean> = _isBatchImporting.asStateFlow()

    fun batchImportStickers(uriStrings: List<String>, context: android.content.Context) {
        if (uriStrings.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isBatchImporting.value = true
            val engine = com.uncoalesced.stickykeys.stickercore.segmentation.MlKitSegmentationEngine()
            uriStrings.forEach { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    val stream = context.contentResolver.openInputStream(uri)
                    val rawBmp = BitmapFactory.decodeStream(stream)
                    if (rawBmp != null) {
                        val segResult = engine.segmentSubject(context, rawBmp)
                        val finalBmp = segResult.getOrDefault(rawBmp)

                        val stickerId = UUID.randomUUID().toString()
                        val webpBytes = ByteArrayOutputStream().apply {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                finalBmp.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS, 100, this)
                            } else {
                                @Suppress("DEPRECATION")
                                finalBmp.compress(android.graphics.Bitmap.CompressFormat.WEBP, 100, this)
                            }
                        }.toByteArray()

                        val thumbBytes = ByteArrayOutputStream().apply {
                            val thumbBmp = android.graphics.Bitmap.createScaledBitmap(finalBmp, 256, 256, true)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                thumbBmp.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 80, this)
                            } else {
                                @Suppress("DEPRECATION")
                                thumbBmp.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, this)
                            }
                        }.toByteArray()

                        val sticker = Sticker(
                            id = stickerId,
                            packId = null,
                            categoryId = null,
                            isFavourite = false,
                            createdAt = System.currentTimeMillis(),
                            mimeType = "image/webp",
                            file = File(""),
                            thumbnailFile = File("")
                        )
                        repository.saveSticker(sticker, webpBytes, thumbBytes)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _isBatchImporting.value = false
        }
    }

    fun selectTab(tab: TabFilter) {
        _selectedTab.value = tab
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newCategory = Category(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                sortOrder = System.currentTimeMillis().toInt()
            )
            repository.saveCategory(newCategory)
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            if (_selectedTab.value is TabFilter.CategoryFilter && (_selectedTab.value as TabFilter.CategoryFilter).category.id == id) {
                _selectedTab.value = TabFilter.All
            }
        }
    }

    fun toggleFavourite(stickerId: String) {
        viewModelScope.launch {
            repository.toggleFavourite(stickerId)
        }
    }

    fun assignCategory(sticker: Sticker, categoryId: String?) {
        viewModelScope.launch {
            repository.updateStickerMetadata(sticker.copy(categoryId = categoryId))
        }
    }

    fun deleteSticker(stickerId: String) {
        viewModelScope.launch {
            repository.deleteSticker(stickerId)
        }
    }
}

@Composable
fun StickersLibraryScreen(
    viewModel: StickersViewModel = hiltViewModel(),
    onImagePicked: (String) -> Unit = {},
    onVideoPicked: (String) -> Unit = {},
    onStickerClick: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var stickerForMenu by remember { mutableStateOf<Sticker?>(null) }
    var pendingImportUris by remember { mutableStateOf<List<String>>(emptyList()) }
    val isBatchImporting by viewModel.isBatchImporting.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingImportUris = uris.map { it.toString() }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onVideoPicked(it.toString()) }
    }

    when (val current = state) {
        is StickersUiState.Loading -> LoadingScreen()
        is StickersUiState.Success -> {
            Scaffold(
                containerColor = StickyKeysTheme.colors.background,
                floatingActionButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val context = LocalContext.current
                        ExtendedFloatingActionButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                )
                            },
                            containerColor = StickyKeysTheme.colors.surfaceVariant,
                            contentColor = StickyKeysTheme.colors.onBackground
                        ) {
                            Text("Import Video")
                        }

                        ExtendedFloatingActionButton(
                            onClick = {
                                val screenshotUri = com.uncoalesced.stickykeys.capture.ScreenshotHelper.getLastScreenshotUri(context)
                                if (screenshotUri != null) {
                                    onImagePicked(screenshotUri.toString())
                                }
                            },
                            containerColor = StickyKeysTheme.colors.secondary,
                            contentColor = StickyKeysTheme.colors.onSecondary
                        ) {
                            Text("Extract Screenshot")
                        }

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
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Category & Filter Tabs
                    ScrollableTabRow(
                        selectedTabIndex = when (current.currentTab) {
                            is TabFilter.All -> 0
                            is TabFilter.Favourites -> 1
                            is TabFilter.CategoryFilter -> 2 + current.categories.indexOfFirst { it.id == (current.currentTab as TabFilter.CategoryFilter).category.id }
                        }.coerceAtLeast(0),
                        edgePadding = StickyKeysTheme.spacing.sm,
                        containerColor = StickyKeysTheme.colors.surfaceVariant
                    ) {
                        Tab(
                            selected = current.currentTab is TabFilter.All,
                            onClick = { viewModel.selectTab(TabFilter.All) },
                            text = { Text("All") }
                        )
                        Tab(
                            selected = current.currentTab is TabFilter.Favourites,
                            onClick = { viewModel.selectTab(TabFilter.Favourites) },
                            text = { Text("★ Favourites") }
                        )
                        current.categories.forEach { category ->
                            Tab(
                                selected = current.currentTab is TabFilter.CategoryFilter && (current.currentTab as TabFilter.CategoryFilter).category.id == category.id,
                                onClick = { viewModel.selectTab(TabFilter.CategoryFilter(category)) },
                                text = { Text(category.name) }
                            )
                        }
                        IconButton(onClick = { showAddCategoryDialog = true }) {
                            Text("+", style = StickyKeysTheme.typography.titleMedium)
                        }
                    }

                    // Stickers Grid
                    if (current.stickers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No stickers in this view.",
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
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(current.stickers, key = { it.id }) { sticker ->
                                StickerGridItem(
                                    sticker = sticker,
                                    onStickerClick = { onStickerClick(sticker.id) },
                                    onToggleFavourite = { viewModel.toggleFavourite(sticker.id) },
                                    onLongClick = { stickerForMenu = sticker }
                                )
                            }
                        }
                    }
                }
            }

            // Dialog for adding Category
            if (showAddCategoryDialog) {
                AddCategoryDialog(
                    onDismiss = { showAddCategoryDialog = false },
                    onAddCategory = { name ->
                        viewModel.addCategory(name)
                        showAddCategoryDialog = false
                    }
                )
            }

            // Context Options for Sticker
            if (stickerForMenu != null) {
                StickerContextMenuDialog(
                    sticker = stickerForMenu!!,
                    categories = current.categories,
                    onDismiss = { stickerForMenu = null },
                    onAssignCategory = { catId ->
                        viewModel.assignCategory(stickerForMenu!!, catId)
                        stickerForMenu = null
                    },
                    onDeleteSticker = {
                        viewModel.deleteSticker(stickerForMenu!!.id)
                        stickerForMenu = null
                    }
                )
            }

            // Dialog for choosing Import Mode
            if (pendingImportUris.isNotEmpty()) {
                val context = LocalContext.current
                AlertDialog(
                    onDismissRequest = { pendingImportUris = emptyList() },
                    title = { Text("Import ${pendingImportUris.size} Images") },
                    text = { Text("Choose how to process your selected photos:") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val uris = pendingImportUris
                                pendingImportUris = emptyList()
                                viewModel.batchImportStickers(uris, context)
                            }
                        ) {
                            Text("⚡ Auto-Segment (Batch)")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = {
                                val firstUri = pendingImportUris.first()
                                pendingImportUris = emptyList()
                                onImagePicked(firstUri)
                            }
                        ) {
                            Text("✏️ Custom Edit")
                        }
                    }
                )
            }

            if (isBatchImporting) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Batch Importing...") },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text("Segmenting & saving stickers...")
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerGridItem(
    sticker: Sticker,
    onStickerClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onLongClick: () -> Unit
) {
    val bitmap = remember(sticker.thumbnailFile) {
        if (sticker.thumbnailFile.exists()) {
            BitmapFactory.decodeFile(sticker.thumbnailFile.absolutePath)
        } else null
    }

    Card(
        modifier = Modifier
            .size(100.dp)
            .combinedClickable(
                onClick = onStickerClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = StickyKeysTheme.colors.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Sticker",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(StickyKeysTheme.spacing.xs)
                )
            } else {
                Text(
                    "Sticker",
                    style = StickyKeysTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            IconButton(
                onClick = onToggleFavourite,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
            ) {
                Text(
                    text = if (sticker.isFavourite) "★" else "☆",
                    color = if (sticker.isFavourite) StickyKeysTheme.colors.primary else StickyKeysTheme.colors.onBackground
                )
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Category Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onAddCategory(text) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StickerContextMenuDialog(
    sticker: Sticker,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAssignCategory: (String?) -> Unit,
    onDeleteSticker: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sticker Options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.xs)) {
                Text("Assign Category:", style = StickyKeysTheme.typography.bodyMedium)
                TextButton(onClick = { onAssignCategory(null) }) {
                    Text(if (sticker.categoryId == null) "• None (Unassigned)" else "None (Unassigned)")
                }
                categories.forEach { cat ->
                    TextButton(onClick = { onAssignCategory(cat.id) }) {
                        Text(if (sticker.categoryId == cat.id) "• ${cat.name}" else cat.name)
                    }
                }
                HorizontalDivider()
                TextButton(onClick = onDeleteSticker) {
                    Text("Delete Sticker", color = StickyKeysTheme.colors.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
