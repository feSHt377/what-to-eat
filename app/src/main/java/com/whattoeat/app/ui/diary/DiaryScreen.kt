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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whattoeat.app.domain.MealType
import com.whattoeat.app.ui.common.CalorieRing
import com.whattoeat.app.ui.common.MacroBar
import com.whattoeat.app.ui.common.SectionCard
import com.whattoeat.app.ui.common.fmt0
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

        // ---------- 三大营养素 ----------
        item {
            SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("营养素", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                MacroBar("蛋白质", state.totals.proteinG, state.targets.proteinG, NutrientColors.Protein)
                Spacer(Modifier.height(12.dp))
                MacroBar("脂肪", state.totals.fatG, state.targets.fatG, NutrientColors.Fat)
                Spacer(Modifier.height(12.dp))
                MacroBar("碳水", state.totals.carbG, state.targets.carbG, NutrientColors.Carb)
            }
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
