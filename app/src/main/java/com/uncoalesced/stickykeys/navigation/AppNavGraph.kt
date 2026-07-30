// Engineered by uncoalesced
package com.uncoalesced.stickykeys.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
fun AppNavGraph(initialImageUri: String? = null) {
    val navController = rememberNavController()

    LaunchedEffect(initialImageUri) {
        if (!initialImageUri.isNullOrEmpty()) {
            val encodedUri = java.net.URLEncoder.encode(initialImageUri, "UTF-8")
            navController.navigate("crop/$encodedUri")
        }
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
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { (route, title, icon) ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = icon,
                                // The label below already carries the name, so repeating it
                                // here would make a screen reader say it twice.
                                contentDescription = null,
                            )
                        },
                        label = { Text(title) },
                        selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
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
