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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * 仓库层（Repository）：UI / ViewModel 只跟这里打交道，不直接碰 Room。
 *
 * 好处：
 *  - 上层不需要知道数据存在哪（现在是 Room，以后可能是云端）
 *  - "克数 × 每100克营养 = 实际营养" 这类换算只写一遍
 */
class DietRepository(private val db: AppDatabase) {

    // ==================== 查询 ====================

    fun searchFoods(query: String): Flow<List<FoodEntity>> = db.foodDao().search(query)

    /** 设置页：全部食物模板 */
    fun allFoods(): Flow<List<FoodEntity>> = db.foodDao().all()

    fun entriesOf(date: LocalDate): Flow<List<LogEntryEntity>> = db.logDao().byDate(date.toString())

    fun entriesBetween(from: LocalDate, to: LocalDate): Flow<List<LogEntryEntity>> =
        db.logDao().between(from.toString(), to.toString())

    fun weights(): Flow<List<WeightLogEntity>> = db.weightDao().all()

    fun profile(): Flow<UserProfile> = db.profileDao().observe().map { it?.toDomain() ?: UserProfile() }

    // ==================== 写饮食记录 ====================

    /**
     * 用「模板食物」添加一条记录：
     * 传入吃了多少克，内部换算成绝对营养值后写入快照。
     */
    suspend fun addEntry(food: FoodEntity, grams: Double, meal: MealType, date: LocalDate) {
        val nutrients = Nutrients
            .per100g(food.kcalPer100g, food.proteinPer100g, food.fatPer100g, food.carbPer100g)
            .scaledTo(grams)

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
            )
        )
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
            )
        )
    }

    suspend fun deleteEntry(id: Long) = db.logDao().deleteById(id)

    // ==================== 写食物模板 ====================

    /**
     * 新增或更新模板。
     * id == 0 视为新增（Room 的 autoGenerate 会分配新 id），否则按 id 更新。
     */
    suspend fun saveFood(food: FoodEntity) {
        if (food.id == 0L) {
            db.foodDao().insert(food.copy(builtIn = false))
        } else {
            db.foodDao().update(food)
        }
    }

    suspend fun deleteFood(food: FoodEntity) = db.foodDao().delete(food)

    // ==================== 其它 ====================

    suspend fun saveWeight(date: LocalDate, weightKg: Double) =
        db.weightDao().upsert(WeightLogEntity(date.toString(), weightKg))

    suspend fun saveProfile(profile: UserProfile) = db.profileDao().upsert(profile.toEntity())

    /** 首次启动时写入内置食物库与默认档案（幂等：已有数据就跳过） */
    suspend fun ensureSeedData() {
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
)

/** 模板食物的「每 100 克」营养值 */
fun FoodEntity.per100gNutrients(): Nutrients = Nutrients(
    kcal = kcalPer100g,
    proteinG = proteinPer100g,
    fatG = fatPer100g,
    carbG = carbPer100g,
)

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    sex = enumOrDefault(sex, Sex.MALE),
    birthYear = birthYear,
    heightCm = heightCm,
    weightKg = weightKg,
    activity = enumOrDefault(activity, ActivityLevel.LIGHT),
    goal = enumOrDefault(goal, Goal.MAINTAIN),
    manualTdeeKcal = manualTdeeKcal,
)

fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    sex = sex.name,
    birthYear = birthYear,
    heightCm = heightCm,
    weightKg = weightKg,
    activity = activity.name,
    goal = goal.name,
    manualTdeeKcal = manualTdeeKcal,
)

/** 字符串解析失败时回退到默认值，避免数据库脏数据把 App 搞崩 */
private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
