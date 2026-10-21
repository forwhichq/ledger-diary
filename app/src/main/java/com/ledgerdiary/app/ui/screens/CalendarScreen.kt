package com.ledgerdiary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerdiary.app.data.TransactionEntity
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.components.MoneyText
import com.ledgerdiary.app.ui.components.MoodDot
import com.ledgerdiary.app.ui.components.moodText
import com.ledgerdiary.app.ui.components.SurfaceCard
import com.ledgerdiary.app.ui.theme.ExpenseRed
import com.ledgerdiary.app.ui.theme.IncomeGreen
import com.ledgerdiary.app.ui.theme.MoodPalette
import com.ledgerdiary.app.util.DateUtils
import com.ledgerdiary.app.util.DiaryUtils
import com.ledgerdiary.app.util.Money
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    vm: AppViewModel,
    onAddForDate: (String) -> Unit,
    onOpenDiaryEntry: (String) -> Unit,
    onNewDiaryForDate: (String) -> Unit
) {
    val all by vm.transactions.collectAsState()
    val entries by vm.diaryEntries.collectAsState()
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(DateUtils.today()) }

    val txByDate = remember(all) { all.groupBy { it.date } }
    // 一天可以有多篇，所以这里是 groupBy 而不是 associateBy
    val diaryByDate = remember(entries) { entries.groupBy { it.date } }
    // 每天的心情色：日历格子上直接用颜色表示当天心情，比一个「有日记」的绿点信息量大
    val dayMoodMap = remember(entries) { DiaryUtils.moodByDate(entries) }

    val first = ym.atDay(1)
    val daysInMonth = ym.lengthOfMonth()
    val leading = (first.dayOfWeek.value - 1)
    val cells = leading + daysInMonth
    val rows = (cells + 6) / 7

    val monthList = remember(all, ym) {
        all.filter { it.date.startsWith("%04d-%02d".format(ym.year, ym.monthValue)) }
    }
    val monthExpense = monthList.filter { it.direction == "EXPENSE" }.sumOf { it.amountCents }
    val monthIncome = monthList.filter { it.direction == "INCOME" }.sumOf { it.amountCents }

    val selList = txByDate[selected] ?: emptyList()
    val selDiaries = remember(diaryByDate, selected) {
        (diaryByDate[selected] ?: emptyList()).sortedBy { it.createdAt }
    }
    // 当天心情：取当天最后一篇填了心情的（只写一篇时和以前完全一样）
    val selMood = remember(selDiaries) { DiaryUtils.dayMood(selDiaries) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("日历", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 8.dp))
        }

        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { ym = ym.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月")
                    }
                    Text(
                        "%d 年 %d 月".format(ym.year, ym.monthValue),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { ym = ym.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("支出 " + Money.centsToString(monthExpense),
                        style = MaterialTheme.typography.labelSmall, color = ExpenseRed)
                    Spacer(Modifier.weight(1f))
                    Text("收入 " + Money.centsToString(monthIncome),
                        style = MaterialTheme.typography.labelSmall, color = IncomeGreen)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                        Text(
                            d,
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val idx = r * 7 + c
                            val day = idx - leading + 1
                            if (day in 1..daysInMonth) {
                                val dateStr = "%04d-%02d-%02d".format(ym.year, ym.monthValue, day)
                                val hasTx = txByDate.containsKey(dateStr)
                                val hasDiary = diaryByDate.containsKey(dateStr)
                                val diaryCount = diaryByDate[dateStr]?.size ?: 0
                                val dayMood = dayMoodMap[dateStr] ?: 0
                                val isToday = dateStr == DateUtils.today()
                                val isSel = dateStr == selected
                                // 有日记的那天：底色是心情色（没填心情就用中性灰），
                                // 这样扫一眼日历就知道这个月哪天开心、哪天难受。
                                val moodColor = MoodPalette[dayMood.coerceIn(0, 5)]
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isSel -> MaterialTheme.colorScheme.primaryContainer
                                                hasDiary -> moodColor.copy(alpha = if (dayMood > 0) 0.40f else 0.16f)
                                                isToday -> MaterialTheme.colorScheme.surfaceVariant
                                                else -> androidx.compose.ui.graphics.Color.Transparent
                                            }
                                        )
                                        .clickable { selected = dateStr },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            day.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isToday || isSel) FontWeight.SemiBold
                                            else FontWeight.Normal,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (hasTx) Box(
                                                Modifier.size(4.dp).clip(RoundedCornerShape(50))
                                                    .background(ExpenseRed)
                                            )
                                            // 心情点：颜色就是当天心情（没填心情显示中性灰点）
                                            if (hasDiary) Box(
                                                Modifier.size(6.dp).clip(RoundedCornerShape(50))
                                                    .background(moodColor)
                                            )
                                            // 一天多篇：再加一个小点，提示这天不止一篇
                                            if (diaryCount > 1) Box(
                                                Modifier.size(4.dp).clip(RoundedCornerShape(50))
                                                    .background(MoodPalette[2])
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(ExpenseRed, "有账")
                    LegendDot(MoodPalette[4], "心情好")
                    LegendDot(MoodPalette[2], "心情一般")
                    LegendDot(MoodPalette[1], "心情差")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "日记那天的底色和圆点就是当天心情；没填心情是灰的。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DateUtils.friendlyDate(selected), style = MaterialTheme.typography.titleMedium)
                    val sub = buildString {
                        if (selList.isNotEmpty()) {
                            val e = selList.filter { it.direction == "EXPENSE" }.sumOf { it.amountCents }
                            val i = selList.filter { it.direction == "INCOME" }.sumOf { it.amountCents }
                            append("支出 " + Money.centsToString(e))
                            if (i > 0) append(" · 收入 " + Money.centsToString(i))
                        } else append("这天没有记账")
                    }
                    Text(sub, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = { onAddForDate(selected) }) {
                    Text("记一笔")
                }
            }
        }

        items(selList, key = { it.id }) { t ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t.categoryName, Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge)
                    Text(DateUtils.timeOf(t.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    MoneyText(t.amountCents, t.direction == "INCOME")
                }
            }
        }

        item {
            // 一天可以有多篇：先把这天已有的篇列出来（点哪篇进哪篇），
            // 再给一个「再写一篇」入口。日历是找日子的地方，不该只能看不能进。
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                selDiaries.isEmpty() -> "这天还没有日记"
                                selDiaries.size == 1 -> "这天有 1 篇日记"
                                else -> "这天有 " + selDiaries.size + " 篇日记"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (selDiaries.isEmpty()) {
                                "点下面「写这篇」，补写这一天"
                            } else {
                                "当天心情 " + moodText(selMood) + " · 点某一篇进去改"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selMood > 0) MoodDot(selMood, 12)
                }

                // 当天每一篇：时间 + 地点 + 心情 + 一句话预览
                selDiaries.forEachIndexed { i, e ->
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenDiaryEntry(e.id) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            DateUtils.timeOf(e.createdAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "第 " + (i + 1) + " 篇" +
                                        (e.location?.takeIf { it.isNotBlank() }?.let { " · " + it } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                        if (e.mood > 0) MoodDot(e.mood, 10)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "打开",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                FilledTonalButton(
                    onClick = { onNewDiaryForDate(selected) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selDiaries.isEmpty()) "写这篇" else "再写一篇")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
