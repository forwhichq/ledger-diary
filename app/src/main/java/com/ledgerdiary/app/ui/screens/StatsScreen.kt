package com.ledgerdiary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ledgerdiary.app.data.TransactionEntity
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.components.*
import com.ledgerdiary.app.ui.theme.ExpenseRed
import com.ledgerdiary.app.ui.theme.IncomeGreen
import com.ledgerdiary.app.util.DateUtils
import com.ledgerdiary.app.util.DiaryUtils
import com.ledgerdiary.app.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val CHART_COLORS = listOf(
    Color(0xFF1F6F5C), Color(0xFF4F9D8C), Color(0xFF7FBF9E),
    Color(0xFFD9A441), Color(0xFFD9544D), Color(0xFF8E7CC3),
    Color(0xFF5B8FF9), Color(0xFF56A3A6), Color(0xFFB58B69), Color(0xFF7A8B99)
)

private enum class Period(val label: String) { WEEK("周"), MONTH("月"), YEAR("年"), CUSTOM("自定") }

/**
 * 统计页。两个 tab：
 *   1) 收支总览：本月数字、分类排行、12 个月趋势
 *   2) 支出 × 心情：把「花了多少钱」和「那天心情如何」画在同一张图上
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: AppViewModel) {
    val all by vm.transactions.collectAsState()
    val diary by vm.diaryEntries.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var period by remember { mutableStateOf(Period.MONTH) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    // 自定义区间：默认最近 30 天
    var customFrom by remember { mutableStateOf(LocalDate.now().minusDays(29).toString()) }
    var customTo by remember { mutableStateOf(LocalDate.now().toString()) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    // 点分类排行 → 看这个分类在当前区间的明细
    var detailCategory by remember { mutableStateOf<String?>(null) }

    val range = remember(period, anchor, customFrom, customTo) {
        if (period == Period.CUSTOM) customFrom to customTo else rangeOf(period, anchor)
    }
    val list = remember(all, range) {
        all.filter { it.date >= range.first && it.date <= range.second }
    }

    // 分类排行（必须在 LazyColumn 之外计算：LazyListScope 不是 @Composable 作用域）
    val catRank = remember(list) {
        list.filter { it.direction == "EXPENSE" }
            .groupBy { it.categoryName }
            .map { (k, v) -> k to v.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
    }

    val expense = list.filter { it.direction == "EXPENSE" }.sumOf { it.amountCents }
    val income = list.filter { it.direction == "INCOME" }.sumOf { it.amountCents }
    val days = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(range.first), LocalDate.parse(range.second)) + 1
    }.getOrDefault(1L).coerceAtLeast(1L)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("统计", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("收支总览", "支出 × 心情").forEachIndexed { i, label ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (tab == i) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { tab = i }
                    ) {
                        Text(
                            label,
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (tab == i) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            SegmentedRow(
                Period.values().map { it.label },
                period.ordinal,
                onSelect = { period = Period.values()[it] }
            )
        }

        if (period == Period.CUSTOM) {
            // 自定义区间：两个日期按钮
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SurfaceCard(modifier = Modifier.weight(1f)) {
                        Text(
                            "开始",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            customFrom,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFromPicker = true }
                        )
                    }
                    SurfaceCard(modifier = Modifier.weight(1f)) {
                        Text(
                            "结束",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            customTo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showToPicker = true }
                        )
                    }
                }
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { anchor = shift(period, anchor, -1) }) { Text("‹ 上一期") }
                    Text(
                        rangeLabel(period, range),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    TextButton(onClick = { anchor = shift(period, anchor, 1) }) { Text("下一期 ›") }
                }
            }
        }

        if (tab == 0) {
            item {
                SurfaceCard {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("支出", Money.centsToString(expense), ExpenseRed, Modifier.weight(1f))
                        StatTile("收入", Money.centsToString(income), IncomeGreen, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            "结余",
                            Money.centsToString(income - expense),
                            if (expense > income) ExpenseRed else IncomeGreen,
                            Modifier.weight(1f)
                        )
                        StatTile(
                            "日均支出",
                            Money.centsToString(expense / days),
                            MaterialTheme.colorScheme.onSurface,
                            Modifier.weight(1f)
                        )
                    }
                }
            }

            // 分类排行
            if (catRank.isNotEmpty()) {
                item {
                    SurfaceCard {
                        Text("分类排行", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        DonutChart(
                            slices = catRank.mapIndexed { i, p ->
                                p.second.toFloat() to CHART_COLORS[i % CHART_COLORS.size]
                            },
                            modifier = Modifier.size(150.dp).align(Alignment.CenterHorizontally),
                            centerText = Money.compact(expense),
                            centerSub = "总支出"
                        )
                        Spacer(Modifier.height(14.dp))
                        catRank.take(8).forEachIndexed { i, (name, cents) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { detailCategory = name }
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(9.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(CHART_COLORS[i % CHART_COLORS.size])
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    Money.centsToString(cents),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "%.0f%%".format(cents * 100.0 / expense.coerceAtLeast(1)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(38.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 12 个月趋势
            item {
                val months = DateUtils.last12Months()
                val values = months.map { ym ->
                    all.filter {
                        it.direction == "EXPENSE" && it.date.startsWith(
                            "%04d-%02d".format(ym.year, ym.monthValue)
                        )
                    }.sumOf { it.amountCents } / 100f
                }
                val labels = months.map { "%d月".format(it.monthValue) }
                SurfaceCard {
                    Text("最近 12 个月支出", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    BarChart(
                        values = values,
                        labels = labels,
                        barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        gridColor = MaterialTheme.colorScheme.surfaceVariant,
                        highlightIndex = 11
                    )
                }
            }
        } else {
            // 支出 × 心情
            item {
                val start = LocalDate.parse(range.first)
                val end = LocalDate.parse(range.second)
                val n = ChronoUnit.DAYS.between(start, end).toInt() + 1
                val spendMap = list.filter { it.direction == "EXPENSE" }
                    .groupBy { it.date }.mapValues { it.value.sumOf { t -> t.amountCents } }
                // 一天可以有多篇，当天心情取最后一篇填了心情的（见 DiaryUtils.dayMood）
                val moodMap = DiaryUtils.moodByDate(diary)
                val days = (0 until n).map { start.plusDays(it.toLong()) }
                val spends = days.map { (spendMap[it.toString()] ?: 0L) / 100f }
                val moods = days.map { moodMap[it.toString()] ?: 0 }
                val labels = days.map { "%d/%d".format(it.monthValue, it.dayOfMonth) }

                SurfaceCard {
                    Text("支出 × 心情", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "柱子是当天支出，折线是当天心情（1 最差、5 最好）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    SpendMoodChart(
                        days = labels,
                        spends = spends,
                        moods = moods,
                        barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        lineColor = Color(0xFFD9A441),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        gridColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    val withMood = days.mapIndexedNotNull { i, d ->
                        val m = moods[i]
                        if (m > 0) Triple(d, spends[i], m) else null
                    }
                    if (withMood.isEmpty()) {
                        Text(
                            "这段时间还没有记过心情",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val avgSpendWhenLow = withMood.filter { it.third <= 2 }.map { it.second }
                        val avgSpendWhenHigh = withMood.filter { it.third >= 4 }.map { it.second }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (avgSpendWhenLow.isNotEmpty()) Text(
                                "心情差（1-2）的那些天，平均花 " +
                                        Money.centsToString((avgSpendWhenLow.average() * 100).toLong()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (avgSpendWhenHigh.isNotEmpty()) Text(
                                "心情好（4-5）的那些天，平均花 " +
                                        Money.centsToString((avgSpendWhenHigh.average() * 100).toLong()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                SurfaceCard {
                    Text("有心情记录的天数", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    // 按「天」数，不是按「篇」数：同一天写多篇只算一天
                    val dayCount = remember(diary) {
                        diary.groupBy { it.date }.count { (_, list) -> DiaryUtils.dayMood(list) > 0 }
                    }
                    Text(
                        dayCount.toString() + " 天",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "共 " + diary.size + " 篇",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showFromPicker) {
        DateRangePickerDialog(
            initial = customFrom,
            onDismiss = { showFromPicker = false },
            onPick = { d ->
                customFrom = d
                if (d > customTo) customTo = d
                showFromPicker = false
            }
        )
    }
    if (showToPicker) {
        DateRangePickerDialog(
            initial = customTo,
            onDismiss = { showToPicker = false },
            onPick = { d ->
                customTo = d
                if (d < customFrom) customFrom = d
                showToPicker = false
            }
        )
    }
    detailCategory?.let { cat ->
        CategoryDetailDialog(
            category = cat,
            range = range,
            items = list.filter { it.categoryName == cat },
            onDismiss = { detailCategory = null }
        )
    }
}

/**
 * 分类明细弹窗：点分类排行进来，看这个分类在当前统计区间里的每一笔。
 * 只读展示（要改某笔去流水页点它）。
 */
@Composable
private fun CategoryDetailDialog(
    category: String,
    range: Pair<String, String>,
    items: List<TransactionEntity>,
    onDismiss: () -> Unit
) {
    val rows = remember(items) { items.sortedByDescending { it.date + " " + it.createdAt } }
    val total = rows.sumOf { it.amountCents }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(category, style = MaterialTheme.typography.titleLarge)
                Text(
                    range.first + " ~ " + range.second + " · " + rows.size + " 笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    Money.centsToString(total),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                if (rows.isEmpty()) {
                    Text(
                        "这个区间没有这个分类的记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(rows, key = { it.id }) { t ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        DateUtils.relativeLabel(t.date),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        listOfNotNull(
                                            t.note.takeIf { it.isNotBlank() },
                                            DateUtils.timeOf(t.createdAt)
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    (if (t.direction == "INCOME") "+" else "-") +
                                            Money.centsToString(t.amountCents),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (t.direction == "INCOME") IncomeGreen else ExpenseRed
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }
}

/** 单个日期选择弹窗（自定义区间用）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    initial: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = runCatching {
            LocalDate.parse(initial).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val d = java.time.Instant.ofEpochMilli(ms)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    if (!d.isAfter(LocalDate.now())) onPick(d.toString())
                }
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) { DatePicker(state = state) }
}

private fun rangeOf(p: Period, anchor: LocalDate): Pair<String, String> = when (p) {
    Period.WEEK -> {
        val monday = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
        monday.toString() to monday.plusDays(6).toString()
    }
    Period.MONTH -> {
        val ym = YearMonth.from(anchor)
        ym.atDay(1).toString() to ym.atEndOfMonth().toString()
    }
    Period.YEAR -> "%04d-01-01".format(anchor.year) to "%04d-12-31".format(anchor.year)
    // CUSTOM 由 customFrom/customTo 直接给出，不走这里；保留分支只为穷尽 when
    Period.CUSTOM -> anchor.toString() to anchor.toString()
}

private fun shift(p: Period, anchor: LocalDate, delta: Long): LocalDate = when (p) {
    Period.WEEK -> anchor.plusWeeks(delta)
    Period.MONTH -> anchor.plusMonths(delta)
    Period.YEAR -> anchor.plusYears(delta)
    Period.CUSTOM -> anchor.plusMonths(delta)
}

private fun rangeLabel(p: Period, range: Pair<String, String>): String = when (p) {
    Period.WEEK -> range.first.substring(5).replace("-", "/") + " - " +
            range.second.substring(5).replace("-", "/")
    Period.MONTH -> range.first.substring(0, 7).replace("-", " 年 ") + " 月"
    Period.YEAR -> range.first.substring(0, 4) + " 年"
    Period.CUSTOM -> range.first + " ~ " + range.second
}
