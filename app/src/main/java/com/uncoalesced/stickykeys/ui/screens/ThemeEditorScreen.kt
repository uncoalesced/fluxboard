// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyboardTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysColors
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID
import android.net.Uri
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File

@HiltViewModel
class ThemeEditorViewModel @Inject constructor(
    private val themeManager: ThemeManager,
    private val keyboardPreferences: KeyboardPreferences
) : ViewModel() {
    val availableThemes = themeManager.availableThemes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val activeTheme = themeManager.activeTheme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    fun setActiveTheme(themeId: String) {
        viewModelScope.launch {
            keyboardPreferences.setActiveThemeId(themeId)
        }
    }
    
    fun saveTheme(theme: KeyboardTheme) {
        viewModelScope.launch {
            themeManager.saveCustomTheme(theme)
        }
    }

    fun updateActiveThemeBackgroundImage(uri: Uri, context: Context) {
        val theme = activeTheme.value ?: return
        viewModelScope.launch {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val targetThemeId = if (theme.id.startsWith("preset_")) {
                    "custom_" + UUID.randomUUID().toString()
                } else {
                    theme.id
                }
                val imagePath = themeManager.saveThemeBackgroundImage(inputStream, targetThemeId)
                val updatedTheme = theme.copy(
                    id = targetThemeId,
                    name = if (theme.id.startsWith("preset_")) "Custom ${theme.name}" else theme.name,
                    backgroundImagePath = imagePath
                )
                themeManager.saveCustomTheme(updatedTheme)
            }
        }
    }

    fun updateActiveThemeOverlayOpacity(opacity: Float) {
        val theme = activeTheme.value ?: return
        viewModelScope.launch {
            val targetThemeId = if (theme.id.startsWith("preset_")) {
                "custom_" + UUID.randomUUID().toString()
            } else {
                theme.id
            }
            val updatedTheme = theme.copy(
                id = targetThemeId,
                name = if (theme.id.startsWith("preset_")) "Custom ${theme.name}" else theme.name,
                imageOverlayOpacity = opacity
            )
            themeManager.saveCustomTheme(updatedTheme)
        }
    }

    fun removeActiveThemeBackgroundImage() {
        val theme = activeTheme.value ?: return
        viewModelScope.launch {
            val updatedTheme = theme.copy(backgroundImagePath = null)
            themeManager.saveCustomTheme(updatedTheme)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThemeEditorViewModel = hiltViewModel()
) {
    val themes by viewModel.availableThemes.collectAsState()
    val activeTheme by viewModel.activeTheme.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateActiveThemeBackgroundImage(uri, context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.text_keyboard_themes)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(themes) { theme ->
                    val isSelected = theme.id == activeTheme?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setActiveTheme(theme.id)
                            }
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = theme.name,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Image Customization Controls
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Background Image Customization",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Text(stringResource(R.string.text_set_background_image))
                        }
                        if (activeTheme?.backgroundImagePath != null) {
                            OutlinedButton(onClick = { viewModel.removeActiveThemeBackgroundImage() }) {
                                Text(stringResource(R.string.text_remove_image))
                            }
                        }
                    }
                    if (activeTheme?.backgroundImagePath != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Overlay Legibility Opacity: ${"%.2f".format(activeTheme?.imageOverlayOpacity ?: 0.4f)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = activeTheme?.imageOverlayOpacity ?: 0.4f,
                            onValueChange = { viewModel.updateActiveThemeOverlayOpacity(it) },
                            valueRange = 0.0f..0.9f
                        )
                    }
                }
            }
            
            Button(
                onClick = {
                    val baseColors = activeTheme?.colors ?: com.uncoalesced.stickykeys.keyboardcore.theme.lightStickyKeysColors()
                    val newTheme = KeyboardTheme(
                        id = "custom_" + UUID.randomUUID().toString(),
                        name = "Custom Theme " + (themes.size),
                        isLight = activeTheme?.isLight ?: true,
                        colors = baseColors.copy(
                            primary = Color(0xFFFF5722),
                            primaryVariant = Color(0xFFE64A19)
                        ),
                        typeScale = TypeScale.MEDIUM
                    )
                    viewModel.saveTheme(newTheme)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.text_create_orange_accent_theme))
            }

            // Live Preview
            activeTheme?.let { theme ->
                KeyboardPreview(theme = theme)
            }
        }
    }
}

@Composable
fun KeyboardPreview(theme: KeyboardTheme) {
    val bgPath = theme.backgroundImagePath
    val bgBitmap = remember(bgPath) {
        if (bgPath != null) {
            val file = File(bgPath)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else null
        } else null
    }

    com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme(
        darkTheme = !theme.isLight,
        typeScale = theme.typeScale,
        customColors = theme.colors
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = theme.imageOverlayOpacity))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (bgBitmap == null) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.background else Color.Transparent)
                    .padding(4.dp)
            ) {
                // Fake Suggestion Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surface)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("hello", "world", "theme").forEach {
                        Text(
                            text = it,
                            color = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurface,
                            style = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.typography.labelLarge
                        )
                    }
                }
                
                // Fake Keys
                val rows = listOf(
                    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                    listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"),
                    listOf("SYMBOLS", "SPACE", "ENTER")
                )
                
                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (keyLabel in row) {
                            val weight = when (keyLabel) {
                                "SPACE" -> 4f
                                "ENTER", "SHIFT", "DEL", "SYMBOLS" -> 1.5f
                                else -> 1f
                            }

                            val isSpecialKey = weight > 1f
                            val keyBgBase = if (isSpecialKey) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surfaceVariant else com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surface
                            val keyBg = if (bgBitmap != null) keyBgBase.copy(alpha = 0.75f) else keyBgBase
                            val keyFg = if (isSpecialKey) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurfaceVariant else com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurface

                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .padding(2.dp)
                                    .height(40.dp)
                                    .background(keyBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (keyLabel.length > 1) keyLabel.take(1) else keyLabel,
                                    color = keyFg,
                                    style = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.typography.keyboardKey
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
