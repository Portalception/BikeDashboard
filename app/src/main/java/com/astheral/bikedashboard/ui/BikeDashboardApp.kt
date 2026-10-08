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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.ui.profile.ProfileSetupScreen
import com.astheral.bikedashboard.ui.profile.ProfileSwitcherSheet
import com.astheral.bikedashboard.ui.profile.ProfilesViewModel
import com.astheral.bikedashboard.ui.details.BikeDetailsScreen
import com.astheral.bikedashboard.ui.settings.IdeasScreen
import com.astheral.bikedashboard.ui.settings.PersonalisationScreen
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

/** Add a bike when [id] is 0, otherwise edit that bike. */
@Serializable data class ProfileSetupRoute(val id: Long = 0)

@Serializable object IdeasRoute
@Serializable object PersonalisationRoute
@Serializable object BikeDetailsRoute

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
private fun NavDestination?.isOverlay() = this != null && !hasRoute(DashboardRoute::class) && !hasRoute(HistoryRoute::class) && !hasRoute(SettingsRoute::class)

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

/**
 * The back gesture follows your finger with the same slides as a tap would: a tab slides back
 * towards the dashboard, and a page opened over the tabs slides down. Without this the library
 * shrinks the page while you swipe, which looked like a glitchy zoom.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.backEnter(): EnterTransition =
    if (initialState.destination.isOverlay()) EnterTransition.None else tabEnter()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.backExit(): ExitTransition =
    if (initialState.destination.isOverlay()) slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) else tabExit()

@Composable
fun BikeDashboardApp(profilesViewModel: ProfilesViewModel = viewModel()) {
    val profiles by profilesViewModel.state.collectAsState()
    val all = profiles.all ?: return // still loading
    if (all.isEmpty()) {
        // First launch: set up a bike before anything else.
        AppBackground { ProfileSetupScreen(profileId = 0, onDone = {}) }
        return
    }
    val nav = rememberNavController()
    var switching by remember { mutableStateOf(false) }
    val profile = profiles.active
    val openSwitcher = { switching = true }
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
                predictivePopEnterTransition = { backEnter() },
                predictivePopExitTransition = { backExit() },
            ) {
                composable<DashboardRoute> {
                    DashboardScreen(
                        profile = profile,
                        onProfileClick = openSwitcher,
                        onAddFillUp = { nav.navigate(EditRoute()) },
                        onImport = { nav.navigate(ImportRoute) },
                    )
                }
                composable<HistoryRoute> { entry ->
                    val deletedId by entry.savedStateHandle.getStateFlow<Long?>(DELETED_FILL_UP_ID, null).collectAsState()
                    HistoryScreen(
                        profile = profile,
                        onProfileClick = openSwitcher,
                        onAddFillUp = { nav.navigate(EditRoute()) },
                        onOpenFillUp = { id -> nav.navigate(EditRoute(id)) },
                        deletingId = deletedId,
                        onDeleteFinished = { entry.savedStateHandle[DELETED_FILL_UP_ID] = null },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onImport = { nav.navigate(ImportRoute) },
                        onProfileClick = openSwitcher,
                        onEditProfile = { id -> nav.navigate(ProfileSetupRoute(id)) },
                        onOpenIdeas = { nav.navigate(IdeasRoute) },
                        onOpenPersonalisation = { nav.navigate(PersonalisationRoute) },
                        onOpenDetails = { nav.navigate(BikeDetailsRoute) },
                    )
                }
                composable<ProfileSetupRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) { entry ->
                    AppBackground {
                        ProfileSetupScreen(profileId = entry.toRoute<ProfileSetupRoute>().id, onDone = { nav.popBackStack() })
                    }
                }
                composable<IdeasRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) {
                    AppBackground { IdeasScreen(onBack = { nav.popBackStack() }) }
                }
                composable<PersonalisationRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) {
                    AppBackground { PersonalisationScreen(onBack = { nav.popBackStack() }) }
                }
                composable<BikeDetailsRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) {
                    AppBackground { BikeDetailsScreen(onBack = { nav.popBackStack() }) }
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

    if (switching) {
        ProfileSwitcherSheet(
            state = profiles,
            onPick = { profilesViewModel.switchTo(it.id); switching = false },
            onEdit = { switching = false; nav.navigate(ProfileSetupRoute(it.id)) },
            onAdd = { switching = false; nav.navigate(ProfileSetupRoute()) },
            onDismiss = { switching = false },
        )
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
