// Engineered by uncoalesced
package com.uncoalesced.stickykeys.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uncoalesced.stickykeys.keyboardcore.theme.DOCK_COLLAPSED_WEIGHT
import com.uncoalesced.stickykeys.keyboardcore.theme.DOCK_EXPANDED_WEIGHT
import com.uncoalesced.stickykeys.keyboardcore.theme.ExpandSpring
import com.uncoalesced.stickykeys.keyboardcore.theme.PRESS_COLOR_ANIM_MS
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.ui.screens.AppSettingsScreen
import com.uncoalesced.stickykeys.ui.screens.DevicePairingScreen
import com.uncoalesced.stickykeys.ui.screens.KeyboardSettingsScreen
import com.uncoalesced.stickykeys.ui.screens.StickersLibraryScreen
import com.uncoalesced.stickykeys.keyboardcore.R as KeyboardCoreR

/** One bottom-bar destination. */
private data class NavEntry(
    val route: String,
    val title: String,
    val icon: ImageVector,
)

@Composable
fun AppNavGraph(
    initialImageUri: String? = null,
    initialRoute: String? = null,
    onInitialRouteHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    LaunchedEffect(initialImageUri) {
        if (!initialImageUri.isNullOrEmpty()) {
            val encodedUri = java.net.URLEncoder.encode(initialImageUri, "UTF-8")
            navController.navigate("crop/$encodedUri")
        }
    }

    // The keyboard's settings shortcut lands here. Navigated like a dock tap rather than pushed
    // on top of the stack, so pressing back from it leaves the app instead of returning to the
    // Styles screen the user never asked for -- and so a second use of the shortcut while the
    // tab is already open is a no-op rather than a second copy of it.
    LaunchedEffect(initialRoute) {
        val route = initialRoute ?: return@LaunchedEffect
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        onInitialRouteHandled()
    }

    // The icons used to be Text(title.first()), so "Styles" and "Settings" both rendered a
    // bare "S" and the bar carried no usable signal at all.
    val screens =
        listOf(
            NavEntry("stickers", "Styles", Icons.Outlined.Star),
            NavEntry(
                "keyboard",
                "Keyboard",
                // Non-transitive R: this drawable belongs to keyboard-core, so it is not on
                // the app module's own R class.
                ImageVector.vectorResource(KeyboardCoreR.drawable.ic_keyboard_flux),
            ),
            NavEntry("transfer", "Transfer", Icons.Outlined.Share),
            NavEntry("settings", "Settings", Icons.Outlined.Settings),
        )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            // Only the rendering changed. The navigate/popUpTo/restoreState block below is the
            // same one NavigationBarItem was given, because the navigation was never the
            // problem -- four Material items in a fixed bar were.
            PillDock(
                entries = screens,
                isSelected = { route ->
                    currentDestination?.hierarchy?.any { it.route == route } == true
                },
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "stickers",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("stickers") {
                StickersLibraryScreen(
                    onImagePicked = { uri ->
                        val encodedUri = java.net.URLEncoder.encode(uri, "UTF-8")
                        navController.navigate("crop/$encodedUri")
                    },
                    onVideoPicked = { uri ->
                        val encodedUri = java.net.URLEncoder.encode(uri, "UTF-8")
                        navController.navigate("trim_video/$encodedUri")
                    },
                    onStickerClick = { stickerId ->
                        navController.navigate("edit/$stickerId")
                    },
                )
            }
            composable("keyboard") {
                KeyboardSettingsScreen(
                    onNavigateToThemeEditor = { navController.navigate("theme_editor") },
                    onNavigateToLayoutEditor = { navController.navigate("layout_editor") },
                )
            }

            composable("theme_editor") {
                com.uncoalesced.stickykeys.ui.screens.ThemeEditorScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPreview = { navController.navigate("keyboard_preview") },
                )
            }

            composable("keyboard_preview") {
                com.uncoalesced.stickykeys.ui.screens.KeyboardPreviewScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable("layout_editor") {
                com.uncoalesced.stickykeys.ui.screens.LayoutEditorScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable("transfer") {
                DevicePairingScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings") {
                AppSettingsScreen(
                    onNavigateToManageCategories = { navController.navigate("manage_categories") },
                )
            }

            composable("manage_categories") {
                com.uncoalesced.stickykeys.ui.screens.ManageCategoriesScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // Video Trim & Convert Flow
            composable("trim_video/{uri}") { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri") ?: ""
                com.uncoalesced.stickykeys.ui.screens.video.VideoTrimScreen(
                    videoUriString = java.net.URLDecoder.decode(uri, "UTF-8"),
                    onTrimComplete = { videoUri, startMs, endMs ->
                        val encodedUri = java.net.URLEncoder.encode(videoUri, "UTF-8")
                        navController.navigate("convert_video/$encodedUri/$startMs/$endMs")
                    },
                    onCancel = { navController.popBackStack("stickers", false) },
                )
            }

            composable("convert_video/{uri}/{startMs}/{endMs}") { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri") ?: ""
                val startMs = backStackEntry.arguments?.getString("startMs")?.toLongOrNull() ?: 0L
                val endMs = backStackEntry.arguments?.getString("endMs")?.toLongOrNull() ?: 5000L

                com.uncoalesced.stickykeys.ui.screens.video.VideoConvertScreen(
                    videoUriString = java.net.URLDecoder.decode(uri, "UTF-8"),
                    startMs = startMs,
                    endMs = endMs,
                    onConversionComplete = { navController.popBackStack("stickers", false) },
                    onCancel = { navController.popBackStack("stickers", false) },
                )
            }

            // Edit Flow
            composable("edit/{stickerId}") { backStackEntry ->
                val stickerId = backStackEntry.arguments?.getString("stickerId") ?: ""
                com.uncoalesced.stickykeys.ui.screens.edit.EditStickerScreen(
                    stickerId = stickerId,
                    onComplete = { navController.popBackStack("stickers", false) },
                    onCancel = { navController.popBackStack("stickers", false) },
                )
            }

            // Creation flow
            composable("crop/{uri}") { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri") ?: ""
                com.uncoalesced.stickykeys.ui.screens.creation.CropScreen(
                    uriString = java.net.URLDecoder.decode(uri, "UTF-8"),
                    // No auto-segmentation in v1: crop hands straight to the eraser.
                    onCropComplete = { croppedUri ->
                        val encodedUri = java.net.URLEncoder.encode(croppedUri, "UTF-8")
                        navController.navigate("erase/$encodedUri")
                    },
                    onCancel = { navController.popBackStack("stickers", false) },
                )
            }

            composable("touchup/{origUri}/{segUri}") { backStackEntry ->
                val origUri = backStackEntry.arguments?.getString("origUri") ?: ""
                val segUri = backStackEntry.arguments?.getString("segUri") ?: ""
                com.uncoalesced.stickykeys.ui.screens.creation.TouchUpScreen(
                    originalUriString = java.net.URLDecoder.decode(origUri, "UTF-8"),
                    segmentedUriString = java.net.URLDecoder.decode(segUri, "UTF-8"),
                    onTouchUpComplete = { finalUri ->
                        val encodedUri = java.net.URLEncoder.encode(finalUri, "UTF-8")
                        navController.navigate("save/$encodedUri")
                    },
                    onCancel = { navController.popBackStack("stickers", false) },
                )
            }

            composable("erase/{uri}") { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri") ?: ""
                com.uncoalesced.stickykeys.ui.screens.creation.EraseScreen(
                    uriString = java.net.URLDecoder.decode(uri, "UTF-8"),
                    onEraseComplete = { erasedUri ->
                        val encodedUri = java.net.URLEncoder.encode(erasedUri, "UTF-8")
                        navController.navigate("save/$encodedUri")
                    },
                    onCancel = { navController.popBackStack("stickers", false) },
                )
            }

            composable("save/{uri}") { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri") ?: ""
                com.uncoalesced.stickykeys.ui.screens.creation.SaveStickerScreen(
                    uriString = java.net.URLDecoder.decode(uri, "UTF-8"),
                    onSaveComplete = { navController.popBackStack("stickers", false) },
                    onCancel = { navController.popBackStack("stickers", false) },
                )
            }
        }
    }
}

/**
 * The bottom dock: icon-only pills at rest, the active one expanding to show its name.
 *
 * Four destinations, not the five the reference image happened to draw. The reference is
 * generic placeholder content and its icon count is not a requirement; inventing a fifth
 * screen to match it would be building the app around a mockup.
 *
 * Colours come from the roles Color.kt already assigns rather than new ones: Fern is documented
 * there as the accent for active states and selection. The label is onPrimary, which is
 * near-black -- Cream on Fern measures 2.15 and cannot be read, and that pairing is the exact
 * mistake the palette's own header warns about.
 */
@Composable
private fun PillDock(
    entries: List<NavEntry>,
    isSelected: (String) -> Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(StickyKeysTheme.colors.background)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            val selected = isSelected(entry.route)
            val weight by animateFloatAsState(
                targetValue = if (selected) DOCK_EXPANDED_WEIGHT else DOCK_COLLAPSED_WEIGHT,
                animationSpec = ExpandSpring,
                label = "dock-weight",
            )
            val fill by animateColorAsState(
                targetValue =
                    if (selected) StickyKeysTheme.colors.primary else Color.Transparent,
                animationSpec = tween(PRESS_COLOR_ANIM_MS),
                label = "dock-fill",
            )
            Row(
                modifier =
                    Modifier
                        .weight(weight)
                        .height(DOCK_ITEM_HEIGHT)
                        .background(fill, StickyKeysTheme.shapes.pill)
                        .clickable(
                            role = Role.Tab,
                            onClick = { onSelect(entry.route) },
                        ).padding(horizontal = 12.dp)
                        .semantics {
                            contentDescription = entry.title
                            stateDescription = if (selected) "Selected" else "Not selected"
                        },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = entry.icon,
                    // The label carries the name when it is shown, and the row's own semantics
                    // carry it when it is not, so announcing it here would double it up.
                    contentDescription = null,
                    tint =
                        if (selected) {
                            StickyKeysTheme.colors.onPrimary
                        } else {
                            StickyKeysTheme.colors.onBackground.copy(alpha = DOCK_INACTIVE_ALPHA)
                        },
                    modifier = Modifier.size(22.dp),
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Text(
                        text = entry.title,
                        color = StickyKeysTheme.colors.onPrimary,
                        maxLines = 1,
                        style = StickyKeysTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Tall enough to clear the 48dp touch guideline once the row's own padding is counted. */
private val DOCK_ITEM_HEIGHT = 44.dp

/** Matches the corner-hint dimming already used on the keyboard rather than a new value. */
private const val DOCK_INACTIVE_ALPHA = 0.6f
