package com.ledgerdiary.app.util

import com.ledgerdiary.app.data.DiaryEntryEntity

/**
 * 日记的「天 / 篇」关系放在这里统一算，别在界面里各写一遍。
 *
 * 数据模型：diary_entries 一行 = 一篇日记，date 上只有普通索引，
 * 所以同一天可以有多行。界面上的「这天」是一个分组，不是一行。
 */
object DiaryUtils {

    /** 一天的全部日记（按写入时间正序：早上写的在前，晚上补的在后）。 */
    data class DiaryDay(
        val date: String,
        val entries: List<DiaryEntryEntity>
    ) {
        /** 这天一共几篇。 */
        val count: Int get() = entries.size

        /** 这天最后一次填的心情；都没填就是 0。 */
        val mood: Int get() = dayMood(entries)
    }

    /**
     * 「当天心情」的取法：取这天最后一篇填了心情的日记。
     * 只写一篇时等价于以前的行为；写多篇时以最后写的那篇为准 —— 一天结束时的心情，
     * 比早上那篇更能代表这一天。
     */
    fun dayMood(entries: List<DiaryEntryEntity>): Int =
        entries.sortedBy { it.createdAt }.lastOrNull { it.mood > 0 }?.mood ?: 0

    /**
     * 按天分组：日期倒序（今天在最上面），天内按写入时间正序（第 1 篇在最上面）。
     * 天内正序是为了像一条时间线往下读 —— 早上写的在早上补的前面，
     * 也和日历页的「第 N 篇」编号顺序一致。
     */
    fun groupByDay(entries: List<DiaryEntryEntity>): List<DiaryDay> =
        entries.groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) -> DiaryDay(date, list.sortedBy { it.createdAt }) }

    /** date -> 当天心情，统计页画折线用。 */
    fun moodByDate(entries: List<DiaryEntryEntity>): Map<String, Int> =
        entries.groupBy { it.date }.mapValues { (_, list) -> dayMood(list) }
}
