package com.astheral.bikedashboard.ui

import androidx.activity.compose.BackHandler
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
import com.astheral.bikedashboard.diagnostics.CrashLogs
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import com.astheral.bikedashboard.ui.dashboard.Period
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.ui.profile.BikeSwitch
import com.astheral.bikedashboard.ui.profile.BikeSwitchOverlay
import com.astheral.bikedashboard.ui.profile.ProfileSetupScreen
import com.astheral.bikedashboard.ui.profile.ProfileSwitcherSheet
import com.astheral.bikedashboard.ui.profile.ProfilesViewModel
import com.astheral.bikedashboard.ui.details.BikeDetailsScreen
import com.astheral.bikedashboard.ui.service.CategoriesScreen
import com.astheral.bikedashboard.ui.service.IntervalsScreen
import com.astheral.bikedashboard.ui.service.ServiceEditScreen
import com.astheral.bikedashboard.ui.service.ServicingScreen
import com.astheral.bikedashboard.ui.settings.DiagnosticLogsScreen
import com.astheral.bikedashboard.ui.settings.IdeasScreen
import com.astheral.bikedashboard.ui.settings.PersonalisationScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.astheral.bikedashboard.ui.dashboard.DashboardScreen
import com.astheral.bikedashboard.ui.dashboard.Metric
import com.astheral.bikedashboard.ui.dashboard.MetricDetailScreen
import com.astheral.bikedashboard.ui.edit.EditScreen
import com.astheral.bikedashboard.ui.history.HistoryScreen
import com.astheral.bikedashboard.ui.importer.ImportScreen
import com.astheral.bikedashboard.ui.settings.SettingsScreen
import com.astheral.bikedashboard.ui.summary.FillSummaryScreen
import kotlinx.serialization.Serializable

/** The three main tabs (Dashboard, History, Settings), side by side in a pager you can swipe. */
@Serializable object TabsRoute
@Serializable object ImportRoute

/** Add a fill-up when [id] is 0, otherwise edit the fill-up with that id. */
@Serializable data class EditRoute(val id: Long = 0)

/** The summary shown right after adding fill-up [id]. */
@Serializable data class FillSummaryRoute(val id: Long)

/** Add a bike when [id] is 0, otherwise edit that bike. */
@Serializable data class ProfileSetupRoute(val id: Long = 0)

@Serializable object IdeasRoute
@Serializable object DiagnosticLogsRoute
@Serializable object PersonalisationRoute
@Serializable object BikeDetailsRoute
@Serializable object ServicingRoute
@Serializable object IntervalsRoute
@Serializable object ServiceCategoriesRoute

/** A dashboard number's detail page: [metric] is a [Metric] name, [period] the tab it opened on. */
@Serializable data class MetricRoute(val metric: String, val period: Int)

/** Add a service record when [id] is 0, otherwise edit that one. */
@Serializable data class ServiceEditRoute(val id: Long = 0)

private data class Tab(val label: String, val icon: ImageVector)

private const val DASHBOARD_TAB = 0
private const val HISTORY_TAB = 1
private const val SETTINGS_TAB = 2

/** In the order they sit in the bottom bar, which is also the order you swipe through them. */
private val tabs = listOf(
    Tab("Dashboard", Icons.Filled.Speed),
    Tab("History", Icons.Filled.History),
    Tab("Settings", Icons.Filled.Settings),
)

/** Top-level screens have the bottom bar under them, so they don't pad for the system bars again. */
val NoInsets = WindowInsets(0, 0, 0, 0)

/** History reads this from its back-stack entry to slide a deleted fill-up out, then delete it. */
const val DELETED_FILL_UP_ID = "deletedFillUpId"

private const val SLIDE_MS = 300

private fun NavDestination?.isOverlay() = this != null && !hasRoute(TabsRoute::class)

/**
 * Pages opened over the tabs (the add/edit form, import, details…) slide up over whatever was
 * showing, and what's underneath stays put. Moving between the tabs themselves is the pager's job.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(): EnterTransition =
    if (initialState.destination.isOverlay()) EnterTransition.None else fadeIn(tween(SLIDE_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(): ExitTransition =
    if (targetState.destination.isOverlay()) ExitTransition.KeepUntilTransitionsFinished else fadeOut(tween(SLIDE_MS))

/**
 * The back gesture slides a page opened over the tabs down as you swipe. Without this the
 * library shrinks the page while you swipe, which looked like a glitchy zoom.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.backEnter(): EnterTransition =
    if (initialState.destination.isOverlay()) EnterTransition.None else tabEnter()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.backExit(): ExitTransition =
    if (initialState.destination.isOverlay()) slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) else tabExit()

/**
 * [openServicingFor] opens that bike's servicing page, e.g. after tapping a reminder, and
 * [openDetailsFor] its details page, after tapping a paperwork reminder.
 */
@Composable
fun BikeDashboardApp(
    openServicingFor: Long? = null,
    onOpenedServicing: () -> Unit = {},
    openDetailsFor: Long? = null,
    onOpenedDetails: () -> Unit = {},
    profilesViewModel: ProfilesViewModel = viewModel(),
) {
    val profiles by profilesViewModel.state.collectAsState()
    val all = profiles.all ?: return // still loading
    if (all.isEmpty()) {
        // First launch: set up a bike before anything else.
        AppBackground { ProfileSetupScreen(profileId = 0, onDone = {}) }
        return
    }
    val nav = rememberNavController()
    var switching by remember { mutableStateOf(false) }
    var bikeSwitch by remember { mutableStateOf<BikeSwitch?>(null) }
    val appearances by profilesViewModel.appearances.collectAsState()
    val profile = profiles.active
    val openSwitcher = { switching = true }
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showTabs = destination?.hasRoute(TabsRoute::class) == true
    // Hoisted so the bottom bar can follow the pager, and the dashboard keeps its period
    // while another tab shows.
    val tabPager = rememberPagerState { tabs.size }
    val periodPager = rememberPagerState { Period.entries.size }
    val scope = rememberCoroutineScope()
    // Each screen opened goes into the diagnostic log's recent events.
    LaunchedEffect(destination?.route) {
        destination?.route?.let { CrashLogs.event("Opened ${screenName(it)}") }
    }
    LaunchedEffect(tabPager.currentPage) {
        if (showTabs) CrashLogs.event("Opened ${tabs[tabPager.currentPage].label} tab")
    }
    LaunchedEffect(openServicingFor) {
        val id = openServicingFor ?: return@LaunchedEffect
        if (all.any { it.id == id }) {
            profilesViewModel.switchTo(id)
            nav.navigate(ServicingRoute) { launchSingleTop = true }
        }
        onOpenedServicing()
    }
    LaunchedEffect(openDetailsFor) {
        val id = openDetailsFor ?: return@LaunchedEffect
        if (all.any { it.id == id }) {
            profilesViewModel.switchTo(id)
            nav.navigate(BikeDetailsRoute) { launchSingleTop = true }
        }
        onOpenedDetails()
    }

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
                        selected = tabPager.currentPage,
                        onSelect = { i -> scope.launch { tabPager.animateScrollToPage(i, animationSpec = tween(SLIDE_MS)) } },
                        onAdd = { nav.navigate(EditRoute()) },
                    )
                }
            },
            contentWindowInsets = NoInsets,
        ) { padding ->
            NavHost(
                nav,
                startDestination = TabsRoute,
                modifier = Modifier.padding(padding),
                enterTransition = { tabEnter() },
                exitTransition = { tabExit() },
                popEnterTransition = { tabEnter() },
                popExitTransition = { tabExit() },
                predictivePopEnterTransition = { backEnter() },
                predictivePopExitTransition = { backExit() },
            ) {
                composable<MetricRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) { entry ->
                    val route = entry.toRoute<MetricRoute>()
                    AppBackground {
                        MetricDetailScreen(
                            metric = Metric.valueOf(route.metric),
                            startPeriod = route.period,
                            profile = profile,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable<TabsRoute> { entry ->
                    // Back on History or Settings goes to the dashboard first, as it used to.
                    BackHandler(enabled = tabPager.currentPage != DASHBOARD_TAB) {
                        scope.launch { tabPager.animateScrollToPage(DASHBOARD_TAB, animationSpec = tween(SLIDE_MS)) }
                    }
                    MainTabs(tabPager, periodPager) { page ->
                        when (page) {
                            DASHBOARD_TAB -> DashboardScreen(
                                profile = profile,
                                onProfileClick = openSwitcher,
                                onAddFillUp = { nav.navigate(EditRoute()) },
                                onImport = { nav.navigate(ImportRoute) },
                                onOpenServicing = { nav.navigate(ServicingRoute) },
                                onOpenMetric = { metric, period -> nav.navigate(MetricRoute(metric.name, period.ordinal)) },
                                periodPager = periodPager,
                            )
                            HISTORY_TAB -> {
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
                            SETTINGS_TAB -> SettingsScreen(
                                onImport = { nav.navigate(ImportRoute) },
                                onProfileClick = openSwitcher,
                                onEditProfile = { id -> nav.navigate(ProfileSetupRoute(id)) },
                                onOpenIdeas = { nav.navigate(IdeasRoute) },
                                onOpenLogs = { nav.navigate(DiagnosticLogsRoute) },
                                onOpenPersonalisation = { nav.navigate(PersonalisationRoute) },
                                onOpenDetails = { nav.navigate(BikeDetailsRoute) },
                                onOpenServicing = { nav.navigate(ServicingRoute) },
                                onOpenIntervals = { nav.navigate(IntervalsRoute) },
                            )
                        }
                    }
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
                composable<DiagnosticLogsRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) {
                    AppBackground { DiagnosticLogsScreen(onBack = { nav.popBackStack() }) }
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
                composable<ServicingRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) {
                    AppBackground {
                        ServicingScreen(
                            onBack = { nav.popBackStack() },
                            onAdd = { nav.navigate(ServiceEditRoute()) },
                            onOpen = { id -> nav.navigate(ServiceEditRoute(id)) },
                            onOpenIntervals = { nav.navigate(IntervalsRoute) },
                            onOpenCategories = { nav.navigate(ServiceCategoriesRoute) },
                        )
                    }
                }
                composable<ServiceCategoriesRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) {
                    AppBackground { CategoriesScreen(onBack = { nav.popBackStack() }) }
                }
                composable<IntervalsRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) {
                    AppBackground { IntervalsScreen(onBack = { nav.popBackStack() }) }
                }
                composable<ServiceEditRoute>(
                    enterTransition = { slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) { entry ->
                    AppBackground { ServiceEditScreen(id = entry.toRoute<ServiceEditRoute>().id, onDone = { nav.popBackStack() }) }
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
                            // A new fill-up shows its summary in place of the form.
                            onSaved = { id ->
                                nav.navigate(FillSummaryRoute(id)) { popUpTo<EditRoute> { inclusive = true } }
                            },
                            // History slides the row out and then deletes it, so you can see which one went.
                            onDeleted = { id ->
                                nav.previousBackStackEntry?.savedStateHandle?.set(DELETED_FILL_UP_ID, id)
                                nav.popBackStack()
                            },
                        )
                    }
                }
                composable<FillSummaryRoute>(
                    enterTransition = { fadeIn(tween(SLIDE_MS)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS)) },
                ) { entry ->
                    AppBackground {
                        FillSummaryScreen(
                            id = entry.toRoute<FillSummaryRoute>().id,
                            onDone = {
                                // Back to the dashboard, wherever the fill-up was added from.
                                if (!nav.popBackStack(TabsRoute, inclusive = false)) nav.popBackStack()
                                scope.launch { tabPager.scrollToPage(DASHBOARD_TAB) }
                            },
                        )
                    }
                }
            }
        }
        bikeSwitch?.let { sw ->
            BikeSwitchOverlay(
                switch = sw,
                onCovered = { profilesViewModel.switchTo(sw.profile.id) },
                onFinished = { if (bikeSwitch == sw) bikeSwitch = null },
            )
        }
    }

    if (switching) {
        ProfileSwitcherSheet(
            state = profiles,
            onPick = { picked, origin ->
                switching = false
                // The switch itself happens under the animation, once the screen is covered.
                if (picked.id != profile?.id) bikeSwitch = BikeSwitch(picked, origin, appearances.of(picked.id))
            },
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
            Icon(Icons.Filled.Add, contentDescription = "Add Fill-Up", tint = MaterialTheme.colorScheme.onTertiary)
        }
    }
}

/** "com.example.EditRoute?id={id}" becomes "EditRoute", for the diagnostic log. */
private fun screenName(route: String) = route.substringBefore('?').substringBefore('/').substringAfterLast('.')
