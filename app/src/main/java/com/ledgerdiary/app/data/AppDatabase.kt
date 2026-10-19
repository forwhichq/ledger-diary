package com.ledgerdiary.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        DiaryEntryEntity::class,
        DiarySegmentEntity::class,
        DiaryPhotoEntity::class,
        ProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun diaryDao(): DiaryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ledger-diary.db"
                )
                    // 以后加字段时在这里注册 Migration，不要用 fallbackToDestructiveMigration，
                    // 那会把用户数据清空。
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
