package com.ledgerdiary.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        DiaryEntryEntity::class,
        DiarySegmentEntity::class,
        DiaryPhotoEntity::class,
        ProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun diaryDao(): DiaryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        /**
         * v1 → v2：日记从「一天一篇」改成「一天可以多篇」。
         * 唯一的 schema 变化是 diary_entries.date 上的唯一索引变成普通索引，
         * 所以不重建表（重建表要整表搬家，风险更高，也更容易在低端机上出问题）。
         * 老数据一条都不动，索引名保持 index_diary_entries_date 不变，
         * Room 启动时的 schema 校验才能对上。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_diary_entries_date")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diary_entries_date ON diary_entries(date)")
            }
        }

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
