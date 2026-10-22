package com.whattoeat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * TDEE 估算优先级的单测：手动 > 自适应 > 性别平均值。
 */
class TdeeEstimatorTest {

    private val profile = UserProfile(
        sex = Sex.MALE,
        birthYear = LocalDate.now().year - 30,
        heightCm = 175.0,
        weightKg = 75.0,
        activity = ActivityLevel.LIGHT,
        goal = Goal.MAINTAIN,
    )

    private val today = LocalDate.of(2026, 10, 22)

    /** 造 6 天有记录、每天固定热量的数据 */
    private fun days(kcal: Double, count: Int = 6): Map<LocalDate, Double> =
        (0 until count).associate { today.minusDays(it.toLong()) to kcal }

    /** 造 7 次体重，每天掉 0.05kg */
    private fun losingWeight(): List<Pair<LocalDate, Double>> =
        (0..6).map { today.minusDays(it.toLong()) to (75.0 + 0.05 * it) }

    @Test
    fun `没有任何数据时用性别平均值`() {
        val estimate = TdeeEstimator.estimate(profile, emptyMap(), emptyList())
        assertEquals(TdeeSource.AVERAGE, estimate.source)
        assertEquals(Metabolism.AVERAGE_TDEE_MALE, estimate.effectiveTdee, 0.01)
        assertNull(estimate.adaptiveTdee)
    }

    @Test
    fun `数据足够时用自适应反推`() {
        val estimate = TdeeEstimator.estimate(profile, days(2000.0), losingWeight())
        assertEquals(TdeeSource.ADAPTIVE, estimate.source)
        // 2000 - (-0.05 * 7700) = 2385
        assertEquals(2385.0, estimate.effectiveTdee, 0.5)
    }

    @Test
    fun `手动设置的值永远优先`() {
        val manual = profile.copy(manualTdeeKcal = 2600.0)
        val estimate = TdeeEstimator.estimate(manual, days(2000.0), losingWeight())
        assertEquals(TdeeSource.MANUAL, estimate.source)
        assertEquals(2600.0, estimate.effectiveTdee, 0.01)
        // 自适应值仍然算出来放在一边，界面上可以展示依据
        assertEquals(2385.0, estimate.adaptiveTdee!!, 0.5)
    }

    @Test
    fun `记录天数不足时不启用自适应`() {
        val estimate = TdeeEstimator.estimate(profile, days(2000.0, count = 3), losingWeight())
        assertEquals(TdeeSource.AVERAGE, estimate.source)
        assertNull(estimate.adaptiveTdee)
    }

    @Test
    fun `只有一次体重时无法反推`() {
        val onePoint = listOf(today to 75.0)
        val estimate = TdeeEstimator.estimate(profile, days(2000.0), onePoint)
        assertEquals(TdeeSource.AVERAGE, estimate.source)
    }

    @Test
    fun `手填的 0 或负数视为未设置`() {
        val estimate = TdeeEstimator.estimate(profile.copy(manualTdeeKcal = 0.0), emptyMap(), emptyList())
        assertEquals(TdeeSource.AVERAGE, estimate.source)
    }

    @Test
    fun `目标热量按目标差值调整且不低于安全下限`() {
        val targets = DailyTargets.from(profile.copy(goal = Goal.CUT), tdee = 2000.0)
        assertEquals(1600.0, targets.kcal, 0.01)

        // 极低 TDEE + 减脂 也不该给出低于 1200 的目标
        val floor = DailyTargets.from(profile.copy(goal = Goal.CUT), tdee = 1000.0)
        assertEquals(Metabolism.MIN_SAFE_KCAL, floor.kcal, 0.01)
        assertTrue(floor.kcal >= Metabolism.MIN_SAFE_KCAL)
    }
}
