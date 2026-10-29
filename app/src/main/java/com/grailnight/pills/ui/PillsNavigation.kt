package com.grailnight.pills.ui

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.grailnight.pills.R
import com.grailnight.pills.ui.edit.EditScreen
import com.grailnight.pills.ui.edit.EditViewModel
import com.grailnight.pills.ui.history.HistoryScreen
import com.grailnight.pills.ui.meds.MedsScreen
import com.grailnight.pills.ui.settings.SettingsScreen
import com.grailnight.pills.ui.today.TodayScreen

private enum class Tab(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Today("today", R.string.nav_today, Icons.Outlined.Today, Icons.Rounded.Today),
    Meds("meds", R.string.nav_meds, Icons.Outlined.Medication, Icons.Rounded.Medication),
    History("history", R.string.nav_history, Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}

private const val EDIT_ROUTE = "edit?${EditViewModel.ARG_ID}={${EditViewModel.ARG_ID}}"

private fun NavHostController.openEditor(id: Long = 0) = navigate("edit?${EditViewModel.ARG_ID}=$id")

@Composable
fun PillsNavigation() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Today.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Tab.Today.route) { TodayScreen(onAddMedication = { navController.openEditor() }) }
            composable(Tab.Meds.route) {
                MedsScreen(onAdd = { navController.openEditor() }, onEdit = { navController.openEditor(it) })
            }
            composable(Tab.History.route) { HistoryScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(
                route = EDIT_ROUTE,
                arguments = listOf(navArgument(EditViewModel.ARG_ID) { type = NavType.LongType; defaultValue = 0L }),
                enterTransition = { slideInVertically { it / 8 } + fadeIn() },
                exitTransition = { fadeOut() },
                popExitTransition = { slideOutVertically { it / 8 } + fadeOut() },
            ) {
                EditScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}
