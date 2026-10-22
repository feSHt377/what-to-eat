package com.whattoeat.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whattoeat.app.ui.theme.NutrientColors

/**
 * 每日热量柱状图，纯 Canvas 手绘（不引入第三方图表库，省一个依赖）。
 *
 * @param values 每天的摄入热量（没记录的天传 0）
 * @param target 目标线，画成虚线
 */
@Composable
fun DailyKcalChart(
    values: List<Float>,
    target: Float,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp,
) {
    if (values.isEmpty()) return

    val barColor = MaterialTheme.colorScheme.primary
    val overColor = NutrientColors.Over
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
    ) {
        val n = values.size
        val slot = size.width / n
        val barWidth = slot * 0.55f
        // 顶部留 15% 空间，避免最高柱贴边
        val maxValue = maxOf(values.maxOrNull() ?: 1f, target, 1f) * 1.15f

        values.forEachIndexed { index, value ->
            val barHeight = (value / maxValue) * size.height
            val left = slot * index + (slot - barWidth) / 2f
            if (barHeight > 0f) {
                drawRoundRect(
                    color = if (target > 0f && value > target) overColor else barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            } else {
                // 没记录的一天：画一条很矮的灰条，表示"这天是空的"
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, size.height - 3f),
                    size = Size(barWidth, 3f),
                    cornerRadius = CornerRadius(1.5f, 1.5f),
                )
            }
        }

        // 目标虚线
        if (target > 0f) {
            val y = size.height - (target / maxValue) * size.height
            drawLine(
                color = trackColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
        }
    }
}
