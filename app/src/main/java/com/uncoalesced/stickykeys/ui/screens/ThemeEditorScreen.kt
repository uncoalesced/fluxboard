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
import androidx.compose.ui.res.painterResource
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
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyGlyph
import com.uncoalesced.stickykeys.keyboardcore.layout.keyGlyph
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyStyle
import com.uncoalesced.stickykeys.keyboardcore.theme.KeyboardTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import com.uncoalesced.stickykeys.keyboardcore.theme.TypeScale
import com.uncoalesced.stickykeys.ui.components.KeyStyleControls
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

        /**
         * Applies an edit to the active theme, forking a preset into a custom copy first.
         *
         * The fork id is derived from the preset rather than random. A random UUID per call
         * looked fine for a one-shot edit but is wrong for anything continuous: dragging a
         * slider fires this on every frame, and `activeTheme` only becomes the new custom
         * theme once the manager has reloaded and the preference has propagated -- so every
         * intervening frame still saw a preset and forked *again*. One drag could leave a
         * dozen near-identical "Custom Ink" themes in the list. A derived id makes repeated
         * edits converge on the same theme, which is what the user meant by dragging.
         */
        private fun mutateActiveTheme(transform: (KeyboardTheme) -> KeyboardTheme) {
            val theme = activeTheme.value ?: return
            viewModelScope.launch {
                val isPreset = theme.id.startsWith("preset_")
                val base =
                    if (isPreset) {
                        theme.copy(
                            id = "custom_${theme.id}",
                            name = "Custom ${theme.name}",
                        )
                    } else {
                        theme
                    }
                themeManager.saveCustomTheme(transform(base))
            }
        }

        fun updateActiveThemeOverlayOpacity(opacity: Float) =
            mutateActiveTheme { it.copy(imageOverlayOpacity = opacity) }

        /** Per-key styling edits all route through here, so the fork rule lives in one place. */
        fun updateKeyStyle(transform: (KeyStyle) -> KeyStyle) =
            mutateActiveTheme { it.copy(keyStyle = transform(it.keyStyle)) }

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
    onOpenPreview: () -> Unit = {},
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
        // One scrolling list for the whole editor, rather than a scrollable theme picker with
        // fixed panels stacked under it.
        //
        // The panels used to sit *below* a `weight(1f)` LazyColumn inside a Column that did
        // not scroll, so they were given whatever height was left and simply clipped when they
        // needed more. Expanding the key-styling section is exactly that case: it adds swatch
        // rows and sliders until the haze controls, the live-preview button and the keyboard
        // preview are all off the bottom of the screen with no way to reach them. Nothing
        // about the controls was broken -- they could not be scrolled to.
        LazyColumn(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            run {
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
            item {
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

                        Spacer(modifier = Modifier.height(12.dp))
                        KeyStyleControls(
                            style = activeTheme?.keyStyle ?: KeyStyle.Default,
                            onChange = { transform -> viewModel.updateKeyStyle(transform) },
                            hasBackgroundImage = activeTheme?.backgroundImagePath != null,
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        // A static swatch cannot tell you whether a keyboard is comfortable to
                        // type on -- the haze, the key size and the haptics only read under a
                        // thumb. This opens the real keyboard with somewhere to type.
                        Button(
                            onClick = onOpenPreview,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open live preview")
                        }
                    }
                }
            }

            item {
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
            }

            // Live Preview
            item {
                activeTheme?.let { theme ->
                    KeyboardPreview(theme = theme)
                }
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

    StickyKeysTheme(
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
                                StickyKeysTheme.colors.background
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
                                StickyKeysTheme.colors.surface,
                            ).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!compact) {
                        listOf("hello", "world", "theme").forEach {
                            Text(
                                text = it,
                                color = StickyKeysTheme.colors.onSurface,
                                style = StickyKeysTheme.typography.labelLarge,
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

                val keyTypography =
                    StickyKeysTheme.typography

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
                            val keyBgBase =
                                if (isSpecialKey) {
                                    StickyKeysTheme.colors.surfaceVariant
                                } else {
                                    StickyKeysTheme.colors.surface
                                }
                            val keyBg =
                                if (bgBitmap != null) {
                                    keyBgBase.copy(alpha = 0.75f)
                                } else {
                                    keyBgBase
                                }
                            val keyFg =
                                if (isSpecialKey) {
                                    StickyKeysTheme.colors.onSurfaceVariant
                                } else {
                                    StickyKeysTheme.colors.onSurface
                                }

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
                                    // Was `keyLabel.take(1)`, which drew SHIFT, SPACE and
                                    // SYMBOLS as three identical "S" keys and DEL as "D".
                                    // Shares the live keyboard's glyph table so the preview
                                    // is actually a preview.
                                    when (val glyph = keyGlyph(keyLabel)) {
                                        is KeyGlyph.Icon ->
                                            Icon(
                                                painter = painterResource(glyph.res),
                                                contentDescription = glyph.description,
                                                tint = keyFg,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        is KeyGlyph.Label ->
                                            Text(
                                                text = glyph.text,
                                                color = keyFg,
                                                maxLines = 1,
                                                style = keyTypography.keyboardKey,
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
}
