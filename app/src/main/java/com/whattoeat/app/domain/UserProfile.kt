package com.whattoeat.app.domain

import java.time.LocalDate

enum class Sex(val label: String) {
    MALE("男"),
    FEMALE("女"),
}

/**
 * 活动系数：国际通用的 Harris-Benedict / Mifflin 系数，
 * 用力把「基础代谢」放大成「全天总消耗」。
 */
enum class ActivityLevel(val label: String, val factor: Double) {
    SEDENTARY("久坐（几乎不运动）", 1.2),
    LIGHT("轻度活动（每周 1-3 次）", 1.375),
    MODERATE("中度活动（每周 3-5 次）", 1.55),
    ACTIVE("高强度（每周 6-7 次）", 1.725),
    VERY_ACTIVE("极高（体力工作 / 一天两练）", 1.9),
}

/** 目标：用一个每日热量差值表达，比让用户自己填数字友好 */
enum class Goal(val label: String, val dailyDeltaKcal: Double) {
    CUT("减脂", -400.0),
    MAINTAIN("维持", 0.0),
    BULK("增肌", 250.0),
}

/**
 * 用户档案。目前是单用户单条记录（数据库里 id 恒为 1），不做多用户。
 */
data class UserProfile(
    val sex: Sex = Sex.MALE,
    val birthYear: Int = LocalDate.now().year - 25,
    val heightCm: Double = 172.0,
    val weightKg: Double = 70.0,
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val goal: Goal = Goal.CUT,
    /** 每公斤体重吃多少克蛋白，健身人群常用 1.6-2.2 */
    val proteinPerKg: Double = 1.6,
    /**
     * 用户手动填写的每日代谢（TDEE）。
     * null = 没设置，此时由 [TdeeEstimator] 自动决定用「自适应反推」还是「性别平均值」。
     * 只要这里填了数字，就无条件优先使用它（用户说了算）。
     */
    val manualTdeeKcal: Double? = null,
) {
    val age: Int get() = (LocalDate.now().year - birthYear).coerceAtLeast(1)
}
