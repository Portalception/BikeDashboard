package com.astheral.bikedashboard.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.astheral.bikedashboard.ui.dashboard.DashboardScreen
import com.astheral.bikedashboard.ui.edit.EditScreen
import com.astheral.bikedashboard.ui.history.HistoryScreen
import com.astheral.bikedashboard.ui.importer.ImportScreen
import com.astheral.bikedashboard.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object DashboardRoute
@Serializable object HistoryRoute
@Serializable object SettingsRoute
@Serializable object ImportRoute

/** Add a fill-up when [id] is 0, otherwise edit the fill-up with that id. */
@Serializable data class EditRoute(val id: Long = 0)

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(DashboardRoute, "Dashboard", Icons.Filled.Home),
    Tab(HistoryRoute, "History", Icons.Filled.DateRange),
    Tab(SettingsRoute, "Settings", Icons.Filled.Settings),
)

/** Top-level screens have the bottom bar under them, so they don't pad for the system bars again. */
val NoInsets = WindowInsets(0, 0, 0, 0)

@Composable
fun BikeDashboardApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showTabs = tabs.any { tab -> destination?.hasRoute(tab.route::class) == true }

    Scaffold(
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(tab.route::class) == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = NoInsets,
    ) { padding ->
        NavHost(nav, startDestination = DashboardRoute, modifier = Modifier.padding(padding)) {
            composable<DashboardRoute> {
                DashboardScreen(
                    onAddFillUp = { nav.navigate(EditRoute()) },
                    onImport = { nav.navigate(ImportRoute) },
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(
                    onAddFillUp = { nav.navigate(EditRoute()) },
                    onOpenFillUp = { id -> nav.navigate(EditRoute(id)) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onImport = { nav.navigate(ImportRoute) })
            }
            composable<ImportRoute> {
                ImportScreen(onBack = { nav.popBackStack() })
            }
            composable<EditRoute> { entry ->
                EditScreen(fillUpId = entry.toRoute<EditRoute>().id, onDone = { nav.popBackStack() })
            }
        }
    }
}
