package com.whattoeat.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    /**
     * 按名字模糊搜索。`:q` 为空字符串时 `LIKE '%%'` 会返回全部。
     * 返回 Flow：数据库一变，UI 自动刷新（这就是 Room + Flow 的威力）。
     */
    @Query("SELECT * FROM foods WHERE name LIKE '%' || :q || '%' ORDER BY builtIn DESC, id ASC LIMIT 300")
    fun search(q: String): Flow<List<FoodEntity>>

    /** 设置页用：全部模板，按分类排序 */
    @Query("SELECT * FROM foods ORDER BY builtIn DESC, category ASC, name ASC")
    fun all(): Flow<List<FoodEntity>>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(items: List<FoodEntity>)

    @Insert
    suspend fun insert(item: FoodEntity): Long

    @Update
    suspend fun update(item: FoodEntity)

    @Delete
    suspend fun delete(item: FoodEntity)
}

@Dao
interface LogDao {

    @Query("SELECT * FROM log_entries WHERE date = :date ORDER BY createdAt ASC")
    fun byDate(date: String): Flow<List<LogEntryEntity>>

    /** 区间查询（含首尾），用于汇总页和趋势计算 */
    @Query("SELECT * FROM log_entries WHERE date BETWEEN :fromDate AND :toDate ORDER BY date ASC")
    fun between(fromDate: String, toDate: String): Flow<List<LogEntryEntity>>

    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface WeightDao {

    @Query("SELECT * FROM weight_logs ORDER BY date ASC")
    fun all(): Flow<List<WeightLogEntity>>

    /** 更新成最近的一条体重，用于同步档案里的体重 */
    @Query("SELECT * FROM weight_logs ORDER BY date DESC LIMIT 1")
    suspend fun latest(): WeightLogEntity?

    /** 同一天重复记录直接覆盖 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: WeightLogEntity)

    @Query("DELETE FROM weight_logs WHERE date = :date")
    suspend fun deleteByDate(date: String)
}

@Dao
interface ProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserProfileEntity)
}
