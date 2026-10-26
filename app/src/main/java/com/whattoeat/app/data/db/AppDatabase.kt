package com.whattoeat.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.whattoeat.app.data.seed.FoodSeed

/**
 * Room 数据库。
 *
 * 版本历史：
 *  - v1：初始版本
 *  - v2：营养素新增「膳食纤维」（foods.fiberPer100g / log_entries.fiberG），
 *        档案新增「自定义每日热量差」（user_profile.customDeltaKcal）
 *  - v3：新增按日饮水记录和每日饮水目标。
 *
 * ⚠️ 改实体字段 → version + 1，并在 [ALL_MIGRATIONS] 里补一条 Migration。
 * 下面的 v1→v2 就是范例：老用户的饮食记录不会丢，旧记录纤维先按 0 计。
 */
@Database(
    entities = [
        FoodEntity::class,
        LogEntryEntity::class,
        WeightLogEntity::class,
        WaterLogEntity::class,
        UserProfileEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun logDao(): LogDao
    abstract fun weightDao(): WeightDao
    abstract fun waterDao(): WaterDao
    abstract fun profileDao(): ProfileDao

    companion object {
        private const val DB_NAME = "whattoeat.db"

        /**
         * v1 → v2：加膳食纤维 + 自定义热量差。
         *
         * 三张表都只是「加一列」，用 ALTER TABLE 就能保住原有数据，
         * 不需要重建表，也就不需要迁移期间的临时表那一套。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 非空列加默认值 0：老记录没有纤维数据，先按 0 算（不瞎猜）
                db.execSQL("ALTER TABLE foods ADD COLUMN fiberPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE log_entries ADD COLUMN fiberG REAL NOT NULL DEFAULT 0")
                // 可空列：null 表示「用目标预设值」，正好是老数据该有的语义
                db.execSQL("ALTER TABLE user_profile ADD COLUMN customDeltaKcal REAL")

                // 内置食物库的纤维值回填一遍，否则老用户看到的全是 0。
                // 只动 builtIn = 1 的行，用户自己建的模板不覆盖。
                FoodSeed.foods.forEach { food ->
                    if (food.fiberPer100g > 0.0) {
                        db.execSQL(
                            "UPDATE foods SET fiberPer100g = ? WHERE name = ? AND builtIn = 1",
                            arrayOf<Any>(food.fiberPer100g, food.name),
                        )
                    }
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS water_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "date TEXT NOT NULL, amountMl INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_water_logs_date ON water_logs(date)")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN waterGoalMl INTEGER NOT NULL DEFAULT 2000")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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
                    .addMigrations(*ALL_MIGRATIONS)
                    // 兜底：万一以后某次改表忘了写 Migration，宁可清库也不要让 App 崩。
                    // 正式版上线前应该把它去掉，改成「缺 Migration 就让构建失败」。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
