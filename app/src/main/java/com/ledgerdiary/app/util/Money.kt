package com.ledgerdiary.app.util

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    /** 把 "12.34" 解析成 1234 分。返回 null 表示非法输入。 */
    fun parseToCents(input: String): Long? {
        val s = input.trim()
        if (s.isEmpty()) return null
        return try {
            val bd = BigDecimal(s)
            if (bd < BigDecimal.ZERO) return null
            if (bd.scale() > 2) return null
            bd.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        } catch (e: Exception) { null }
    }

    /** 1234 -> "12.34" */
    fun centsToString(cents: Long): String {
        val neg = cents < 0
        val abs = kotlin.math.abs(cents)
        val s = "%d.%02d".format(abs / 100, abs % 100)
        return if (neg) "-$s" else s
    }

    /** 1234 -> "¥12.34" */
    fun display(cents: Long): String = "¥" + centsToString(cents)

    /** 大额时简写：1234567 分 -> "¥1.2万" */
    fun compact(cents: Long): String {
        val yuan = cents / 100.0
        return when {
            kotlin.math.abs(yuan) >= 10000 -> "¥%.1f万".format(yuan / 10000)
            else -> display(cents)
        }
    }

    /** 键盘输入拼接：当前串 + 新按键 */
    fun appendKey(current: String, key: String): String {
        return when {
            key == "." -> if (current.contains(".")) current else if (current.isEmpty()) "0." else "$current."
            current == "0" -> key
            else -> {
                val dot = current.indexOf('.')
                if (dot >= 0 && current.length - dot > 2) current else current + key
            }
        }
    }
}
