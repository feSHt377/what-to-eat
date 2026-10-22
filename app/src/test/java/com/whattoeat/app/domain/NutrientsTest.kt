package com.whattoeat.app.domain

import org.junit.Assert.assertEquals
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
}
