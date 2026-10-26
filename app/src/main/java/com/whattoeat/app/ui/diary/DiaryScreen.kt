package com.whattoeat.app.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.whattoeat.app.domain.MealType
import com.whattoeat.app.domain.TdeeEstimator
import com.whattoeat.app.ui.common.CalorieRing
import com.whattoeat.app.ui.common.MacroBar
import com.whattoeat.app.ui.common.SectionCard
import com.whattoeat.app.ui.common.fmt0
import com.whattoeat.app.ui.common.fmt1
import com.whattoeat.app.ui.theme.NutrientColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 「今日」页面。这是一个**无状态** Composable：
 * 数据从参数进来，动作通过回调出去。这样可以在 Preview 里随便造数据看效果。
 */
@Composable
fun DiaryScreen(
    state: DiaryUiState,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onAddToMeal: (MealType) -> Unit,
    onSaveWeight: (Double) -> Unit,
    onClearWeight: () -> Unit,
    onAddWater: (Int) -> Unit,
    onUndoWater: () -> Unit,
    onClearWater: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA) }
    val isToday = state.date == LocalDate.now()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 日期切换 ----------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onPrevDay) { Text("‹ 前一天") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.date.format(dateFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!isToday) {
                        TextButton(onClick = onToday) { Text("回到今天") }
                    }
                }
                TextButton(onClick = onNextDay) { Text("后一天 ›") }
            }
        }

        // ---------- 热量总览 ----------
        item {
            SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    CalorieRing(consumed = state.totals.kcal, target = state.targets.kcal)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoLine("已摄入", "${state.totals.kcal.fmt0()} kcal")
                        InfoLine("目标", "${state.targets.kcal.fmt0()} kcal")
                        InfoLine("估算消耗 TDEE", "${state.tdee.fmt0()} kcal")
                        InfoLine(
                            label = if (state.isOverKcal) "超出" else "还可吃",
                            value = "${(state.totals.kcal - state.targets.kcal).let { if (it < 0) -it else it }.fmt0()} kcal",
                            highlight = state.isOverKcal,
                        )
                    }
                }
            }
        }

        // ---------- 营养素 ----------
        item {
            SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("营养素", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                MacroBar("蛋白质", state.totals.proteinG, state.targets.proteinG, NutrientColors.Protein)
                Spacer(Modifier.height(12.dp))
                MacroBar("脂肪", state.totals.fatG, state.targets.fatG, NutrientColors.Fat)
                Spacer(Modifier.height(12.dp))
                MacroBar("碳水", state.totals.carbG, state.targets.carbG, NutrientColors.Carb)
                Spacer(Modifier.height(12.dp))
                MacroBar("膳食纤维", state.totals.fiberG, state.targets.fiberG, NutrientColors.Fiber)
            }
        }

        // ---------- 当天饮水 ----------
        item {
            WaterCard(
                state = state,
                onAdd = onAddWater,
                onUndo = onUndoWater,
                onClear = onClearWater,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // ---------- 当天体重 ----------
        item {
            WeightCard(
                date = state.date,
                weightKg = state.weightKg,
                weightLogCount = state.weightLogCount,
                loggedDayCount = state.loggedDayCount,
                hasEnoughForTrend = state.hasEnoughWeightForTrend,
                usingAdaptiveTdee = state.usingAdaptiveTdee,
                onSave = onSaveWeight,
                onClear = onClearWeight,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // ---------- 四个餐次 ----------
        MealType.entries.forEach { meal ->
            item(key = meal.name) {
                MealSection(
                    meal = meal,
                    state = state,
                    onDeleteEntry = onDeleteEntry,
                    onAdd = { onAddToMeal(meal) },
                )
            }
        }
    }
}

@Composable
private fun MealSection(
    meal: MealType,
    state: DiaryUiState,
    onDeleteEntry: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    val entries = state.entriesOf(meal)
    val subtotal = state.subtotalOf(meal)

    SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = "${subtotal.kcal.fmt0()} kcal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAdd) { Text("+ 添加") }
        }

        if (entries.isEmpty()) {
            Text(
                text = "还没有记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.foodName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${entry.grams.fmt0()} g · 蛋白 ${entry.proteinG.fmt0()} / 脂 ${entry.fatG.fmt0()} / 碳 ${entry.carbG.fmt0()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${entry.kcal.fmt0()} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { onDeleteEntry(entry.id) }) { Text("删") }
                }
            }
        }
    }
}

@Composable
private fun WaterCard(
    state: DiaryUiState,
    onAdd: (Int) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var customAmount by remember(state.date) { mutableStateOf("") }
    var showRecords by remember(state.date) { mutableStateOf(false) }
    var showClearConfirmation by remember(state.date) { mutableStateOf(false) }
    val amount = customAmount.toIntOrNull()
    val amountValid = amount != null && com.whattoeat.app.domain.Hydration.isValidAmount(amount)

    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("💧 喝水", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (state.waterTotalMl >= state.waterGoalMl) "今日目标已达成" else "还差 ${state.waterRemainingMl} ml",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.waterTotalMl >= state.waterGoalMl) NutrientColors.Fiber
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${state.waterTotalMl}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = NutrientColors.Protein,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "/ ${state.waterGoalMl} ml",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.waterProgress },
            modifier = Modifier.fillMaxWidth(),
            color = NutrientColors.Protein,
        )
        Text(
            text = "已记录 ${state.waterLogs.size} 次 · 达标 ${(state.waterProgress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(200, 250, 500).forEach { amountMl ->
                Button(onClick = { onAdd(amountMl) }, modifier = Modifier.weight(1f)) {
                    Text("+$amountMl ml")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customAmount,
                onValueChange = { customAmount = it.filter(Char::isDigit).take(4) },
                label = { Text("自定义饮水量 ml") },
                placeholder = { Text("例如 300") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = customAmount.isNotEmpty() && !amountValid,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    amount?.let(onAdd)
                    customAmount = ""
                },
                enabled = amountValid,
            ) { Text("记录") }
        }
        if (customAmount.isNotEmpty() && !amountValid) {
            Text(
                "单次请输入 1–2000 ml",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onUndo, enabled = state.waterLogs.isNotEmpty()) { Text("撤销上次") }
            TextButton(onClick = { showRecords = !showRecords }, enabled = state.waterLogs.isNotEmpty()) {
                Text(if (showRecords) "收起记录" else "查看记录")
            }
            if (state.waterLogs.isNotEmpty()) {
                TextButton(onClick = { showClearConfirmation = true }) { Text("清零") }
            }
        }
        if (showRecords) {
            state.waterLogs.takeLast(10).reversed().forEach { log ->
                Text(
                    text = "${log.amountMl} ml · ${java.time.Instant.ofEpochMilli(log.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        Text(
            text = "饮水量仅用于健康记录，不计入食物热量。每日目标可在设置中修改。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showClearConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("清零当天饮水？") },
            text = { Text("将删除 ${state.date} 的全部饮水记录，不会影响饮食和体重数据。") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirmation = false
                }) { Text("清零") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) NutrientColors.Over else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 「当天体重」卡片。
 *
 * 体重是反推真实代谢的唯一依据（摄入已知、体重变化已知 ⇒ 消耗可解），
 * 但之前 App 里压根没有录入入口，所以自适应反推永远是「数据不足」。
 *
 * 规则：
 *  - 这天没记 → 输入框就是空的（不预填档案体重，避免把「没称」记成「称了」）
 *  - 记了就写进 weight_logs，同一天再记会覆盖
 *  - 至少要有 2 个体重点才可能算趋势，界面明确提示还差几个
 */
@Composable
private fun WeightCard(
    date: LocalDate,
    weightKg: Double?,
    weightLogCount: Int,
    loggedDayCount: Int,
    hasEnoughForTrend: Boolean,
    usingAdaptiveTdee: Boolean,
    onSave: (Double) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 日期或已存值变化时重置草稿；null 就是空字符串 = 这天没称
    var text by remember(date, weightKg) { mutableStateOf(weightKg?.fmt1() ?: "") }
    val parsed = text.toDoubleOrNull()?.takeIf { it > 0.0 }

    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("体重", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (weightKg != null) "这天已记录" else "这天还没称",
                style = MaterialTheme.typography.labelSmall,
                color = if (weightKg != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { it.isDigit() || it == '.' }
                },
                label = { Text("当天体重 kg") },
                placeholder = { Text("没称就留空") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null,
            ) { Text(if (weightKg != null) "覆盖" else "记录") }
        }

        if (weightKg != null) {
            TextButton(onClick = onClear) { Text("清除这天体重") }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append("已记录 $weightLogCount 次体重。")
                when {
                    !hasEnoughForTrend -> {
                        val need = DiaryUiState.MIN_WEIGHT_POINTS - weightLogCount
                        append("还差 $need 次才能算出体重趋势（至少 ${DiaryUiState.MIN_WEIGHT_POINTS} 次）。")
                    }
                    usingAdaptiveTdee -> append("当前代谢就是按你的体重趋势反推的。")
                    loggedDayCount < TdeeEstimator.MIN_LOGGED_DAYS -> {
                        append("体重够算了，但反推还需要最近 28 天里至少有 ${TdeeEstimator.MIN_LOGGED_DAYS} 天饮食记录")
                        append("（现在 $loggedDayCount 天）。")
                    }
                    else -> append("可以反推代谢了。")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (usingAdaptiveTdee) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "体重是反推真实代谢的唯一依据：吃得多少已知、体重怎么变已知，消耗就能解出来。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
