package com.ledgerdiary.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ledgerdiary.app.ui.screens.CalendarScreen
import com.ledgerdiary.app.ui.screens.DiaryListScreen
import com.ledgerdiary.app.ui.screens.LedgerScreen
import com.ledgerdiary.app.ui.screens.SettingsScreen
import com.ledgerdiary.app.ui.screens.StatsScreen

/**
 * 底部导航的一页。列表顺序 = 手指左右滑动的顺序，
 * 所以顺序本身就是「页面怎么排」，不要为了美观随意调整。
 */
internal data class MainTab(val route: String, val label: String, val icon: ImageVector)

internal val MainTabs = listOf(
    MainTab("ledger", "流水", Icons.Outlined.ReceiptLong),
    MainTab("diary", "日记", Icons.Outlined.MenuBook),
    MainTab("stats", "统计", Icons.Outlined.PieChart),
    MainTab("calendar", "日历", Icons.Outlined.CalendarMonth),
    MainTab("me", "我的", Icons.Outlined.Person)
)

/**
 * 五个主页面装在一个 HorizontalPager 里。
 *
 * 为什么不用 NavHost 来回切换：以前点底部导航是 nav.navigate(route)，
 * 五个页面在 NavHost 里是「前进/后退」关系，转场永远是从右边滑进来 ——
 * 从统计点日记，内容却是左滑进来的，方向跟手指的位置对不上，看着就别扭。
 * 换成 pager 之后，页面之间的关系变成「左右并排」，点导航和手指滑动
 * 走的是同一套位置，方向感就一致了。
 *
 * 详情页（记一笔、写日记、备份等）仍然走 NavHost，从下方淡入滑出。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPager(
    pager: PagerState,
    vm: AppViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pager,
        modifier = modifier.fillMaxSize(),
        // 预先组合左右各一页：滑到一半时邻页已经画好了，不会白屏一下
        beyondBoundsPageCount = 1,
        key = { MainTabs[it].route }
    ) { page ->
        when (MainTabs[page].route) {
            "ledger" -> LedgerScreen(
                vm = vm,
                onAdd = { onNavigate("add") },
                onEdit = { id -> onNavigate("add?id=$id") }
            )

            "diary" -> DiaryListScreen(
                vm = vm,
                onOpenEntry = { id -> onNavigate("diaryEdit?id=$id") },
                onNewEntry = { date -> onNavigate("diaryEdit?date=$date") }
            )

            "stats" -> StatsScreen(vm)

            "calendar" -> CalendarScreen(
                vm = vm,
                onAddForDate = { date -> onNavigate("add?date=$date") },
                onOpenDiaryEntry = { id -> onNavigate("diaryEdit?id=$id") },
                onNewDiaryForDate = { date -> onNavigate("diaryEdit?date=$date") }
            )

            else -> SettingsScreen(
                vm = vm,
                onOpenProfile = { onNavigate("profile") },
                onOpenBackup = { onNavigate("backup") }
            )
        }
    }
}
