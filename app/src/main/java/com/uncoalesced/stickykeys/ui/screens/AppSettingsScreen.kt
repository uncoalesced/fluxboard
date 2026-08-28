// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.uncoalesced.stickykeys.BuildConfig
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.data.local.AppPreferences
import com.uncoalesced.stickykeys.data.local.ThemeMode
import com.uncoalesced.stickykeys.ui.components.LoadingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed interface AppSettingsUiState {
    data object Loading : AppSettingsUiState

    data object Success : AppSettingsUiState
}

@HiltViewModel
class AppSettingsViewModel
    @Inject
    constructor(
        private val appPreferences: AppPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AppSettingsUiState>(AppSettingsUiState.Success)
        val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

        val defaultExportFormat = appPreferences.defaultExportFormat

        fun setDefaultExportFormat(format: String) {
            appPreferences.setDefaultExportFormat(format)
        }

        val themeMode = appPreferences.themeMode

        fun setThemeMode(mode: ThemeMode) {
            appPreferences.setThemeMode(mode)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel = hiltViewModel(),
    onNavigateToManageCategories: () -> Unit,
    onNavigateToTransfer: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    when (state) {
        is AppSettingsUiState.Loading -> LoadingScreen()
        is AppSettingsUiState.Success -> {
            val currentExportFormat by viewModel.defaultExportFormat.collectAsState()
            var formatDropdownExpanded by remember { mutableStateOf(false) }

            val exportFormats =
                listOf(
                    "image/webp" to "Animated WebP",
                    "image/gif" to "Standard GIF",
                )
            val currentFormatLabel =
                // Falls back to the shipped default's label, not the other option's: this
                // only fires for a stored value neither entry matches, and naming the
                // format that is not in use would misreport what the next export produces.
                exportFormats.find { it.first == currentExportFormat }?.second ?: "Standard GIF"

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // Scrollable: the About section pushed the content past a phone screen,
                        // and a non-scrolling Column clips rather than scrolls -- the same
                        // failure the theme editor had.
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                // Brand header. Reuses the adaptive-icon foreground rather than shipping a
                // second copy of the same artwork: it is already packaged at five densities,
                // it is the transparent mark (so no black box on the light palette), and the
                // 108dp canvas padding just reads as margin at this size.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            if (IS_PRERELEASE) {
                                Spacer(modifier = Modifier.width(8.dp))
                                PrereleaseBadge()
                            }
                        }
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (IS_PRERELEASE) {
                    // Said once, plainly, where somebody deciding whether to trust this build
                    // will see it. A badge alone says "beta" without saying what follows from
                    // it, and what follows is the part a tester needs: things will break, and
                    // reporting them is the point of their having it.
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.text_beta_notice_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.text_beta_notice_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                // Category Management Entry
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToManageCategories() }
                            .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_manage_categories),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(
                                R.string.text_add_rename_or_delete_your_sticker_categories,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App theme. The light palette existed in the design tokens from the start
                // but nothing ever selected it -- the app was hardcoded dark.
                Text(
                    stringResource(R.string.text_app_theme),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val currentThemeMode by viewModel.themeMode.collectAsState()
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options =
                        listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.text_theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.text_theme_light),
                            ThemeMode.DARK to stringResource(R.string.text_theme_dark),
                        )
                    options.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = currentThemeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = options.size,
                                ),
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Default Export Format
                Text(
                    stringResource(R.string.text_default_export_format),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = formatDropdownExpanded,
                    onExpandedChange = { formatDropdownExpanded = !formatDropdownExpanded },
                ) {
                    OutlinedTextField(
                        value = currentFormatLabel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = formatDropdownExpanded,
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = formatDropdownExpanded,
                        onDismissRequest = { formatDropdownExpanded = false },
                    ) {
                        exportFormats.forEach { (format, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setDefaultExportFormat(format)
                                    formatDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This format is used when converting videos to stickers.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Transfer, which used to be a dock tab of its own.
                //
                // It is a thing you do roughly once, when moving to a new phone, and it held
                // a quarter of the dock permanently for that. Here it costs one row and the
                // dock drops to three tabs. The screen itself is unchanged and still a
                // pushed route, which is also what makes its app bar and back arrow correct
                // rather than the odd one out among the tabs.
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToTransfer() }
                            .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.text_transfer_to_a_new_device),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.text_transfer_row_summary),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // About
                //
                // Two rows, two destinations: the repository, and the author's profile. The
                // uncoalesced site is not live, and shipping a link that 404s in an alpha
                // testers are actively poking at is worse than pointing somewhere real, so
                // SITE_URL holds the profile until there is a site. One line to change then.
                Text(
                    stringResource(R.string.text_about),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinkRow(
                    label = stringResource(R.string.text_source_code),
                    url = REPO_URL,
                )
                LinkRow(
                    label = stringResource(R.string.text_uncoalesced),
                    url = SITE_URL,
                )
                Text(
                    text = "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * One tappable link row.
 *
 * Launched with a plain try/catch rather than a `resolveActivity` check first: package
 * visibility filtering on Android 11+ makes that return null even when the launch would
 * succeed, so checking is less reliable than trying.
 */
@Composable
private fun LinkRow(
    label: String,
    url: String,
) {
    val context = LocalContext.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The repository. Real, public, and where the source actually lives. */
private const val REPO_URL = "https://github.com/uncoalesced/fluxboard"

/**
 * uncoalesced.
 *
 * The author's profile rather than this repository -- the row next to it already points at the
 * repository, and two rows leading to the same page is a link that looks like information and
 * is not. Points here until the site is live; one line to change when it is.
 */
private const val SITE_URL = "https://github.com/uncoalesced"

/**
 * Whether this build is a pre-release, derived from the version rather than declared.
 *
 * A separate flag would be a second thing to remember at release time, and the one that gets
 * forgotten is always the one that makes a stable build announce itself as a beta -- or worse,
 * a beta stay silent. The version string is already the thing that has to be right.
 */
private val IS_PRERELEASE: Boolean =
    BuildConfig.VERSION_NAME.contains("BETA", ignoreCase = true) ||
        BuildConfig.VERSION_NAME.contains("ALPHA", ignoreCase = true) ||
        BuildConfig.VERSION_NAME.contains("RC", ignoreCase = true)

/** The word itself, next to the app name, in the one colour reserved for saying "not final". */
@Composable
private fun PrereleaseBadge() {
    val label =
        when {
            BuildConfig.VERSION_NAME.contains("BETA", ignoreCase = true) -> "BETA"
            BuildConfig.VERSION_NAME.contains("ALPHA", ignoreCase = true) -> "ALPHA"
            else -> "PRE-RELEASE"
        }
    Surface(
        color = MaterialTheme.colorScheme.tertiary,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
