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
import com.ledgerdiary.app.ui.components.SurfaceCard
import com.ledgerdiary.app.ui.theme.ExpenseRed
import com.ledgerdiary.app.ui.theme.IncomeGreen
import com.ledgerdiary.app.ui.theme.MoodPalette
import com.ledgerdiary.app.util.DateUtils
import com.ledgerdiary.app.util.Money
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    vm: AppViewModel,
    onAddForDate: (String) -> Unit,
    onOpenDiary: (String) -> Unit
) {
    val all by vm.transactions.collectAsState()
    val entries by vm.diaryEntries.collectAsState()
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(DateUtils.today()) }

    val txByDate = remember(all) { all.groupBy { it.date } }
    val diaryByDate = remember(entries) { entries.associateBy { it.date } }

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
    val selDiary = diaryByDate[selected]

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
                                val isToday = dateStr == DateUtils.today()
                                val isSel = dateStr == selected
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isSel -> MaterialTheme.colorScheme.primaryContainer
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
                                            if (hasDiary) Box(
                                                Modifier.size(4.dp).clip(RoundedCornerShape(50))
                                                    .background(MoodPalette[4])
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
                    LegendDot(MoodPalette[4], "有日记")
                }
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
            // 有日记就点进去编辑，没有就给个「写日记」入口 —— 日历是找日子的地方，
            // 不该只能看不能进。
            SurfaceCard(modifier = Modifier.clickable { onOpenDiary(selected) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (selDiary != null) "这天有日记" else "这天还没有日记",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (selDiary != null) {
                                "心情 " + (if (selDiary.mood > 0) selDiary.mood.toString() + "/5" else "未填") +
                                        (selDiary.weather?.let { " · " + it } ?: "") +
                                        (selDiary.tags.takeIf { it.isNotBlank() }?.let { " · #" + it } ?: "")
                            } else {
                                "点这里补写这一天"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (selDiary != null) "打开" else "写",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
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
