package com.whattoeat.app.domain

/** 纯 Kotlin 的饮水进度计算，避免把业务判断散落在 UI 中。 */
object Hydration {
    const val DEFAULT_GOAL_ML = 2000
    const val MAX_ENTRY_ML = 2000
    const val MAX_GOAL_ML = 10000

    fun isValidAmount(amountMl: Int): Boolean = amountMl in 1..MAX_ENTRY_ML

    fun isValidGoal(goalMl: Int): Boolean = goalMl in 250..MAX_GOAL_ML

    fun progress(totalMl: Int, goalMl: Int): Float =
        if (goalMl > 0) (totalMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f

    fun remainingMl(totalMl: Int, goalMl: Int): Int = (goalMl - totalMl).coerceAtLeast(0)
}
