package com.whattoeat.app.ui.summary

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whattoeat.app.domain.TdeeSource
import com.whattoeat.app.ui.common.DailyKcalChart
import com.whattoeat.app.ui.common.LabeledValue
import com.whattoeat.app.ui.common.MacroBar
import com.whattoeat.app.ui.common.SectionCard
import com.whattoeat.app.ui.common.fmt0
import com.whattoeat.app.ui.common.fmt1
import com.whattoeat.app.ui.theme.NutrientColors

/**
 * 「周期汇总」页：把最近 7 / 14 / 30 天的数据落成几个能看懂的数字。
 */
@Composable
fun SummaryScreen(
    state: SummaryUiState,
    onSelectPeriod: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 周期选择 ----------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(7, 14, 30).forEach { days ->
                    FilterChip(
                        selected = state.periodDays == days,
                        onClick = { onSelectPeriod(days) },
                        label = { Text("最近 $days 天") },
                    )
                }
            }
        }

        // ---------- 概览 ----------
        item {
            SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "最近 ${state.periodDays} 天概览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.avgKcal.fmt0(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "kcal / 天（日均）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))
                LabeledValue("每日目标", "${state.targetKcal.fmt0()} kcal")
                LabeledValue("累计热量差", buildString {
                    append(if (state.cumulativeBalance <= 0) "缺口 " else "盈余 ")
                    append(kotlin.math.abs(state.cumulativeBalance).fmt0())
                    append(" kcal")
                }, )
                LabeledValue("记录天数", "${state.loggedDays} / ${state.periodDays} 天")
                LabeledValue("达标天数", "${state.onTargetDays} 天（不超过目标）")
                state.weightChangeKg?.let {
                    val sign = if (it >= 0) "+" else ""
                    LabeledValue("体重变化", "$sign${it.fmt1()} kg")
                }

                if (state.loggedDays == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "这段时间还没有记录。去「今日」页记一餐试试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---------- 每日柱状图 ----------
        item {
            SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "每日热量",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "虚线是每日目标；超标的柱子会标红，灰线表示那天没记录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                DailyKcalChart(
                    values = state.points.map { it.kcal.toFloat() },
                    target = state.targetKcal.toFloat(),
                )
            }
        }

        // ---------- 代谢估算 ----------
        item {
            SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "每日消耗 TDEE",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.estimate?.let {
                        Text(
                            text = it.source.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.estimate?.effectiveTdee?.fmt0() ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "kcal / 天",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                state.estimate?.let { est ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "来源：${est.source.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LabeledValue("可信度", "${est.confidence.label}（饮食 ${est.loggedDays} 天 / 体重 ${est.weightPoints} 次）")
                    LabeledValue("基础代谢 BMR（参考）", "${est.bmr.fmt0()} kcal")
                    LabeledValue("公式法 TDEE（参考）", "${est.formulaTdee.fmt0()} kcal")
                    LabeledValue("性别平均值（兜底）", "${est.averageTdee.fmt0()} kcal")
                    est.adaptiveTdee?.let { LabeledValue("自适应反推", "${it.fmt0()} kcal") }
                    est.avgIntake?.let { LabeledValue("日均摄入（近 28 天）", "${it.fmt0()} kcal") }
                    est.trendKgPerWeek?.let {
                        val sign = if (it >= 0) "+" else ""
                        LabeledValue("体重趋势", "$sign${it.fmt1()} kg / 周")
                    }

                    if (est.source != TdeeSource.ADAPTIVE && est.manualTdee == null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "想更准？在设置里手动填一个代谢值；或者连续记录 5 天饮食 + 2 次体重，App 会自动按你的体重趋势反推。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---------- 日均营养素 ----------
        item {
            SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "日均营养素",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                MacroBar("蛋白质", state.avgProtein, state.targets.proteinG, NutrientColors.Protein)
                Spacer(Modifier.height(10.dp))
                MacroBar("脂肪", state.avgFat, state.targets.fatG, NutrientColors.Fat)
                Spacer(Modifier.height(10.dp))
                MacroBar("碳水", state.avgCarb, state.targets.carbG, NutrientColors.Carb)
                Spacer(Modifier.height(10.dp))
                MacroBar("膳食纤维", state.avgFiber, state.targets.fiberG, NutrientColors.Fiber)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "进度条 = 日均摄入 / 每日目标。纤维目标按每 1000 kcal 配 14 g 折算。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------- 逐日明细 ----------
        item {
            SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "逐日明细",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                state.points.reversed().forEach { point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(point.date.toString(), style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = if (point.logged) "${point.kcal.fmt0()} kcal" else "—",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (point.logged) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                !point.logged -> MaterialTheme.colorScheme.onSurfaceVariant
                                point.inTarget -> MaterialTheme.colorScheme.onSurface
                                else -> NutrientColors.Over
                            },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
