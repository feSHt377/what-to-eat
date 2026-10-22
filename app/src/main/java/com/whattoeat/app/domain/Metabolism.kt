package com.whattoeat.app.domain

import java.time.LocalDate
import kotlin.math.abs

/**
 * 全部是**纯函数**，不碰数据库、不碰 Android API。
 * 好处：可以直接在 app/src/test 里写 JUnit 单测，跑得飞快（不用模拟器）。
 *
 * 这个类是本 App 的核心差异点：用「实际吃了多少 + 体重怎么变」反推真实代谢，
 * 而不是只用公式估一个数字。
 */
object Metabolism {

    /** 1 公斤体重变化 ≈ 多少 kcal（脂肪组织近似 7700 kcal/kg） */
    const val KCAL_PER_KG = 7700.0

    /** 低于这个摄入量就不健康了，作为硬下限兜底 */
    const val MIN_SAFE_KCAL = 1200.0

    /**
     * 没有个人数据时的「普通成人」日均能量需要量（轻体力活动）。
     * 参考《中国居民膳食营养素参考摄入量》的成人中等/轻体力水平，作为兜底默认值。
     * 男女分开，因为差异很大（相差 400+ kcal），不分性别会离谱。
     */
    const val AVERAGE_TDEE_MALE = 2250.0
    const val AVERAGE_TDEE_FEMALE = 1800.0

    fun averageTdee(sex: Sex): Double =
        if (sex == Sex.MALE) AVERAGE_TDEE_MALE else AVERAGE_TDEE_FEMALE

    /**
     * Mifflin-St Jeor 基础代谢（BMR）：只跟性别、体重、身高、年龄有关。
     * 公式：男 = 10×kg + 6.25×cm − 5×age + 5
     *       女 = 10×kg + 6.25×cm − 5×age − 161
     */
    fun bmrMifflinStJeor(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return if (sex == Sex.MALE) base + 5.0 else base - 161.0
    }

    /**
     * 公式法 TDEE = BMR × 活动系数。
     * 注意：这只是**起点**，个体误差可达 ±20~30%，所以本 App 会随着记录变多
     * 逐渐改用 [adaptiveTdee] 的结果。
     */
    fun formulaTdee(profile: UserProfile): Double =
        bmrMifflinStJeor(profile.sex, profile.weightKg, profile.heightCm, profile.age) *
            profile.activity.factor

    /**
     * 体重趋势斜率（kg / 天）。
     *
     * 用最小二乘法对 (第几天, 体重) 做一元线性回归。
     * 为什么不用「最后一次减第一次」？因为单日体重会被水分、盐分、排便影响，
     * 直接相减噪声极大；回归能把这些抖动平均掉。
     *
     * @param points 体重记录（日期, 公斤），顺序无所谓
     * @return 每天增减多少公斤；数据点少于 2 个返回 null
     */
    fun weightTrendKgPerDay(points: List<Pair<LocalDate, Double>>): Double? {
        if (points.size < 2) return null
        val sorted = points.sortedBy { it.first }
        val origin = sorted.first().first.toEpochDay()
        val xs = sorted.map { (it.first.toEpochDay() - origin).toDouble() }
        val ys = sorted.map { it.second }

        val n = xs.size.toDouble()
        val sumX = xs.sum()
        val sumY = ys.sum()
        val sumXY = xs.indices.sumOf { xs[it] * ys[it] }
        val sumXX = xs.sumOf { it * it }

        val denominator = n * sumXX - sumX * sumX
        if (abs(denominator) < 1e-9) return null

        return (n * sumXY - sumX * sumY) / denominator
    }

    /**
     * 自适应 TDEE（核心算法）。
     *
     * 能量守恒：摄入 − 消耗 = 体重变化对应的能量
     *   ⇒ 消耗 = 平均摄入 − (每天体重变化 × 7700)
     *
     * 举例：平均每天吃 2000 kcal，体重每天掉 0.05 kg
     *   ⇒ TDEE = 2000 − (−0.05 × 7700) = 2000 + 385 = 2385 kcal
     * （体重在掉，说明消耗大于摄入，所以真实消耗比吃进去的多）
     */
    fun adaptiveTdee(avgDailyIntakeKcal: Double, trendKgPerDay: Double): Double =
        avgDailyIntakeKcal - trendKgPerDay * KCAL_PER_KG

    /**
     * 数据天数 → 可信度。纯展示用途，给用户一个「现在这个数字能不能信」的提示。
     */
    fun confidence(days: Int): Confidence = when {
        days >= 21 -> Confidence.HIGH
        days >= 10 -> Confidence.MEDIUM
        else -> Confidence.LOW
    }

    enum class Confidence(val label: String) {
        LOW("数据不足"),
        MEDIUM("较可信"),
        HIGH("可信"),
    }
}

/**
 * 每日目标：总热量 + 三大营养素配额。
 * 分配逻辑（可以按自己需要改）：
 *   - 热量 = TDEE + 目标差值（减脂 -400 / 维持 0 / 增肌 +250），且有安全下限
 *   - 蛋白 = 体重 × proteinPerKg
 *   - 脂肪 = 总热量的 25%
 *   - 碳水 = 剩下的热量
 */
data class DailyTargets(
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbG: Double,
) {
    companion object {
        fun from(profile: UserProfile, tdee: Double): DailyTargets {
            val kcal = (tdee + profile.goal.dailyDeltaKcal).coerceAtLeast(Metabolism.MIN_SAFE_KCAL)
            val proteinG = profile.weightKg * profile.proteinPerKg
            val fatG = kcal * 0.25 / 9.0
            val carbG = ((kcal - proteinG * 4.0 - fatG * 9.0) / 4.0).coerceAtLeast(0.0)
            return DailyTargets(kcal = kcal, proteinG = proteinG, fatG = fatG, carbG = carbG)
        }
    }
}
