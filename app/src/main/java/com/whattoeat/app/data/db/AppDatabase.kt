package com.whattoeat.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 数据库。
 * 改了实体字段 → version + 1，并写 Migration（或用 fallbackToDestructiveMigration 清库重来）。
 * 现在还是 v1，随便改字段都可以。
 */
@Database(
    entities = [
        FoodEntity::class,
        LogEntryEntity::class,
        WeightLogEntity::class,
        UserProfileEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun logDao(): LogDao
    abstract fun weightDao(): WeightDao
    abstract fun profileDao(): ProfileDao

    companion object {
        private const val DB_NAME = "whattoeat.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** 单例：整个 App 只开一个数据库连接 */
        fun create(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    // 开发期用：改了表结构但没写迁移时，直接把库重建（会丢数据）。
                    // 正式版上线前一定要改成真正的 Migration，否则用户数据会没。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
