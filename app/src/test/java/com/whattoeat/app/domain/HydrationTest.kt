package com.whattoeat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationTest {
    @Test
    fun `默认目标与有效范围`() {
        assertEquals(2000, Hydration.DEFAULT_GOAL_ML)
        assertTrue(Hydration.isValidGoal(250))
        assertTrue(Hydration.isValidGoal(2000))
        assertTrue(Hydration.isValidGoal(10000))
        assertFalse(Hydration.isValidGoal(0))
        assertFalse(Hydration.isValidGoal(10001))
    }

    @Test
    fun `单次饮水量必须为正且不超过上限`() {
        assertTrue(Hydration.isValidAmount(200))
        assertTrue(Hydration.isValidAmount(2000))
        assertFalse(Hydration.isValidAmount(0))
        assertFalse(Hydration.isValidAmount(-250))
        assertFalse(Hydration.isValidAmount(2001))
    }

    @Test
    fun `饮水进度与剩余量`() {
        assertEquals(0.25f, Hydration.progress(500, 2000))
        assertEquals(1f, Hydration.progress(2500, 2000))
        assertEquals(1500, Hydration.remainingMl(500, 2000))
        assertEquals(0, Hydration.remainingMl(2500, 2000))
    }
}
