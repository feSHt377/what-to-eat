package com.whattoeat.app.data.backup

import com.whattoeat.app.data.db.FoodEntity
import com.whattoeat.app.data.db.LogEntryEntity
import com.whattoeat.app.data.db.UserProfileEntity
import com.whattoeat.app.data.db.WeightLogEntity
import com.whattoeat.app.data.db.WaterLogEntity
import com.whattoeat.app.domain.Hydration
import com.whattoeat.app.domain.ActivityLevel
import com.whattoeat.app.domain.Goal
import com.whattoeat.app.domain.MealType
import com.whattoeat.app.domain.Sex
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.ResolverStyle

/**
 * ZIP 中的 data.json。备份格式版本独立于 Room 数据库版本。
 *
 * 后续新增字段时优先提供默认值；这样旧备份仍然可以读，且不需要重建
 * 用户的数据库。真正改 Room 表结构时，仍必须单独提供 Migration。
 */
@Serializable
data class BackupData(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val appVersion: String = "",
    val exportedAt: String = "",
    val profile: UserProfileEntity,
    val foods: List<FoodEntity> = emptyList(),
    val entries: List<LogEntryEntity> = emptyList(),
    val weights: List<WeightLogEntity> = emptyList(),
    val waterLogs: List<WaterLogEntity> = emptyList(),
) {
    fun validate() {
        require(formatVersion == CURRENT_FORMAT_VERSION) {
            "不支持此备份版本，请先升级 WhatToEat"
        }
        require(profile.id == UserProfileEntity.SINGLETON_ID) { "档案数据无效" }
        require(profile.sex in Sex.entries.map { it.name }) { "档案中的性别无效" }
        require(profile.activity in ActivityLevel.entries.map { it.name }) { "档案中的活动量无效" }
        require(profile.goal in Goal.entries.map { it.name }) { "档案中的目标无效" }
        requireFinite(profile.heightCm, "身高")
        requireFinite(profile.weightKg, "体重")
        require(profile.heightCm > 0 && profile.weightKg > 0) { "档案中的身高或体重无效" }
        profile.manualTdeeKcal?.let { requireFinite(it, "每日代谢") }
        profile.customDeltaKcal?.let { requireFinite(it, "每日热量差") }
        require(Hydration.isValidGoal(profile.waterGoalMl)) { "每日饮水目标范围无效" }

        require(foods.size <= MAX_ROWS) { "食物模板数量超过备份上限" }
        require(entries.size <= MAX_ROWS) { "饮食记录数量超过备份上限" }
        require(weights.size <= MAX_ROWS) { "体重记录数量超过备份上限" }
        require(waterLogs.size <= MAX_ROWS) { "饮水记录数量超过备份上限" }
        require(foods.map { it.id }.distinct().size == foods.size) { "食物模板 ID 重复" }
        require(entries.map { it.id }.distinct().size == entries.size) { "饮食记录 ID 重复" }

        foods.forEach { food ->
            require(food.id > 0 && food.name.isNotBlank()) { "存在无效的食物模板" }
            listOf(
                food.kcalPer100g, food.proteinPer100g, food.fatPer100g,
                food.carbPer100g, food.fiberPer100g, food.defaultGrams,
            ).forEach { requireFinite(it, "食物 ${food.name} 的营养数值") }
            require(food.defaultGrams > 0) { "食物 ${food.name} 的默认克数无效" }
        }
        entries.forEach { entry ->
            require(entry.id > 0 && entry.foodName.isNotBlank()) { "存在无效的饮食记录" }
            require(entry.meal in MealType.entries.map { it.name }) { "饮食记录中的餐次无效" }
            validateDate(entry.date, "饮食记录")
            listOf(entry.grams, entry.kcal, entry.proteinG, entry.fatG, entry.carbG, entry.fiberG)
                .forEach { requireFinite(it, "饮食记录 ${entry.foodName} 的营养数值") }
        }
        require(weights.map { it.date }.distinct().size == weights.size) { "体重记录日期重复" }
        weights.forEach {
            validateDate(it.date, "体重记录")
            requireFinite(it.weightKg, "体重")
            require(it.weightKg > 0) { "体重记录数值无效" }
        }
        require(waterLogs.map { it.id }.distinct().size == waterLogs.size) { "饮水记录 ID 重复" }
        waterLogs.forEach {
            require(it.id > 0) { "饮水记录 ID 无效" }
            validateDate(it.date, "饮水记录")
            require(Hydration.isValidAmount(it.amountMl)) { "饮水记录数值无效" }
        }
    }

    private fun requireFinite(value: Double, label: String) {
        require(value.isFinite() && kotlin.math.abs(value) <= MAX_NUMBER) { "${label}无效" }
    }

    private fun validateDate(value: String, label: String) {
        try {
            LocalDate.parse(value, ISO_DATE)
        } catch (_: Exception) {
            throw IllegalArgumentException("${label}中的日期无效")
        }
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        private const val MAX_ROWS = 1_000_000
        private const val MAX_NUMBER = 1_000_000_000.0
        private val ISO_DATE = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
            .withResolverStyle(ResolverStyle.STRICT)
    }
}

/** ZIP 内的小型清单，方便其他工具或未来版本识别备份。 */
@Serializable
data class BackupManifest(
    val app: String = "WhatToEat",
    val formatVersion: Int = BackupData.CURRENT_FORMAT_VERSION,
    val dataFile: String = DATA_FILE,
) {
    companion object {
        const val DATA_FILE = "data.json"
    }
}
