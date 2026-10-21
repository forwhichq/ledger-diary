package com.ledgerdiary.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ledgerdiary.app.ui.screens.AddTransactionScreen
import com.ledgerdiary.app.ui.screens.BackupScreen
import com.ledgerdiary.app.ui.screens.DiaryEditScreen
import com.ledgerdiary.app.ui.screens.ProfileScreen
import kotlinx.coroutines.launch

/**
 * 应用骨架，分两层：
 *
 *   1) 五个主页面（流水 / 日记 / 统计 / 日历 / 我的）装在 [MainPager] 里，
 *      左右滑动和点底部导航是同一件事 —— 都是改变 pager 的当前页。
 *      以前用 NavHost 装主页面，点导航是「navigate」，页面之间永远是从右边滑进来，
 *      从统计切到日记时方向反了，这就是滑动看起来别扭的原因。
 *
 *   2) 详情页（记一笔、写日记、备份、资料）仍然是 NavHost 里的独立页面，
 *      从下往上淡入，退出时落回去，跟主页面之间的左右关系区分开。
 */
@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = "main",
        // 主页面 → 详情页：从下方淡入上浮，暗示「盖上一层」，不参与左右滑动
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(260)) +
                    fadeIn(tween(180))
        },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(260)) +
                    fadeOut(tween(180))
        }
    ) {
        composable("main") { MainScreen(vm, nav) }

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

        composable(
            "diaryEdit?id={id}&date={date}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { entry ->
            DiaryEditScreen(
                vm = vm,
                entryId = entry.arguments?.getString("id"),
                date = entry.arguments?.getString("date"),
                onDone = { nav.popBackStack() }
            )
        }

        composable("profile") { ProfileScreen(vm, onBack = { nav.popBackStack() }) }
        composable("backup") { BackupScreen(vm, onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(vm: AppViewModel, nav: androidx.navigation.NavHostController) {
    val pager = rememberPagerState(pageCount = { MainTabs.size })
    val scope = rememberCoroutineScope()

    // 在非第一页时，系统返回键先回到第一页，而不是直接退出 App
    BackHandler(enabled = pager.currentPage != 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                MainTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pager.currentPage == index,
                        onClick = {
                            if (pager.currentPage != index) {
                                scope.launch { pager.animateScrollToPage(index) }
                            }
                        },
                        icon = { Icon(tab.icon, tab.label) },
                        label = {
                            Text(tab.label, style = MaterialTheme.typography.labelSmall)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        MainPager(
            pager = pager,
            vm = vm,
            onNavigate = { route -> nav.navigate(route) },
            modifier = Modifier.padding(padding)
        )
    }
}
