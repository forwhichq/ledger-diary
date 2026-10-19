package com.ledgerdiary.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 手写 Canvas 图表。没用第三方图表库，因为只需要柱状/折线/环形三种，
 * 引一个几百 KB 的库不划算，而且自己画更容易做「支出柱 + 心情线」这种混合图。
 */

/** 单值柱状图：用于最近 12 个月趋势 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    barColor: Color,
    labelColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
    highlightIndex: Int = -1,
    highlightColor: Color = barColor
) {
    Box(modifier.fillMaxWidth().height(180.dp)) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            if (values.isEmpty()) return@Canvas
            val labelH = 22.dp.toPx()
            val topPad = 8.dp.toPx()
            val chartH = size.height - labelH - topPad
            val maxV = max(values.maxOrNull() ?: 0f, 1f)
            val n = values.size
            val slot = size.width / n
            val barW = (slot * 0.52f).coerceAtLeast(2f)

            // 三条水平网格线
            val gridPaint = Paint().apply {
                color = gridColor.toArgb(); strokeWidth = 1f; isAntiAlias = true
            }
            for (i in 0..2) {
                val y = topPad + chartH * i / 2f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            values.forEachIndexed { i, v ->
                val h = (v / maxV) * chartH
                val left = slot * i + (slot - barW) / 2f
                val top = topPad + chartH - h
                drawRoundRect(
                    color = if (i == highlightIndex) highlightColor else barColor,
                    topLeft = Offset(left, top),
                    size = Size(barW, h.coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(barW / 3f, barW / 3f)
                )
            }

            // 横轴标签：只画首、中、尾，避免挤成一团
            val textPaint = Paint().apply {
                color = labelColor.toArgb(); textSize = 10.dp.toPx(); isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val picks = if (n <= 6) (0 until n) else listOf(0, n / 2, n - 1)
            picks.forEach { i ->
                val cx = slot * i + slot / 2f
                drawContext.canvas.nativeCanvas.drawText(
                    labels.getOrElse(i) { "" },
                    cx,
                    size.height - 6.dp.toPx(),
                    textPaint
                )
            }
        }
    }
}

/** 混合图：柱子 = 每天支出，折线 = 每天心情（0 表示没记，断线不画） */
@Composable
fun SpendMoodChart(
    days: List<String>,          // 日期标签
    spends: List<Float>,         // 每天支出（元）
    moods: List<Int>,            // 0..5
    barColor: Color,
    lineColor: Color,
    labelColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().height(200.dp)) {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            if (days.isEmpty()) return@Canvas
            val labelH = 22.dp.toPx()
            val topPad = 10.dp.toPx()
            val chartH = size.height - labelH - topPad
            val maxSpend = max(spends.maxOrNull() ?: 0f, 1f)
            val n = days.size
            val slot = size.width / n
            val barW = (slot * 0.5f).coerceAtLeast(2f)

            for (i in 0..2) {
                val y = topPad + chartH * i / 2f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            spends.forEachIndexed { i, v ->
                val h = (v / maxSpend) * chartH
                val left = slot * i + (slot - barW) / 2f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, topPad + chartH - h),
                    size = Size(barW, h.coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(barW / 3f, barW / 3f)
                )
            }

            // 心情折线：心情 1..5 映射到图高，0 视为缺失
            val path = Path()
            var started = false
            moods.forEachIndexed { i, m ->
                if (m <= 0) { started = false; return@forEachIndexed }
                val x = slot * i + slot / 2f
                val y = topPad + chartH * (1f - (m - 1) / 4f)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
            moods.forEachIndexed { i, m ->
                if (m <= 0) return@forEachIndexed
                val x = slot * i + slot / 2f
                val y = topPad + chartH * (1f - (m - 1) / 4f)
                drawCircle(lineColor, radius = 2.5.dp.toPx(), center = Offset(x, y))
            }

            val textPaint = Paint().apply {
                color = labelColor.toArgb(); textSize = 10.dp.toPx(); isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val picks = if (n <= 6) (0 until n) else listOf(0, n / 2, n - 1)
            picks.forEach { i ->
                drawContext.canvas.nativeCanvas.drawText(
                    days.getOrElse(i) { "" },
                    slot * i + slot / 2f,
                    size.height - 6.dp.toPx(),
                    textPaint
                )
            }
        }
    }
}

/** 环形图：分类占比 */
@Composable
fun DonutChart(
    slices: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    thickness: Float = 26f,
    centerText: String? = null,
    centerSub: String? = null,
    centerTextColor: Color = Color.Unspecified
) {
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(modifier.fillMaxWidth()) {
            val total = slices.sumOf { it.first.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            val d = minOf(size.width, size.height)
            val stroke = thickness.dp.toPx()
            val inset = stroke / 2f
            var start = -90f
            slices.forEach { (v, c) ->
                val sweep = 360f * (v / total)
                drawArc(
                    color = c,
                    startAngle = start,
                    sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset + (size.width - d) / 2f, inset + (size.height - d) / 2f),
                    size = Size(d - stroke, d - stroke),
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                start += sweep
            }
        }
        if (centerText != null) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Text(
                    centerText,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = if (centerTextColor == Color.Unspecified)
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurface else centerTextColor
                )
                if (centerSub != null) {
                    androidx.compose.material3.Text(
                        centerSub,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
