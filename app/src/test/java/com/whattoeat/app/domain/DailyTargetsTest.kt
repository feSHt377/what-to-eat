package com.whattoeat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 每日目标（热量 / 蛋白 / 脂肪 / 碳水 / 膳食纤维）的单测。
 *
 * 重点覆盖两件事：
 *  1. 自定义热量差要真的覆盖目标预设值
 *  2. 膳食纤维目标随热量缩放
 */
class DailyTargetsTest {

    private val profile = UserProfile(
        sex = Sex.MALE,
        birthYear = 1996,
        heightCm = 175.0,
        weightKg = 75.0,
        activity = ActivityLevel.LIGHT,
        goal = Goal.CUT,
    )

    @Test
    fun `没填自定义差值时用目标预设值`() {
        val targets = DailyTargets.from(profile, tdee = 2000.0)
        // 减脂 -400
        assertEquals(1600.0, targets.kcal, 0.01)
        assertFalse(profile.usingCustomDelta)
        assertEquals(-400.0, profile.effectiveDeltaKcal, 0.01)
    }

    @Test
    fun `自定义差值会覆盖目标预设值`() {
        val custom = profile.copy(customDeltaKcal = -600.0)
        val targets = DailyTargets.from(custom, tdee = 2000.0)
        assertEquals(1400.0, targets.kcal, 0.01)
        assertTrue(custom.usingCustomDelta)
    }

    @Test
    fun `自定义盈余也能生效`() {
        // 维持状态下想增肌 +300
        val custom = profile.copy(goal = Goal.MAINTAIN, customDeltaKcal = 300.0)
        val targets = DailyTargets.from(custom, tdee = 2200.0)
        assertEquals(2500.0, targets.kcal, 0.01)
    }

    @Test
    fun `自定义缺口再大也不低于安全下限`() {
        // 2000 - 1500 = 500，必须兜到 1200
        val extreme = profile.copy(customDeltaKcal = -1500.0)
        val targets = DailyTargets.from(extreme, tdee = 2000.0)
        assertEquals(Metabolism.MIN_SAFE_KCAL, targets.kcal, 0.01)
    }

    @Test
    fun `膳食纤维目标按每1000kcal配14克`() {
        // 2000 kcal → 28 g
        val targets = DailyTargets.from(profile.copy(customDeltaKcal = 0.0), tdee = 2000.0)
        assertEquals(28.0, targets.fiberG, 0.01)

        // 热量降到 1500，纤维目标跟着降到 21 g
        val lower = DailyTargets.from(profile.copy(customDeltaKcal = -500.0), tdee = 2000.0)
        assertEquals(1500.0, lower.kcal, 0.01)
        assertEquals(21.0, lower.fiberG, 0.01)
    }

    @Test
    fun `被安全下限兜住时纤维目标也按兜住后的热量算`() {
        // 2000 - 1000 = 1000，被兜到 1200 kcal → 纤维 16.8 g
        val floored = DailyTargets.from(profile.copy(customDeltaKcal = -1000.0), tdee = 2000.0)
        assertEquals(Metabolism.MIN_SAFE_KCAL, floored.kcal, 0.01)
        assertEquals(16.8, floored.fiberG, 0.01)
    }

    @Test
    fun `蛋白目标只跟体重有关`() {
        val a = DailyTargets.from(profile, tdee = 2000.0)
        val b = DailyTargets.from(profile.copy(customDeltaKcal = -800.0), tdee = 2000.0)
        // 75kg × 1.6 = 120g，改热量差不该影响蛋白
        assertEquals(120.0, a.proteinG, 0.01)
        assertEquals(a.proteinG, b.proteinG, 0.01)
    }
}
