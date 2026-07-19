package com.uncoalesced.stickykeys.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uncoalesced.stickykeys.ui.screens.AppSettingsScreen
import com.uncoalesced.stickykeys.ui.screens.KeyboardSettingsScreen
import com.uncoalesced.stickykeys.ui.screens.StickersLibraryScreen
import com.uncoalesced.stickykeys.ui.screens.TransferShareScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    val screens = listOf(
        "stickers" to "Stickers",
        "keyboard" to "Keyboard",
        "transfer" to "Transfer",
        "settings" to "Settings"
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { (route, title) ->
                    NavigationBarItem(
                        icon = { Text(title.first().toString()) }, // Stub icon
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
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "stickers",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("stickers") { StickersLibraryScreen() }
            composable("keyboard") { KeyboardSettingsScreen() }
            composable("transfer") { TransferShareScreen() }
            composable("settings") { AppSettingsScreen() }
        }
    }
}
