package com.whattoeat.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 食物库（每 100 克的营养值）。
 * 内置食物 [builtIn] = true；用户手动新增的为 false。
 */
@Serializable
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbPer100g: Double,
    /** 膳食纤维（每 100 克）。没有数据时填 0，不要瞎猜 */
    val fiberPer100g: Double = 0.0,
    /** 默认一份多少克，添加快捷一点 */
    val defaultGrams: Double = 100.0,
    val builtIn: Boolean = true,
)

/**
 * 一条饮食记录。
 *
 * ⚠️ 重要设计：这里**冗余存了食物名和营养素**（快照），而不是只存 foodId。
 * 原因：以后你修改/删除食物库里的数据，历史记录不应该跟着变。
 * 这是"记账"类 App 的通用做法（会计上叫快照）。
 */
@Serializable
@Entity(tableName = "log_entries", indices = [Index("date"), Index("foodId")])
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO 日期字符串：2026-10-22。用字符串存日期，排序和范围查询都直接可用 */
    val date: String,
    /** MealType.name，如 LUNCH */
    val meal: String,
    val foodId: Long,
    val foodName: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbG: Double,
    /** 膳食纤维（快照） */
    val fiberG: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 体重记录：一天一条（同一天再记会覆盖）。没记录的日子就没有行，就是「空」 */
@Serializable
@Entity(tableName = "weight_logs")
data class WeightLogEntity(
    @PrimaryKey val date: String,
    val weightKg: Double,
)

/** 用户档案：单条记录，主键恒为 1 */
@Serializable
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val sex: String,
    val birthYear: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: String,
    val goal: String,
    /** 设置页手动填写的每日代谢；null 表示"自动" */
    val manualTdeeKcal: Double? = null,
    /** 自定义每日热量差；null 表示"用目标预设值" */
    val customDeltaKcal: Double? = null,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
