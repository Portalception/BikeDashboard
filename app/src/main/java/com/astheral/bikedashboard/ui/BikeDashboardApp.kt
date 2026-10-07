package com.astheral.bikedashboard.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.ui.components.AppBackground
import com.astheral.bikedashboard.ui.components.PillShape
import com.astheral.bikedashboard.ui.components.outline
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
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
    Tab(DashboardRoute, "Dashboard", Icons.Filled.Speed),
    Tab(HistoryRoute, "History", Icons.Filled.History),
    Tab(SettingsRoute, "Settings", Icons.Filled.Settings),
)

/** Top-level screens have the bottom bar under them, so they don't pad for the system bars again. */
val NoInsets = WindowInsets(0, 0, 0, 0)

/** History reads this from its back-stack entry to slide a deleted fill-up out, then delete it. */
const val DELETED_FILL_UP_ID = "deletedFillUpId"

private const val SLIDE_MS = 300

private fun NavDestination?.tabIndex() = tabs.indexOfFirst { this?.hasRoute(it.route::class) == true }
private fun NavDestination?.isOverlay() = this?.hasRoute(EditRoute::class) == true || this?.hasRoute(ImportRoute::class) == true

/**
 * Switching tabs slides the screens sideways, towards the tab you picked.
 * The add/edit form and the import screen slide up over whatever was showing.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(): EnterTransition {
    val from = initialState.destination.tabIndex()
    val to = targetState.destination.tabIndex()
    return when {
        initialState.destination.isOverlay() -> EnterTransition.None
        from >= 0 && to >= 0 -> slideIntoContainer(
            if (to > from) SlideDirection.Left else SlideDirection.Right, tween(SLIDE_MS),
        )
        else -> fadeIn(tween(SLIDE_MS))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(): ExitTransition {
    val from = initialState.destination.tabIndex()
    val to = targetState.destination.tabIndex()
    return when {
        targetState.destination.isOverlay() -> ExitTransition.KeepUntilTransitionsFinished
        from >= 0 && to >= 0 -> slideOutOfContainer(
            if (to > from) SlideDirection.Left else SlideDirection.Right, tween(SLIDE_MS),
        )
        else -> fadeOut(tween(SLIDE_MS))
    }
}

@Composable
fun BikeDashboardApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showTabs = tabs.any { tab -> destination?.hasRoute(tab.route::class) == true }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                AnimatedVisibility(
                    visible = showTabs,
                    enter = slideInVertically(tween(SLIDE_MS)) { it },
                    exit = slideOutVertically(tween(SLIDE_MS)) { it },
                ) {
                    FloatingTabBar(
                        selected = destination.tabIndex(),
                        onSelect = { i ->
                            nav.navigate(tabs[i].route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onAdd = { nav.navigate(EditRoute()) },
                    )
                }
            },
            contentWindowInsets = NoInsets,
        ) { padding ->
            NavHost(
                nav,
                startDestination = DashboardRoute,
                modifier = Modifier.padding(padding),
                enterTransition = { tabEnter() },
                exitTransition = { tabExit() },
                popEnterTransition = { tabEnter() },
                popExitTransition = { tabExit() },
            ) {
                composable<DashboardRoute> {
                    DashboardScreen(
                        onAddFillUp = { nav.navigate(EditRoute()) },
                        onImport = { nav.navigate(ImportRoute) },
                    )
                }
                composable<HistoryRoute> { entry ->
                    val deletedId by entry.savedStateHandle.getStateFlow<Long?>(DELETED_FILL_UP_ID, null).collectAsState()
                    HistoryScreen(
                        onAddFillUp = { nav.navigate(EditRoute()) },
                        onOpenFillUp = { id -> nav.navigate(EditRoute(id)) },
                        deletingId = deletedId,
                        onDeleteFinished = { entry.savedStateHandle[DELETED_FILL_UP_ID] = null },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(onImport = { nav.navigate(ImportRoute) })
                }
                composable<ImportRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) {
                    AppBackground { ImportScreen(onBack = { nav.popBackStack() }) }
                }
                composable<EditRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) { entry ->
                    AppBackground {
                        EditScreen(
                            fillUpId = entry.toRoute<EditRoute>().id,
                            onDone = { nav.popBackStack() },
                            // History slides the row out and then deletes it, so you can see which one went.
                            onDeleted = { id ->
                                nav.previousBackStackEntry?.savedStateHandle?.set(DELETED_FILL_UP_ID, id)
                                nav.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bottom bar: one pill per tab, the chosen one wider with its name showing,
 * and an accent "+" pill for adding a fill-up.
 */
@Composable
private fun FloatingTabBar(selected: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSelected = i == selected
            val weight by animateFloatAsState(if (isSelected) 2.6f else 1f, tween(SLIDE_MS), label = "tabWeight")
            val fill by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "tabFill",
            )
            Row(
                Modifier
                    .weight(weight)
                    .height(56.dp)
                    .clip(PillShape)
                    .background(fill)
                    .border(outline(), PillShape)
                    .clickable(role = Role.Tab) { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon, contentDescription = tab.label)
                AnimatedVisibility(isSelected) {
                    Text("  ${tab.label}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
            }
        }
        Box(
            Modifier
                .size(56.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.tertiary)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add fill-up", tint = MaterialTheme.colorScheme.onTertiary)
        }
    }
}
