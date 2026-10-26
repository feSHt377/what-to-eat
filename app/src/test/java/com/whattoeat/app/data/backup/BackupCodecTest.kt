package com.whattoeat.app.data.backup

import com.whattoeat.app.data.db.FoodEntity
import com.whattoeat.app.data.db.LogEntryEntity
import com.whattoeat.app.data.db.UserProfileEntity
import com.whattoeat.app.data.db.WeightLogEntity
import com.whattoeat.app.data.db.WaterLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupCodecTest {
    private val sample = BackupData(
        appVersion = "0.2.0",
        profile = UserProfileEntity(
            sex = "FEMALE", birthYear = 1998, heightCm = 165.0,
            weightKg = 58.0, activity = "MODERATE", goal = "CUT",
            customDeltaKcal = -300.0,
        ),
        foods = listOf(
            FoodEntity(1, "燕麦", "谷物", 380.0, 12.0, 7.0, 60.0, 10.0, 50.0, true),
        ),
        entries = listOf(
            LogEntryEntity(4, "2026-10-24", "BREAKFAST", 1, "燕麦", 50.0, 190.0, 6.0, 3.5, 30.0, 5.0),
        ),
        weights = listOf(WeightLogEntity("2026-10-24", 58.0)),
        waterLogs = listOf(
            WaterLogEntity(1, "2026-10-24", 250, 1_700_000_000_000),
            WaterLogEntity(2, "2026-10-24", 500, 1_700_000_001_000),
        ),
    )

    @Test
    fun `ZIP导出导入完整保留五张表数据`() {
        val output = ByteArrayOutputStream()
        BackupCodec.write(output, sample)
        assertEquals(sample, BackupCodec.read(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun `旧备份缺少纤维和自定义热量差时采用默认值`() {
        val data = """
            {
              "formatVersion": 1,
              "profile": {
                "id": 1, "sex": "MALE", "birthYear": 1995,
                "heightCm": 175.0, "weightKg": 70.0,
                "activity": "LIGHT", "goal": "CUT"
              },
              "foods": [{
                "id": 1, "name": "米饭", "category": "主食",
                "kcalPer100g": 116.0, "proteinPer100g": 2.6,
                "fatPer100g": 0.3, "carbPer100g": 25.9,
                "defaultGrams": 150.0, "builtIn": true
              }],
              "entries": [],
              "weights": []
            }
        """.trimIndent()
        val restored = BackupCodec.read(zipWith(data))
        assertEquals(0.0, restored.foods.single().fiberPer100g, 0.0)
        assertNull(restored.profile.customDeltaKcal)
        assertNull(restored.profile.manualTdeeKcal)
        assertEquals(2000, restored.profile.waterGoalMl)
        assertTrue(restored.waterLogs.isEmpty())
    }

    @Test
    fun `无效饮水记录和重复ID在覆盖前被拒绝`() {
        val invalidAmount = sample.copy(
            waterLogs = listOf(WaterLogEntity(1, "2026-10-24", 0)),
        )
        assertThrows(IllegalArgumentException::class.java) { invalidAmount.validate() }

        val duplicateId = sample.copy(
            waterLogs = listOf(
                WaterLogEntity(1, "2026-10-24", 200),
                WaterLogEntity(1, "2026-10-25", 250),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) { duplicateId.validate() }
    }

    @Test
    fun `损坏ZIP被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.read(ByteArrayInputStream("not a zip".toByteArray()))
        }
    }

    @Test
    fun `不支持未来版本备份`() {
        val data = sample.copy(formatVersion = 999)
        assertThrows(IllegalArgumentException::class.java) { data.validate() }
    }

    @Test
    fun `无效日期在覆盖数据前被拒绝`() {
        val invalid = sample.copy(weights = listOf(WeightLogEntity("2026-02-30", 60.0)))
        assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
    }

    private fun zipWith(dataJson: String): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"app":"WhatToEat","formatVersion":1,"dataFile":"data.json"}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(dataJson.toByteArray())
            zip.closeEntry()
        }
        return ByteArrayInputStream(output.toByteArray())
    }
}
