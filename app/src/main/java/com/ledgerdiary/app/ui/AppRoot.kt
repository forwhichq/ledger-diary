package com.ledgerdiary.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ledgerdiary.app.ui.screens.*

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("ledger", "流水", Icons.Outlined.ReceiptLong),
    Tab("diary", "日记", Icons.Outlined.MenuBook),
    Tab("stats", "统计", Icons.Outlined.PieChart),
    Tab("calendar", "日历", Icons.Outlined.CalendarMonth),
    Tab("me", "我的", Icons.Outlined.Person)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                if (current != tab.route) {
                                    nav.navigate(tab.route) {
                                        popUpTo("ledger") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "ledger",
            modifier = Modifier.padding(padding),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(220))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(220))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(220))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(220))
            }
        ) {
            composable("ledger") {
                LedgerScreen(vm, onAdd = { nav.navigate("add") }, onEdit = { id -> nav.navigate("add?id=$id") })
            }
            composable(
                "add?id={id}&date={date}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                AddTransactionScreen(
                    vm = vm,
                    editId = entry.arguments?.getString("id"),
                    presetDate = entry.arguments?.getString("date"),
                    onDone = { nav.popBackStack() }
                )
            }
            composable("diary") {
                DiaryListScreen(vm, onOpen = { date -> nav.navigate("diaryEdit?date=$date") })
            }
            composable(
                "diaryEdit?date={date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                DiaryEditScreen(
                    vm = vm,
                    date = entry.arguments?.getString("date"),
                    onDone = { nav.popBackStack() }
                )
            }
            composable("stats") { StatsScreen(vm) }
            composable("calendar") {
                CalendarScreen(
                    vm = vm,
                    onAddForDate = { date -> nav.navigate("add?date=$date") },
                    onOpenDiary = { date -> nav.navigate("diaryEdit?date=$date") }
                )
            }
            composable("me") {
                SettingsScreen(
                    vm = vm,
                    onOpenProfile = { nav.navigate("profile") },
                    onOpenBackup = { nav.navigate("backup") }
                )
            }
            composable("profile") { ProfileScreen(vm, onBack = { nav.popBackStack() }) }
            composable("backup") { BackupScreen(vm, onBack = { nav.popBackStack() }) }
        }
    }
}
