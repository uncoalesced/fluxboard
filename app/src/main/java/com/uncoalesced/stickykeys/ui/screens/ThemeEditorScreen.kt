// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.ime.rememberBackgroundBitmap
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyboardTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ThemeEditorViewModel
    @Inject
    constructor(
        private val themeManager: ThemeManager,
        private val keyboardPreferences: KeyboardPreferences,
    ) : ViewModel() {
        val availableThemes =
            themeManager.availableThemes.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )
        val activeTheme =
            themeManager.activeTheme.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null,
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

        fun updateActiveThemeBackgroundImage(
            uri: Uri,
            context: Context,
        ) {
            val theme = activeTheme.value ?: return
            viewModelScope.launch {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val targetThemeId =
                        if (theme.id.startsWith("preset_")) {
                            "custom_" + UUID.randomUUID().toString()
                        } else {
                            theme.id
                        }
                    val imagePath =
                        themeManager.saveThemeBackgroundImage(
                            inputStream,
                            targetThemeId,
                        )
                    val updatedTheme =
                        theme.copy(
                            id = targetThemeId,
                            name =
                                if (theme.id.startsWith(
                                        "preset_",
                                    )
                                ) {
                                    "Custom ${theme.name}"
                                } else {
                                    theme.name
                                },
                            backgroundImagePath = imagePath,
                        )
                    themeManager.saveCustomTheme(updatedTheme)
                }
            }
        }

        fun updateActiveThemeOverlayOpacity(opacity: Float) {
            val theme = activeTheme.value ?: return
            viewModelScope.launch {
                val targetThemeId =
                    if (theme.id.startsWith("preset_")) {
                        "custom_" + UUID.randomUUID().toString()
                    } else {
                        theme.id
                    }
                val updatedTheme =
                    theme.copy(
                        id = targetThemeId,
                        name =
                            if (theme.id.startsWith(
                                    "preset_",
                                )
                            ) {
                                "Custom ${theme.name}"
                            } else {
                                theme.name
                            },
                        imageOverlayOpacity = opacity,
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
    viewModel: ThemeEditorViewModel = hiltViewModel(),
) {
    val themes by viewModel.availableThemes.collectAsState()
    val activeTheme by viewModel.activeTheme.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.updateActiveThemeBackgroundImage(uri, context)
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.text_keyboard_themes)) },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(themes) { theme ->
                    val isSelected = theme.id == activeTheme?.id
                    // The picker used to be a bare list of theme names, so choosing one meant
                    // applying it and looking at the keyboard to find out what it was. The
                    // preview composable already existed; it just was not on this screen.
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setActiveTheme(theme.id) }
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                ).padding(horizontal = 16.dp, vertical = 12.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Theme ${theme.name}"
                                    role = Role.RadioButton
                                    stateDescription =
                                        if (isSelected) "Selected" else "Not selected"
                                },
                    ) {
                        Text(
                            text = theme.name,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        KeyboardPreview(theme = theme, compact = true)
                    }
                }
            }

            // Image Customization Controls
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Background Image Customization",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Text(stringResource(R.string.text_set_background_image))
                        }
                        if (activeTheme?.backgroundImagePath != null) {
                            OutlinedButton(
                                onClick = { viewModel.removeActiveThemeBackgroundImage() },
                            ) {
                                Text(stringResource(R.string.text_remove_image))
                            }
                        }
                    }
                    if (activeTheme?.backgroundImagePath != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Overlay Legibility Opacity: ${"%.2f".format(
                                activeTheme?.imageOverlayOpacity ?: 0.4f,
                            )}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = activeTheme?.imageOverlayOpacity ?: 0.4f,
                            onValueChange = { viewModel.updateActiveThemeOverlayOpacity(it) },
                            valueRange = 0.0f..0.9f,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val baseColors =
                        activeTheme?.colors
                            ?: com.uncoalesced.stickykeys.keyboardcore.theme
                                .lightStickyKeysColors()
                    val newTheme =
                        KeyboardTheme(
                            id = "custom_" + UUID.randomUUID().toString(),
                            name = "Custom Theme " + (themes.size),
                            isLight = activeTheme?.isLight ?: true,
                            colors =
                                baseColors.copy(
                                    primary = Color(0xFFFF5722),
                                    primaryVariant = Color(0xFFE64A19),
                                ),
                            typeScale = TypeScale.MEDIUM,
                        )
                    viewModel.saveTheme(newTheme)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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

/**
 * A miniature of the keyboard drawn in [theme]'s colours.
 *
 * [compact] trims it to a thumbnail for the preset picker, where one of these is drawn per
 * theme; the full version is used for the live preview of the active theme.
 */
@Composable
fun KeyboardPreview(
    theme: KeyboardTheme,
    compact: Boolean = false,
) {
    val keyHeight = if (compact) 14.dp else 40.dp
    val stripHeight = if (compact) 16.dp else 40.dp

    // Downsampled and decoded off the main thread. A full-resolution decode per row would
    // be far worse here than it was on the keyboard: the picker draws one preview per theme.
    val density = LocalDensity.current
    val previewWidthPx =
        with(density) {
            LocalConfiguration.current.screenWidthDp.dp
                .roundToPx()
        }
    val previewHeightPx = with(density) { (keyHeight * 4 + stripHeight).roundToPx() }
    val bgBitmap =
        rememberBackgroundBitmap(theme.backgroundImagePath, previewWidthPx, previewHeightPx)

    com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme(
        darkTheme = !theme.isLight,
        typeScale = theme.typeScale,
        customColors = theme.colors,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = theme.imageOverlayOpacity)),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (bgBitmap ==
                                null
                            ) {
                                com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.background
                            } else {
                                Color.Transparent
                            },
                        ).padding(4.dp),
            ) {
                // Fake Suggestion Strip
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(stripHeight)
                            .background(
                                com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surface,
                            ).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!compact) {
                        listOf("hello", "world", "theme").forEach {
                            Text(
                                text = it,
                                color = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurface,
                                style = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                // Fake Keys
                val rows =
                    listOf(
                        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                        listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"),
                        listOf("SYMBOLS", "SPACE", "ENTER"),
                    )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        for (keyLabel in row) {
                            val weight =
                                when (keyLabel) {
                                    "SPACE" -> 4f
                                    "ENTER", "SHIFT", "DEL", "SYMBOLS" -> 1.5f
                                    else -> 1f
                                }

                            val isSpecialKey = weight > 1f
                            val keyBgBase = if (isSpecialKey) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surfaceVariant else com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.surface
                            val keyBg =
                                if (bgBitmap !=
                                    null
                                ) {
                                    keyBgBase.copy(alpha = 0.75f)
                                } else {
                                    keyBgBase
                                }
                            val keyFg = if (isSpecialKey) com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurfaceVariant else com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.colors.onSurface

                            Box(
                                modifier =
                                    Modifier
                                        .weight(weight)
                                        .padding(if (compact) 1.dp else 2.dp)
                                        .height(keyHeight)
                                        .background(keyBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!compact) {
                                    Text(
                                        text =
                                            if (keyLabel.length > 1) keyLabel.take(1) else keyLabel,
                                        color = keyFg,
                                        style = com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme.typography.keyboardKey,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
