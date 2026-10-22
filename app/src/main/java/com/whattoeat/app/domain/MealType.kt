package com.whattoeat.app.domain

import java.time.LocalTime

/**
 * 餐次。数据库里存的是 enum 的 name（字符串），
 * 这样以后加减餐次（比如「夜宵」「加餐」）不用改表结构。
 */
enum class MealType(val label: String) {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐"),
    ;

    companion object {
        /** 根据当前时间猜一个默认餐次，减少用户点选次数 */
        fun suggestFor(time: LocalTime = LocalTime.now()): MealType = when (time.hour) {
            in 4..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 17..20 -> DINNER
            else -> SNACK
        }

        fun fromName(name: String): MealType =
            entries.firstOrNull { it.name == name } ?: SNACK
    }
}
