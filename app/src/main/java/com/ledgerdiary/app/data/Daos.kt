package com.ledgerdiary.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND date >= :from AND date <= :to ORDER BY date DESC, createdAt DESC")
    fun observeRange(from: String, to: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND date >= :from AND date <= :to ORDER BY date DESC, createdAt DESC")
    suspend fun listRange(from: String, to: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL")
    suspend fun listAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TransactionEntity>)

    @Query("UPDATE transactions SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("UPDATE transactions SET deletedAt = NULL, updatedAt = :ts WHERE id = :id")
    suspend fun restore(id: String, ts: Long)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY usageCount DESC, sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY usageCount DESC, sortOrder ASC, name ASC")
    suspend fun listAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CategoryEntity>)

    @Query("UPDATE categories SET usageCount = usageCount + 1 WHERE name = :name")
    suspend fun bumpUsage(name: String)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY date DESC")
    fun observeEntries(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE date = :date LIMIT 1")
    suspend fun entryByDate(date: String): DiaryEntryEntity?

    @Query("SELECT * FROM diary_entries WHERE id = :id LIMIT 1")
    suspend fun entryById(id: String): DiaryEntryEntity?

    @Query("SELECT * FROM diary_entries")
    suspend fun allEntries(): List<DiaryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(e: DiaryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(items: List<DiaryEntryEntity>)

    @Query("SELECT * FROM diary_segments WHERE entryId = :entryId ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun segments(entryId: String): List<DiarySegmentEntity>

    @Query("SELECT * FROM diary_segments")
    suspend fun allSegments(): List<DiarySegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSegments(items: List<DiarySegmentEntity>)

    @Query("DELETE FROM diary_segments WHERE entryId = :entryId")
    suspend fun deleteSegments(entryId: String)

    @Query("SELECT * FROM diary_photos WHERE entryId = :entryId ORDER BY createdAt ASC")
    suspend fun photos(entryId: String): List<DiaryPhotoEntity>

    @Query("SELECT * FROM diary_photos")
    suspend fun allPhotos(): List<DiaryPhotoEntity>

    @Query("SELECT COUNT(*) FROM diary_photos")
    fun observePhotoCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotos(items: List<DiaryPhotoEntity>)

    @Query("DELETE FROM diary_photos WHERE id = :id")
    suspend fun deletePhoto(id: String)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("SELECT DISTINCT date FROM diary_entries")
    suspend fun datesWithDiary(): List<String>

    @Query("DELETE FROM diary_entries")
    suspend fun clearEntries()

    @Query("DELETE FROM diary_segments")
    suspend fun clearSegments()

    @Query("DELETE FROM diary_photos")
    suspend fun clearPhotos()

    @Transaction
    suspend fun clearAll() {
        clearPhotos(); clearSegments(); clearEntries()
    }
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 'local' LIMIT 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 'local' LIMIT 1")
    suspend fun getProfile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProfileEntity)
}
