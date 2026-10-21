package com.ledgerdiary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerdiary.app.ui.theme.ExpenseRed
import com.ledgerdiary.app.ui.theme.IncomeGreen
import com.ledgerdiary.app.util.Money

/** 统一的卡片外壳：圆角、留白一致，避免每个页面各写一套。 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** 金额文本：支出红、收入绿、带正负号。 */
@Composable
fun MoneyText(
    cents: Long,
    income: Boolean,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    showSign: Boolean = true
) {
    val color = if (income) IncomeGreen else ExpenseRed
    val sign = if (!showSign) "" else if (income) "+" else "-"
    Text(
        text = sign + Money.centsToString(cents),
        color = color,
        style = style,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

/** 分段选择器，用于「周/月/年/自定义」这类切换 */
@Composable
fun SegmentedRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 小标签（日记标签用） */
@Composable
fun TagChip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 底部「已删除 · 撤销」条，5 秒后自动消失 */
@Composable
fun UndoBar(
    message: String,
    visible: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it }
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    message,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
                Text(
                    "撤销",
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onUndo() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    }
}

/**
 * 心情的 emoji 与文字，列表 / 日历 / 统计共用一份，避免各页面写得不一样。
 * 下标 0 是「没填」，所以真实心情 1..5 对应下标 1..5。
 */
val MoodEmoji = listOf("", "😖", "😕", "😐", "🙂", "😄")
val MoodWord = listOf("未填", "很差", "一般", "还行", "不错", "很好")

fun moodText(mood: Int): String {
    if (mood <= 0) return MoodWord[0]
    val i = mood.coerceIn(1, 5)
    return MoodEmoji[i] + " " + MoodWord[i]
}

/** 心情圆点 */
@Composable
fun MoodDot(mood: Int, size: Int = 10) {
    val color = com.ledgerdiary.app.ui.theme.MoodPalette[mood.coerceIn(0, 5)]
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

/** 页面顶部的大标题 */
@Composable
fun PageHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}
