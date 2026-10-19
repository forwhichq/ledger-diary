package com.ledgerdiary.app.backup

import com.google.gson.annotations.SerializedName

/**
 * 备份文件的顶层结构。
 * version 字段是给未来的自己留的：以后加字段时，老版本 App 打开新备份
 * 或者新版本 App 打开老备份，都能靠它决定怎么兼容。
 */
data class BackupFile(
    @SerializedName("format") val format: String = FORMAT,
    @SerializedName("version") val version: Int = 1,
    @SerializedName("appVersion") val appVersion: String = "1.0.0",
    @SerializedName("exportedAt") val exportedAt: Long = System.currentTimeMillis(),
    @SerializedName("deviceId") val deviceId: String = "",
    @SerializedName("profile") val profile: ProfileDto? = null,
    @SerializedName("transactions") val transactions: List<TransactionDto> = emptyList(),
    @SerializedName("categories") val categories: List<CategoryDto> = emptyList(),
    @SerializedName("diaryEntries") val diaryEntries: List<DiaryEntryDto> = emptyList(),
    @SerializedName("diarySegments") val diarySegments: List<DiarySegmentDto> = emptyList(),
    @SerializedName("diaryPhotos") val diaryPhotos: List<DiaryPhotoDto> = emptyList()
) {
    companion object { const val FORMAT = "ledger-diary-backup" }
}

data class ProfileDto(
    @SerializedName("nickname") val nickname: String = "",
    @SerializedName("avatarFile") val avatarFile: String? = null,
    @SerializedName("deviceId") val deviceId: String = "",
    @SerializedName("deviceLabel") val deviceLabel: String = ""
)

data class TransactionDto(
    @SerializedName("id") val id: String,
    @SerializedName("direction") val direction: String,
    @SerializedName("amountCents") val amountCents: Long,
    @SerializedName("categoryName") val categoryName: String,
    @SerializedName("note") val note: String = "",
    @SerializedName("date") val date: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long = createdAt,
    @SerializedName("accountName") val accountName: String? = null
)

data class CategoryDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("icon") val icon: String = "🏷️",
    @SerializedName("colorHex") val colorHex: String = "#607D8B",
    @SerializedName("sortOrder") val sortOrder: Int = 0,
    @SerializedName("usageCount") val usageCount: Int = 0
)

data class DiaryEntryDto(
    @SerializedName("id") val id: String,
    @SerializedName("date") val date: String,
    @SerializedName("mood") val mood: Int = 0,
    @SerializedName("tags") val tags: String = "",
    @SerializedName("weather") val weather: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long = createdAt
)

data class DiarySegmentDto(
    @SerializedName("id") val id: String,
    @SerializedName("entryId") val entryId: String,
    @SerializedName("text") val text: String = "",
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long = createdAt,
    @SerializedName("sortOrder") val sortOrder: Int = 0
)

data class DiaryPhotoDto(
    @SerializedName("id") val id: String,
    @SerializedName("entryId") val entryId: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
    @SerializedName("createdAt") val createdAt: Long = 0
)

/** CSV 导出用的一行（只管账单，方便丢进 Excel） */
data class CsvRow(
    val date: String,
    val time: String,
    val direction: String,
    val amount: String,
    val category: String,
    val note: String
)
