package com.whattoeat.app.domain

import java.time.LocalDate

/** TDEE 数字的来源。界面要明确告诉用户"这个数是哪来的"，否则用户不敢信。 */
enum class TdeeSource(val label: String, val shortLabel: String) {
    MANUAL("你在设置里手动填写的", "手动设置"),
    ADAPTIVE("根据你的饮食 + 体重记录反推的", "自适应反推"),
    AVERAGE("按性别取的普通成人平均值", "性别平均值"),
}

/**
 * 一次「每日代谢估算」的结果。
 * 把所有可能的来源都保留下来，方便界面展示依据、也方便排查问题。
 */
data class TdeeEstimate(
    val bmr: Double,
    val formulaTdee: Double,
    val manualTdee: Double?,
    val adaptiveTdee: Double?,
    val averageTdee: Double,
    val source: TdeeSource,
    val avgIntake: Double?,
    val trendKgPerWeek: Double?,
    val loggedDays: Int,
    val weightPoints: Int,
) {
    /**
     * 最终采用的值。优先级（高 → 低）：
     *   1. 设置里手动填的代谢值 —— 用户明确指定，最优先
     *   2. 自适应反推 —— 有 ≥5 天饮食 + 2 次体重时可以算，最贴合个人
     *   3. 性别平均值 —— 什么数据都没有时的兜底
     */
    val effectiveTdee: Double get() = manualTdee ?: adaptiveTdee ?: averageTdee

    val usingAdaptive: Boolean get() = source == TdeeSource.ADAPTIVE

    val confidence: Metabolism.Confidence get() = when (source) {
        TdeeSource.MANUAL -> Metabolism.Confidence.HIGH
        TdeeSource.ADAPTIVE -> Metabolism.confidence(minOf(loggedDays, weightPoints))
        TdeeSource.AVERAGE -> Metabolism.Confidence.LOW
    }
}

/**
 * 把「用户档案 + 饮食记录 + 体重记录」换算成 TDEE 估算。
 *
 * 纯函数、不依赖数据库：所有数据由调用方传进来，
 * 既能给 ViewModel 用，也能在单元测试里直接测。
 */
object TdeeEstimator {

    /** 滚动窗口天数：只看最近 28 天（一个月），太老的数据参考价值低 */
    const val WINDOW_DAYS = 28L

    /** 至少要有 5 天饮食记录才敢反推，否则平均摄入太不可靠 */
    const val MIN_LOGGED_DAYS = 5

    fun estimate(
        profile: UserProfile,
        dailyKcal: Map<LocalDate, Double>,
        weightPoints: List<Pair<LocalDate, Double>>,
    ): TdeeEstimate {
        val averageTdee = Metabolism.averageTdee(profile.sex)
        val avgIntake = if (dailyKcal.isEmpty()) null else dailyKcal.values.average()
        val trendKgPerDay = Metabolism.weightTrendKgPerDay(weightPoints)

        val adaptive = if (trendKgPerDay != null &&
            avgIntake != null &&
            dailyKcal.size >= MIN_LOGGED_DAYS
        ) {
            Metabolism.adaptiveTdee(avgIntake, trendKgPerDay)
        } else {
            null
        }

        val manual = profile.manualTdeeKcal?.takeIf { it > 0 }

        val source = when {
            manual != null -> TdeeSource.MANUAL
            adaptive != null -> TdeeSource.ADAPTIVE
            else -> TdeeSource.AVERAGE
        }

        return TdeeEstimate(
            bmr = Metabolism.bmrMifflinStJeor(
                sex = profile.sex,
                weightKg = profile.weightKg,
                heightCm = profile.heightCm,
                age = profile.age,
            ),
            formulaTdee = Metabolism.formulaTdee(profile),
            manualTdee = manual,
            adaptiveTdee = adaptive,
            averageTdee = averageTdee,
            source = source,
            avgIntake = avgIntake,
            trendKgPerWeek = trendKgPerDay?.times(7.0),
            loggedDays = dailyKcal.size,
            weightPoints = weightPoints.size,
        )
    }
}
