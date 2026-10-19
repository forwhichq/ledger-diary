package com.ledgerdiary.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerdiary.app.data.TransactionEntity
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.components.*
import com.ledgerdiary.app.ui.theme.ExpenseRed
import com.ledgerdiary.app.ui.theme.IncomeGreen
import com.ledgerdiary.app.util.DateUtils
import com.ledgerdiary.app.util.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(vm: AppViewModel, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val all by vm.transactions.collectAsState()
    val scope = rememberCoroutineScope()

    // 月份筛选：null = 全部
    var month by remember { mutableStateOf<String?>(DateUtils.monthKey()) }
    var deletedId by remember { mutableStateOf<String?>(null) }
    var undoVisible by remember { mutableStateOf(false) }
    var undoJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val shown = remember(all, month) {
        if (month == null) all else all.filter { it.date.startsWith(month!!) }
    }
    val grouped = remember(shown) {
        shown.groupBy { it.date }.toSortedMap(compareByDescending { it })
    }

    val expense = shown.filter { it.direction == "EXPENSE" }.sumOf { it.amountCents }
    val income = shown.filter { it.direction == "INCOME" }.sumOf { it.amountCents }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("记一笔", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "记账日记",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            // 汇总卡
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            if (month == null) "全部记录" else month!!.replace("-", " 年 ") + " 月",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                Money.centsToString(income - expense),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "结余",
                                Modifier.padding(bottom = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            Column {
                                Text("支出", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                                Text(Money.centsToString(expense),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            }
                            Column {
                                Text("收入", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                                Text(Money.centsToString(income),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }

            // 月份切换
            item {
                val months = remember(all) {
                    (listOf(DateUtils.monthKey()) + all.map { it.date.take(7) })
                        .distinct().sortedDescending().take(12)
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScrollChip("全部", month == null) { month = null }
                    months.take(6).forEach { m ->
                        ScrollChip(m.substring(5) + "月", month == m) { month = m }
                    }
                }
            }

            if (grouped.isEmpty()) {
                item { EmptyHint("这个月还没有记账\n点右下角「记一笔」开始") }
            }

            grouped.forEach { (date, list) ->
                item(key = "h_" + date) {
                    val dayExpense = list.filter { it.direction == "EXPENSE" }.sumOf { it.amountCents }
                    val dayIncome = list.filter { it.direction == "INCOME" }.sumOf { it.amountCents }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            DateUtils.relativeLabel(date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "  " + DateUtils.weekdayCn(DateUtils.parse(date).dayOfWeek),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (dayExpense > 0) Text(
                            "支 " + Money.centsToString(dayExpense),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (dayIncome > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "收 " + Money.centsToString(dayIncome),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(list, key = { it.id }) { t ->
                    TransactionRow(
                        t,
                        onClick = { onEdit(t.id) },
                        onLongClick = {
                            scope.launch {
                                vm.repo.deleteTransaction(t.id)
                                deletedId = t.id
                                undoVisible = true
                                undoJob?.cancel()
                                undoJob = launch {
                                    delay(5000)
                                    undoVisible = false
                                }
                            }
                        }
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize().padding(bottom = 88.dp, start = 16.dp, end = 16.dp), Alignment.BottomCenter) {
            UndoBar("已删除这笔记录", undoVisible, onUndo = {
                deletedId?.let { id ->
                    scope.launch {
                        vm.repo.restoreTransaction(id)
                        undoVisible = false
                    }
                }
            })
        }
    }
}

@Composable
private fun ScrollChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        Text(
            label,
            Modifier
                .combinedClickableCompat(onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable { onClick() })

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(t: TransactionEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    val income = t.direction == "INCOME"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (income) IncomeGreen.copy(alpha = 0.12f) else ExpenseRed.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t.categoryName.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (income) IncomeGreen else ExpenseRed
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.categoryName, style = MaterialTheme.typography.bodyLarge)
                val sub = listOfNotNull(
                    t.note.takeIf { it.isNotBlank() },
                    DateUtils.timeOf(t.createdAt)
                ).joinToString(" · ")
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            MoneyText(t.amountCents, income)
        }
    }
}
