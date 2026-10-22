package com.whattoeat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 领域层单测：不依赖 Android，跑得飞快（./gradlew test）。
 * 这些公式是这个 App 的核心，改算法前先跑一遍这里。
 */
class MetabolismTest {

    @Test
    fun `男性 BMR 与手算结果一致`() {
        // 10*70 + 6.25*175 - 5*25 + 5 = 700 + 1093.75 - 125 + 5 = 1673.75
        val bmr = Metabolism.bmrMifflinStJeor(Sex.MALE, weightKg = 70.0, heightCm = 175.0, age = 25)
        assertEquals(1673.75, bmr, 0.01)
    }

    @Test
    fun `女性 BMR 比同条件男性低 166`() {
        // 男女公式只差常数项：+5 与 -161，相差 166
        val male = Metabolism.bmrMifflinStJeor(Sex.MALE, 60.0, 165.0, 30)
        val female = Metabolism.bmrMifflinStJeor(Sex.FEMALE, 60.0, 165.0, 30)
        assertEquals(166.0, male - female, 0.01)
    }

    @Test
    fun `性别平均值兜底值男女不同`() {
        assertEquals(2250.0, Metabolism.averageTdee(Sex.MALE), 0.01)
        assertEquals(1800.0, Metabolism.averageTdee(Sex.FEMALE), 0.01)
    }

    @Test
    fun `体重完全不变时趋势为 0`() {
        val base = java.time.LocalDate.of(2026, 10, 1)
        val points = (0..6).map { base.plusDays(it.toLong()) to 70.0 }
        assertEquals(0.0, Metabolism.weightTrendKgPerDay(points)!!, 1e-9)
    }

    @Test
    fun `每天掉 0_1 公斤时斜率正确`() {
        val base = java.time.LocalDate.of(2026, 10, 1)
        val points = (0..6).map { base.plusDays(it.toLong()) to (70.0 - 0.1 * it) }
        assertEquals(-0.1, Metabolism.weightTrendKgPerDay(points)!!, 1e-9)
    }

    @Test
    fun `体重抖动不影响趋势的整体方向`() {
        // 水分造成的单日抖动 +-0.5kg，但整体每天 -0.05kg
        val base = java.time.LocalDate.of(2026, 10, 1)
        val noise = listOf(0.4, -0.3, 0.2, -0.4, 0.3, -0.2, 0.1)
        val points = (0..6).map { i ->
            base.plusDays(i.toLong()) to (72.0 - 0.05 * i + noise[i])
        }
        val slope = Metabolism.weightTrendKgPerDay(points)!!
        // 允许被噪声轻微带偏，但必须仍然是明显下降
        org.junit.Assert.assertTrue("期望仍为下降趋势，实际 $slope", slope < -0.02)
    }

    @Test
    fun `少于两个体重点无法算趋势`() {
        assertNull(Metabolism.weightTrendKgPerDay(emptyList()))
        assertNull(Metabolism.weightTrendKgPerDay(listOf(java.time.LocalDate.now() to 70.0)))
    }

    @Test
    fun `体重下降时反推出的消耗大于摄入`() {
        // 日均摄入 2000，每天掉 0.05kg → TDEE = 2000 + 385 = 2385
        val tdee = Metabolism.adaptiveTdee(avgDailyIntakeKcal = 2000.0, trendKgPerDay = -0.05)
        assertEquals(2385.0, tdee, 0.01)
    }

    @Test
    fun `体重上升时反推出的消耗小于摄入`() {
        val tdee = Metabolism.adaptiveTdee(avgDailyIntakeKcal = 3000.0, trendKgPerDay = 0.05)
        assertEquals(2615.0, tdee, 0.01)
    }

    @Test
    fun `可信度随天数提升`() {
        assertEquals(Metabolism.Confidence.LOW, Metabolism.confidence(3))
        assertEquals(Metabolism.Confidence.MEDIUM, Metabolism.confidence(10))
        assertEquals(Metabolism.Confidence.HIGH, Metabolism.confidence(28))
    }
}
