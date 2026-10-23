package com.whattoeat.app.data

import com.whattoeat.app.data.db.AppDatabase
import com.whattoeat.app.data.db.FoodEntity
import com.whattoeat.app.data.db.LogEntryEntity
import com.whattoeat.app.data.db.UserProfileEntity
import com.whattoeat.app.data.db.WeightLogEntity
import com.whattoeat.app.data.seed.FoodSeed
import com.whattoeat.app.domain.ActivityLevel
import com.whattoeat.app.domain.Goal
import com.whattoeat.app.domain.MealType
import com.whattoeat.app.domain.Nutrients
import com.whattoeat.app.domain.Sex
import com.whattoeat.app.domain.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.LinkedHashMap

/**
 * 仓库层（Repository）：UI / ViewModel 只跟这里打交道，不直接碰 Room。
 *
 * 好处：
 *  - 上层不需要知道数据存在哪（现在是 Room，以后可能是云端）
 *  - "克数 × 每100克营养 = 实际营养" 这类换算只写一遍
 *
 * ## 线程模型（改动前请先读这段）
 *
 * Room 的 `Flow` 查询是**冷流**：每调用一次 DAO 方法，就会真的去查一次库。
 * 原来三个页面各自 `repository.profile()`、`repository.weights()`、
 * `repository.entriesBetween(...)`，实际会打出 9 次重复查询，而且每切一次页面就重来一遍。
 *
 * 现在这些查询在这里被 **shareIn 共享**成一条流：
 *  - 三个页面读同一条流 → 只查一次库
 *  - 换页时因为已经有订阅者，**不会再触发新查询**，所以切 Tab 是瞬时的
 *  - 写入后 Room 主动 invalidate → 共享流自动把新值推给所有页面
 *
 * `scope` 是 App 级别的作用域（见 AppContainer），保证共享流比任何页面活得都久。
 */
class DietRepository(
    private val db: AppDatabase,
    private val scope: CoroutineScope,
) {

    // ==================== 共享查询流 ====================

    /** 个人档案：三个页面都要读，共享成一条 */
    val profile: Flow<UserProfile> = db.profileDao().observe()
        .map { it?.toDomain() ?: UserProfile() }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** 体重记录：三个页面都要读，共享成一条 */
    val weights: Flow<List<WeightLogEntity>> = db.weightDao().all()
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** 全部食物模板（设置页用） */
    val allFoods: Flow<List<FoodEntity>> = db.foodDao().all()
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * 区间查询（汇总页 / 今日页都要查「最近 28 天」）。
     *
     * 同样的 (from, to) 会复用同一条共享流 —— 今日页和汇总页查的都是
     * 「今天往前 28 天」，所以它们共用一次查询，而不是各查一次。
     *
     * 用 LRU 限制最多缓存 32 个窗口，防止用户天天翻日期把内存吃光。
     */
    private val windowCache =
        object : LinkedHashMap<Pair<LocalDate, LocalDate>, Flow<List<LogEntryEntity>>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Pair<LocalDate, LocalDate>, Flow<List<LogEntryEntity>>>,
            ): Boolean = size > 32
        }

    fun entriesBetween(from: LocalDate, to: LocalDate): Flow<List<LogEntryEntity>> =
        synchronized(windowCache) {
            windowCache.getOrPut(from to to) {
                db.logDao().between(from.toString(), to.toString())
                    .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
            }
        }

    /** 某一天的记录（今日页用） */
    fun entriesOf(date: LocalDate): Flow<List<LogEntryEntity>> =
        db.logDao().byDate(date.toString())
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // ==================== 搜索 ====================

    /**
     * 搜索食物模板。
     *
     * 用「查询词作为流」的方式，是为了能 **防抖**：
     * 之前每敲一个字就重建一个流、打一次库；现在输入停下来 150ms 才查，
     * 空查询（刚打开页面）立刻出结果，不让用户干等。
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun searchFoods(queryFlow: Flow<String>): Flow<List<FoodEntity>> =
        queryFlow
            .distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0L else 150L }
            .flatMapLatest { db.foodDao().search(it) }
            .flowOn(Dispatchers.IO)

    // ==================== 写饮食记录 ====================

    /**
     * 用「模板食物」添加一条记录：
     * 传入吃了多少克，内部换算成绝对营养值后写入快照。
     */
    suspend fun addEntry(food: FoodEntity, grams: Double, meal: MealType, date: LocalDate) {
        val nutrients = Nutrients
            .per100g(
                kcal = food.kcalPer100g,
                proteinG = food.proteinPer100g,
                fatG = food.fatPer100g,
                carbG = food.carbPer100g,
                fiberG = food.fiberPer100g,
            )
            .scaledTo(grams)

        withContext(Dispatchers.IO) {
            db.logDao().insert(
                LogEntryEntity(
                    date = date.toString(),
                    meal = meal.name,
                    foodId = food.id,
                    foodName = food.name,
                    grams = grams,
                    kcal = nutrients.kcal,
                    proteinG = nutrients.proteinG,
                    fatG = nutrients.fatG,
                    carbG = nutrients.carbG,
                    fiberG = nutrients.fiberG,
                )
            )
        }
    }

    /**
     * 添加一条「自定义」记录：用户直接写名称和热量，不必先建模板。
     *
     * 说明：foodId 存 0，表示不是模板食物。因为记录本身存了名称和营养快照，
     * 所以以后模板改了/删了，历史记录也不受影响。
     */
    suspend fun addCustomEntry(
        date: LocalDate,
        meal: MealType,
        name: String,
        nutrients: Nutrients,
        grams: Double = 0.0,
    ) {
        withContext(Dispatchers.IO) {
            db.logDao().insert(
                LogEntryEntity(
                    date = date.toString(),
                    meal = meal.name,
                    foodId = 0L,
                    foodName = name.trim(),
                    grams = grams,
                    kcal = nutrients.kcal,
                    proteinG = nutrients.proteinG,
                    fatG = nutrients.fatG,
                    carbG = nutrients.carbG,
                    fiberG = nutrients.fiberG,
                )
            )
        }
    }

    suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        db.logDao().deleteById(id)
    }

    // ==================== 写食物模板 ====================

    /**
     * 新增或更新模板。
     * id == 0 视为新增（Room 的 autoGenerate 会分配新 id），否则按 id 更新。
     */
    suspend fun saveFood(food: FoodEntity) {
        withContext(Dispatchers.IO) {
            if (food.id == 0L) {
                db.foodDao().insert(food.copy(builtIn = false))
            } else {
                db.foodDao().update(food)
            }
        }
    }

    suspend fun deleteFood(food: FoodEntity) = withContext(Dispatchers.IO) {
        db.foodDao().delete(food)
    }

    // ==================== 其它 ====================

    /**
     * 记录某天的体重。
     *
     * 除了写进体重历史，如果这条正好是**最新**的体重，
     * 还会顺手把档案里的体重也更新掉 —— 否则 BMR 和蛋白目标会一直用旧体重算，
     * 用户会觉得「我明明记了体重，怎么数字没变」。
     */
    suspend fun saveWeight(date: LocalDate, weightKg: Double) = withContext(Dispatchers.IO) {
        db.weightDao().upsert(WeightLogEntity(date.toString(), weightKg))

        val latest = db.weightDao().latest()
        if (latest != null && latest.date == date.toString()) {
            db.profileDao().get()?.let { entity ->
                db.profileDao().upsert(entity.copy(weightKg = weightKg))
            }
        }
    }

    /** 删除某天的体重记录 */
    suspend fun deleteWeight(date: LocalDate) = withContext(Dispatchers.IO) {
        db.weightDao().deleteByDate(date.toString())
    }

    suspend fun saveProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        db.profileDao().upsert(profile.toEntity())
    }

    /** 首次启动时写入内置食物库与默认档案（幂等：已有数据就跳过） */
    suspend fun ensureSeedData() = withContext(Dispatchers.IO) {
        if (db.foodDao().count() == 0) {
            db.foodDao().insertAll(FoodSeed.foods)
        }
        if (db.profileDao().get() == null) {
            db.profileDao().upsert(UserProfile().toEntity())
        }
    }
}

// ==================== 实体 ↔ 领域模型 的转换 ====================

/** 一条记录的营养素，转成领域模型，方便加总 */
fun LogEntryEntity.toNutrients(): Nutrients = Nutrients(
    kcal = kcal,
    proteinG = proteinG,
    fatG = fatG,
    carbG = carbG,
    fiberG = fiberG,
)

/** 模板食物的「每 100 克」营养值 */
fun FoodEntity.per100gNutrients(): Nutrients = Nutrients(
    kcal = kcalPer100g,
    proteinG = proteinPer100g,
    fatG = fatPer100g,
    carbG = carbPer100g,
    fiberG = fiberPer100g,
)

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    sex = enumOrDefault(sex, Sex.MALE),
    birthYear = birthYear,
    heightCm = heightCm,
    weightKg = weightKg,
    activity = enumOrDefault(activity, ActivityLevel.LIGHT),
    goal = enumOrDefault(goal, Goal.MAINTAIN),
    manualTdeeKcal = manualTdeeKcal,
    customDeltaKcal = customDeltaKcal,
)

fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    sex = sex.name,
    birthYear = birthYear,
    heightCm = heightCm,
    weightKg = weightKg,
    activity = activity.name,
    goal = goal.name,
    manualTdeeKcal = manualTdeeKcal,
    customDeltaKcal = customDeltaKcal,
)

/** 字符串解析失败时回退到默认值，避免数据库脏数据把 App 搞崩 */
private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
