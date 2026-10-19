package com.ledgerdiary.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val ZONE: ZoneId = ZoneId.systemDefault()

    fun today(): String = LocalDate.now().toString()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun parse(date: String): LocalDate = LocalDate.parse(date, ISO)

    fun format(d: LocalDate): String = d.format(ISO)

    fun monthKey(d: LocalDate = LocalDate.now()): String =
        "%04d-%02d".format(d.year, d.monthValue)

    fun monthRange(ym: YearMonth): Pair<String, String> =
        ym.atDay(1).toString() to ym.atEndOfMonth().toString()

    /** 把毫秒时间戳显示成 "14:32" */
    fun timeOf(ts: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), ZONE)
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    /** "2026年10月19日 周日" */
    fun friendlyDate(date: String): String = try {
        val d = parse(date)
        "%d年%d月%d日 %s".format(d.year, d.monthValue, d.dayOfMonth, weekdayCn(d.dayOfWeek))
    } catch (e: Exception) { date }

    fun weekdayCn(dow: DayOfWeek): String = when (dow) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }

    /** 相对今天："今天" / "昨天" / "10月17日" */
    fun relativeLabel(date: String): String = try {
        val d = parse(date)
        val today = LocalDate.now()
        when {
            d == today -> "今天"
            d == today.minusDays(1) -> "昨天"
            d == today.minusDays(2) -> "前天"
            d.year == today.year -> "%d月%d日".format(d.monthValue, d.dayOfMonth)
            else -> "%d年%d月%d日".format(d.year, d.monthValue, d.dayOfMonth)
        }
    } catch (e: Exception) { date }

    fun last12Months(): List<YearMonth> {
        val now = YearMonth.now()
        return (11 downTo 0).map { now.minusMonths(it.toLong()) }
    }

    fun isValidDate(s: String): Boolean =
        try { LocalDate.parse(s, ISO); true } catch (e: Exception) { false }
}
