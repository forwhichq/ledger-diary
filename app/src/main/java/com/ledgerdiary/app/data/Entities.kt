package com.ledgerdiary.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 设计原则（为后续新增需求留后路）：
 * 1. 所有业务表的主键都是 String UUID，不用自增 Long —— 这样两台手机的记录合并时不会撞 ID，
 *    也方便以后同步。自增主键在「导入合并」场景下必然冲突。
 * 2. 所有表都有 createdAt / updatedAt，以后要做「最近修改」「增量同步」直接可用。
 * 3. 字段尽量宽松（可空），新增字段一律给默认值，配 Room Migration 增量升级。
 */

enum class Direction { EXPENSE, INCOME }

@Entity(tableName = "transactions", indices = [Index("date"), Index("categoryName")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val direction: String,          // "EXPENSE" / "INCOME"
    val amountCents: Long,          // 金额，单位「分」，永远整数
    val categoryName: String,       // 分类名，自由填写
    val note: String = "",          // 备注，可空
    val date: String,               // YYYY-MM-DD，年月日
    val createdAt: Long,            // 毫秒时间戳，仅用于排序/留痕
    val updatedAt: Long = createdAt,
    // ---- 预留扩展位 ----
    val accountName: String? = null,   // 以后做多账户用
    val deletedAt: Long? = null        // 软删除，以后做回收站用
)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "🏷️",
    val colorHex: String = "#607D8B",
    val sortOrder: Int = 0,
    val usageCount: Int = 0,
    val direction: String? = null,   // null = 收支通用
    val createdAt: Long = System.currentTimeMillis()
)

// 一天可以写多篇日记，所以 date 上不能有唯一约束，只留普通索引（按天查当天那几篇）。
// 「当天心情」不单独存，由当天几篇里的 mood 派生（见 util/DiaryUtils.kt 的 dayMoodOf）：
// 用户只写一篇时行为和以前完全一样，写多篇时以最后写的那篇为准。
@Entity(tableName = "diary_entries", indices = [Index("date")])
data class DiaryEntryEntity(
    @PrimaryKey val id: String,
    val date: String,               // YYYY-MM-DD，同一天可以有多篇
    val mood: Int = 0,              // 0 = 未填，1..5；每篇各记各的
    val tags: String = "",          // 逗号分隔
    val weather: String? = null,    // 可选，手动填
    val location: String? = null,   // 可选，手动填；每篇日记一个地址
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

@Entity(
    tableName = "diary_segments",
    foreignKeys = [ForeignKey(
        entity = DiaryEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("entryId")]
)
data class DiarySegmentEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val text: String = "",
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "diary_photos",
    foreignKeys = [ForeignKey(
        entity = DiaryEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("entryId")]
)
data class DiaryPhotoEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val segmentId: String? = null,  // 预留：以后可以挂到某一段
    val fileName: String,           // 存在 filesDir/photos/ 下
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String = "local",
    val nickname: String = "",
    val avatarFile: String? = null,
    val deviceId: String = "",       // 随机生成，仅作来源标识，不锁数据
    val deviceLabel: String = "",    // 例如 "Pixel 7"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
