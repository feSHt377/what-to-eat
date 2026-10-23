package com.whattoeat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientsTest {

    @Test
    fun `按克数换算营养值`() {
        // 每 100 克 116 kcal 的米饭，吃 150 克 = 174 kcal
        val rice = Nutrients.per100g(kcal = 116.0, proteinG = 2.6, fatG = 0.3, carbG = 25.9)
        val eaten = rice.scaledTo(150.0)

        assertEquals(174.0, eaten.kcal, 0.01)
        assertEquals(3.9, eaten.proteinG, 0.01)
        assertEquals(38.85, eaten.carbG, 0.01)
    }

    @Test
    fun `多条记录可以加总`() {
        val a = Nutrients(kcal = 300.0, proteinG = 20.0, fatG = 10.0, carbG = 30.0)
        val b = Nutrients(kcal = 200.0, proteinG = 5.0, fatG = 5.0, carbG = 25.0)
        val total = Nutrients.sum(listOf(a, b))

        assertEquals(500.0, total.kcal, 0.01)
        assertEquals(25.0, total.proteinG, 0.01)
        assertEquals(15.0, total.fatG, 0.01)
        assertEquals(55.0, total.carbG, 0.01)
    }

    @Test
    fun `空集合加总为零`() {
        assertEquals(Nutrients.ZERO, Nutrients.sum(emptyList()))
    }

    @Test
    fun `零克数结果为零`() {
        val rice = Nutrients.per100g(116.0, 2.6, 0.3, 25.9)
        assertEquals(0.0, rice.scaledTo(0.0).kcal, 0.001)
    }

    @Test
    fun `膳食纤维也按克数换算`() {
        // 燕麦片每 100 克含 10.6 g 纤维，吃 40 克 = 4.24 g
        val oats = Nutrients.per100g(
            kcal = 377.0,
            proteinG = 15.0,
            fatG = 6.7,
            carbG = 61.0,
            fiberG = 10.6,
        )
        assertEquals(4.24, oats.scaledTo(40.0).fiberG, 0.001)
        // 热量换算不受纤维影响
        assertEquals(150.8, oats.scaledTo(40.0).kcal, 0.01)
    }

    @Test
    fun `膳食纤维可以加总`() {
        val a = Nutrients(kcal = 100.0, fiberG = 2.5)
        val b = Nutrients(kcal = 200.0, fiberG = 3.5)
        assertEquals(6.0, Nutrients.sum(listOf(a, b)).fiberG, 0.001)
    }

    @Test
    fun `只有纤维没有热量也不算空`() {
        // 纯纤维补充剂：0 kcal 但有纤维，不应被当成「空记录」
        assertTrue(Nutrients(fiberG = 5.0).isEmpty().not())
        assertTrue(Nutrients.ZERO.isEmpty())
    }
}
