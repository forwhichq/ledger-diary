package com.ledgerdiary.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class Repository private constructor(private val db: AppDatabase) {

    val transactions: Flow<List<TransactionEntity>> = db.transactionDao().observeAll()
    val categories: Flow<List<CategoryEntity>> = db.categoryDao().observeAll()
    val diaryEntries: Flow<List<DiaryEntryEntity>> = db.diaryDao().observeEntries()
    val profile: Flow<ProfileEntity?> = db.profileDao().observeProfile()
    val photoCount: Flow<Int> = db.diaryDao().observePhotoCount()

    suspend fun addTransaction(
        direction: String,
        amountCents: Long,
        categoryName: String,
        note: String,
        date: String
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.transactionDao().upsert(
            TransactionEntity(
                id = id,
                direction = direction,
                amountCents = amountCents,
                categoryName = categoryName.ifBlank { "未分类" },
                note = note,
                date = date,
                createdAt = now
            )
        )
        ensureCategory(categoryName.ifBlank { "未分类" })
        db.categoryDao().bumpUsage(categoryName.ifBlank { "未分类" })
        return id
    }

    suspend fun updateTransaction(t: TransactionEntity) {
        db.transactionDao().upsert(t.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTransaction(id: String) {
        db.transactionDao().softDelete(id, System.currentTimeMillis())
    }

    suspend fun restoreTransaction(id: String) {
        db.transactionDao().restore(id, System.currentTimeMillis())
    }

    private suspend fun ensureCategory(name: String) {
        if (db.categoryDao().byName(name) == null) {
            db.categoryDao().upsert(
                CategoryEntity(id = UUID.randomUUID().toString(), name = name)
            )
        }
    }

    suspend fun renameCategory(old: String, new: String) {
        val existing = db.categoryDao().byName(old) ?: return
        db.categoryDao().upsert(existing.copy(name = new))
    }

    // ---------- 日记 ----------

    suspend fun getOrCreateEntry(date: String): DiaryEntryEntity {
        db.diaryDao().entryByDate(date)?.let { return it }
        val now = System.currentTimeMillis()
        val e = DiaryEntryEntity(id = UUID.randomUUID().toString(), date = date, createdAt = now)
        db.diaryDao().upsertEntry(e)
        return e
    }

    suspend fun loadDiary(date: String): DiaryBundle? {
        val e = db.diaryDao().entryByDate(date) ?: return null
        return DiaryBundle(
            entry = e,
            segments = db.diaryDao().segments(e.id),
            photos = db.diaryDao().photos(e.id)
        )
    }

    suspend fun loadDiaryById(id: String): DiaryBundle? {
        val e = db.diaryDao().entryById(id) ?: return null
        return DiaryBundle(
            entry = e,
            segments = db.diaryDao().segments(e.id),
            photos = db.diaryDao().photos(e.id)
        )
    }

    suspend fun saveDiary(bundle: DiaryBundle) {
        val now = System.currentTimeMillis()
        db.diaryDao().upsertEntry(bundle.entry.copy(updatedAt = now))
        db.diaryDao().deleteSegments(bundle.entry.id)
        db.diaryDao().upsertSegments(
            bundle.segments.mapIndexed { i, s -> s.copy(sortOrder = i) }
        )
        db.diaryDao().upsertPhotos(bundle.photos)
    }

    suspend fun deleteDiary(entryId: String) {
        db.diaryDao().deleteEntry(entryId)
    }

    suspend fun diaryDates(): Set<String> = db.diaryDao().datesWithDiary().toSet()

    suspend fun photosOf(entryId: String) = db.diaryDao().photos(entryId)

    suspend fun removePhoto(id: String) = db.diaryDao().deletePhoto(id)

    // ---------- 档案 ----------

    suspend fun saveProfile(p: ProfileEntity) {
        db.profileDao().upsert(p.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun getProfile(): ProfileEntity? = db.profileDao().getProfile()

    // ---------- 统计 ----------

    suspend fun listTransactions(from: String, to: String): List<TransactionEntity> =
        db.transactionDao().listRange(from, to)

    suspend fun listAllTransactions(): List<TransactionEntity> = db.transactionDao().listAll()

    companion object {
        @Volatile private var INSTANCE: Repository? = null
        fun get(context: Context): Repository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Repository(AppDatabase.get(context)).also { INSTANCE = it }
            }
    }
}

data class DiaryBundle(
    val entry: DiaryEntryEntity,
    val segments: List<DiarySegmentEntity>,
    val photos: List<DiaryPhotoEntity>
)
