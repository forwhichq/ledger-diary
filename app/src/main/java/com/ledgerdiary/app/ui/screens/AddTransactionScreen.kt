package com.ledgerdiary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerdiary.app.data.TransactionEntity
import com.ledgerdiary.app.ui.AppViewModel
import com.ledgerdiary.app.ui.components.SurfaceCard
import com.ledgerdiary.app.ui.theme.ExpenseRed
import com.ledgerdiary.app.ui.theme.IncomeGreen
import com.ledgerdiary.app.util.DateUtils
import com.ledgerdiary.app.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 记一笔 / 改一笔。
 * 只有金额必填，其余全可选 —— 这是用户明确要求的「记账自由度」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    vm: AppViewModel,
    editId: String?,
    presetDate: String?,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val categories by vm.categories.collectAsState()

    var loaded by remember { mutableStateOf(editId == null) }
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }

    var income by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(presetDate ?: DateUtils.today()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editId) {
        if (editId != null) {
            val t = vm.transactions.value.firstOrNull { it.id == editId }
                ?: vm.db.transactionDao().byId(editId)
            if (t != null) {
                editing = t
                income = t.direction == "INCOME"
                amount = Money.centsToString(t.amountCents)
                category = t.categoryName
                note = t.note
                date = t.date
            }
            loaded = true
        }
    }

    val suggestions = remember(categories, income) {
        categories.map { it.name }.distinct()
    }

    fun save() {
        val cents = Money.parseToCents(amount)
        if (cents == null || cents <= 0) {
            error = "请填写有效金额"
            return
        }
        scope.launch {
            val dir = if (income) "INCOME" else "EXPENSE"
            val cur = editing
            if (cur == null) {
                vm.repo.addTransaction(dir, cents, category.trim(), note.trim(), date)
            } else {
                vm.repo.updateTransaction(
                    cur.copy(
                        direction = dir,
                        amountCents = cents,
                        categoryName = category.trim().ifBlank { "未分类" },
                        note = note.trim(),
                        date = date
                    )
                )
            }
            onDone()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "记一笔" else "改一笔") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) {
                        Icon(Icons.Filled.Check, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 收 / 支 切换
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(false to "支出", true to "收入").forEach { (isIncome, label) ->
                    val active = income == isIncome
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.surface else Color.Transparent
                            )
                            .clickable { income = isIncome }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                active && isIncome -> IncomeGreen
                                active -> ExpenseRed
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // 金额显示
            SurfaceCard {
                Text(
                    "金额",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "¥",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (income) IncomeGreen else ExpenseRed
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (amount.isEmpty()) "0.00" else amount,
                        style = MaterialTheme.typography.displayLarge,
                        color = if (amount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (income) IncomeGreen else ExpenseRed
                    )
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 分类
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("分类", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        placeholder = { Text("随便填，比如 早餐", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        modifier = Modifier.weight(1.6f),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions.take(24)) { name ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (name == category)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { category = name }
                            ) {
                                Text(
                                    name,
                                    Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (name == category)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 日期 + 备注
            SurfaceCard {
                Row(
                    Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("日期", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        DateUtils.friendlyDate(date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            // 数字键盘
            NumberPad(
                onKey = { key ->
                    amount = Money.appendKey(amount, key)
                    error = null
                },
                onBackspace = {
                    if (amount.isNotEmpty()) amount = amount.dropLast(1)
                },
                onClear = { amount = "" }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        if (!d.isAfter(LocalDate.now())) date = d.toString()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/** 自绘数字键盘：比系统键盘更适合记账，且不会被输入法遮挡。 */
@Composable
private fun NumberPad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 清零：单独一行，避免误触把整数部分删掉
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable { onClear() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "清零",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable {
                                when (k) {
                                    "⌫" -> onBackspace()
                                    else -> onKey(k)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                k,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
